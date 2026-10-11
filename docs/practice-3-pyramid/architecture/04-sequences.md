# А4. Sequence — одобрение, отказ, компенсация

В [исходной архитектуре](../../architecture.md) есть только happy path. Добавлены отказ `BLOCKED` и компенсация, уточнён фактический порядок CMS → BIN → баланс → лимиты, показаны две асинхронные ветви. Сценарии ниже отражают текущий код. Требования MTI `0400` и ожидания publisher confirm отдельно сопоставлены с реализацией.

Легенда: `->>` — sync HTTP/JDBC или явно подписанный локальный вызов; `-->>` — ответ; `-)` — async AMQP/WebSocket. Запись в Logger и уведомления могут появиться как до HTTP-ответа клиенту, так и после него; строгого порядка между этими ветвями нет.

## 1. Happy path: карта ACTIVE, достаточно средств и лимитов

```mermaid
sequenceDiagram
    autonumber
    actor T as Терминал / эквайрер
    participant G as Gateway
    participant S as Switch
    participant A as Authorization
    participant C as CMS
    participant B as BIN Lookup
    participant P as PostgreSQL
    participant M as Merchant Acquirer
    participant Q as RabbitMQ
    participant L as Logger
    participant N as Notification
    participant D as Dashboard
    T->>G: HTTP POST /api/transactions (0100)
    G->>G: Java: валидация, rate limit, circuit breaker
    G->>S: HTTP POST /api/internal/route
    S->>S: Java: BIN → issuerId
    S->>A: HTTP POST /api/internal/authorize
    A->>C: HTTP GET /api/cards/{pan}
    C->>P: JDBC SELECT cards
    C-->>A: ACTIVE, срок, баланс, лимиты
    A->>B: HTTP GET /api/bin/{bin}
    B-->>A: issuerId (сейчас только логируется)
    A->>A: Java: статус → срок → баланс
    A->>P: JDBC upsert limit_usage, получить блок RRN при необходимости
    P-->>A: лимиты обновлены
    A->>C: HTTP POST /api/cards/{pan}/reserve
    C->>P: JDBC transaction: lock card, reservation, balance, PENDING outbox
    P-->>C: COMMIT удержания, баланса и outbox
    par Продолжение платёжного запроса
        C-->>A: удержание создано
        A-->>S: HTTP 200, APPROVED / 00 / rrn / authCode
        Note over A,P: Локальная транзакция Authorization завершается до ответа контроллера
        S->>M: HTTP POST /api/simulator/merchant/fee
        M-->>S: комиссия или null при сбое
        S-)Q: AMQP smp.transactions / transaction.log (APPROVED)
        Note over S,Q: convertAndSend вернулся, ACK за 2s код не ожидает
        par HTTP-ответ
            S-->>G: HTTP 200, APPROVED / 00
            G-->>T: HTTP 200, APPROVED / 00
        and Журнал
            Q-)L: AMQP consume transaction-log
            L->>P: JDBC saveAndFlush transactions
            L-)D: WebSocket новая транзакция через nginx
        end
    and Независимый scheduler CMS после COMMIT
        C->>P: JDBC scheduled read PENDING outbox
        C-)Q: AMQP smp.card-events / card.CardServiceReserveEvent
        C->>P: JDBC outbox → PROCESSED после возврата из send
        Q-)N: AMQP consume card-notifications
        N->>P: JDBC INSERT card_notifications
    end
```

Scheduler CMS независим от платёжного запроса: после COMMIT он может прочитать событие уже на ближайшем запуске, даже до ответа CMS в Authorization. Он не ждёт расчёта комиссии или публикации Switch. Outbox хранит события отдельно от брокера. `PROCESSED` не доказывает, что Notification уже сохранил запись; HTTP `APPROVED` не доказывает, что Logger уже обработал очередь. Проверка eventual consistency должна опрашивать состояние по корреляционным полям с конечным сроком ожидания.

## 2. Declined: карта BLOCKED

```mermaid
sequenceDiagram
    autonumber
    actor T as Терминал / эквайрер
    participant G as Gateway
    participant S as Switch
    participant A as Authorization
    participant C as CMS
    participant B as BIN Lookup
    participant M as Merchant Acquirer
    participant Q as RabbitMQ
    participant L as Logger
    participant P as PostgreSQL
    T->>G: HTTP POST /api/transactions (0100)
    G->>S: HTTP POST /api/internal/route
    S->>A: HTTP POST /api/internal/authorize
    A->>C: HTTP GET /api/cards/{pan}
    C-->>A: BLOCKED
    A->>B: HTTP GET /api/bin/{bin}
    B-->>A: issuerId или fallback empty
    A->>A: Java: status != ACTIVE
    Note over A,C: reserve не вызывается, limit_usage не обновляется
    A-->>S: HTTP 403, DECLINED / 05 / CARD_BLOCKED
    S->>M: HTTP запрос комиссии (вызов есть и при DECLINED)
    M-->>S: комиссия или null
    S-)Q: AMQP transaction.log (DECLINED)
    par HTTP-ответ
        S-->>G: HTTP 200, DECLINED / 05
        G-->>T: HTTP 200, DECLINED / 05
    and Журнал отказа
        Q-)L: AMQP consume transaction-log
        L->>P: JDBC INSERT transactions (DECLINED)
    end
```

Бизнес-проверки Authorization заканчиваются отказом, но Switch продолжает запрос комиссии и публикацию журнала. HTTP `403` внутреннего Authorization преобразуется в HTTP `200` контроллером Switch; бизнес-код `05` остаётся в JSON. Поэтому HTTP-код и `responseCode` нельзя считать одним полем.

## 3. Reversal: требуемый MTI 0400 и фактическая компенсация

```mermaid
sequenceDiagram
    autonumber
    actor T as Терминал / эквайрер
    participant G as Gateway
    participant S as Switch
    participant A as Authorization
    participant C as CMS
    participant P as PostgreSQL
    participant Q as RabbitMQ
    alt Внешний запрос с mti 0400
        T->>G: HTTP POST /api/transactions (0400)
        G->>G: Java validate: разрешён только 0100
        G-->>T: HTTP 400 (маршрутизация reversal не реализована)
    else Автоматическая компенсация после одобрения 0100
        T->>G: HTTP POST /api/transactions (0100)
        G->>S: HTTP POST /api/internal/route
        S->>A: HTTP authorize
        Note over A,C: Успешные проверки и reserve, как в сценарии 1
        A-->>S: APPROVED / 00 / rrn
        Note over S: Затем запрос комиссии к Merchant Acquirer
        S-)Q: AMQP convertAndSend
        Q-->>S: Ошибка отправки AmqpException
        Note over S,Q: Это исключение send, а не таймаут ожидания confirm
        S->>A: HTTP POST /api/internal/rollback (rrn, pan, amount)
        A->>C: HTTP POST /api/cards/{pan}/rollback
        C->>P: JDBC transaction: lock, reservation → ROLLED_BACK, balance += amount, outbox
        C-->>A: средства возвращены
        A-->>S: rollback APPROVED / 00
        Note over A,P: limit_usage не уменьшен, Logger не получает событие ROLLED_BACK
        S-->>G: HTTP 200, DECLINED / 96
        G-->>T: HTTP 200, DECLINED / 96
    end
```

По [ТЗ Switch, п. 3–4](../../../tz/03-switch.md), NACK или отсутствие confirm за 2 секунды должны инициировать reversal, а [ТЗ Authorization, дополнительные задания](../../../tz/04-authorization.md) требует восстановления лимитов. Сейчас механизм частичный:

- `LoggerClient` не ждёт confirm. Если `convertAndSend` завершится без исключения, последующий NACK / отсутствие ACK не вызовет rollback.
- Switch создаёт `RollbackRequest` и вызывает отдельный `/api/internal/rollback`; это не HTTP-маршрутизация ISO-запроса с `mti=0400`.
- Authorization возвращает средства через CMS, но не восстанавливает использованные лимиты и не обновляет журнал транзакции.
- Switch возвращает код `96` даже при неуспешном ответе rollback; сам `96` не подтверждает возврат денег.

Это основания для требовательных тестов отказа, а не повод заменить ожидаемый результат текущей ошибкой. Чтобы требование `0400` было видно как полный путь, ниже приведена **целевая схема** третьего сценария. Её отличия от предыдущей схемы сейчас не реализованы.

```mermaid
sequenceDiagram
    autonumber
    actor T as Клиент
    participant G as Gateway
    participant S as Switch
    participant A as Authorization
    participant C as CMS
    participant P as PostgreSQL
    participant Q as RabbitMQ
    Note over T,Q: Целевое требование, не фактическое выполнение текущего кода
    T->>G: HTTP POST /api/transactions (0100)
    G->>S: HTTP route
    S->>A: HTTP authorize
    Note over A,C: Проверки, limit_usage и reserve успешно выполнены
    A-->>S: APPROVED / 00 / rrn
    S-)Q: AMQP publish APPROVED
    alt NACK
        Q-->>S: Publisher confirm NACK
    else Отсутствие подтверждения
        Note over S,Q: Истекли 2s ожидания ACK
    end
    S->>A: sync HTTP reversal, mti 0400 и исходный rrn
    A->>C: sync HTTP вернуть удержанные средства
    C->>P: sync JDBC reservation ROLLED_BACK, balance восстановлен
    C-->>A: возврат выполнен
    A->>P: sync JDBC восстановить использованные лимиты
    A-->>S: reversal успешен
    S-->>G: DECLINED / 96
    G-->>T: DECLINED / 96
```

URL и JSON-контракт целевого `0400` здесь намеренно не выдуманы: ТЗ задаёт механизм, текущий код использует иной DTO / endpoint. При реализации понадобится согласовать контракт и обработку повторного reversal.

| Шаги схем | Источник |
|---|---|
| Валидация MTI, маршруты и порядок фильтров Gateway | [TransactionRequestValidator](../../../services/gateway/src/main/java/com/processing/gateway/validation/TransactionRequestValidator.java), [TransactionValidationFilter](../../../services/gateway/src/main/java/com/processing/gateway/validation/TransactionValidationFilter.java), [application.yml](../../../services/gateway/src/main/resources/application.yml), [фильтры Gateway](../../../services/gateway/src/main/java/com/processing/gateway) |
| BIN, вызовы authorize / fee / logger / rollback | [RoutingService](../../../services/switch/src/main/java/com/processing/service/RoutingService.java), [RouteService](../../../services/switch/src/main/java/com/processing/service/RouteService.java), [AuthorizationClient](../../../services/switch/src/main/java/com/processing/service/AuthorizationClient.java), [LoggerClient](../../../services/switch/src/main/java/com/processing/service/LoggerClient.java) |
| HTTP 200 Switch и HTTP 403 Authorization | [RouteController](../../../services/switch/src/main/java/com/processing/controller/RouteController.java), [AuthControllerImpl](../../../services/authorization/src/main/java/com/processing/authorization/controller/AuthControllerImpl.java) |
| Порядок авторизации и отсутствие уменьшения лимитов в rollback | [AuthServiceImpl](../../../services/authorization/src/main/java/com/processing/authorization/services/AuthServiceImpl.java) |
| Удержание, возврат и outbox CMS | [CardServiceImpl](../../../services/card-management/src/main/java/com/processing/cardmanagement/services/CardServiceImpl.java), [OutboxEventProcessorImpl](../../../services/card-management/src/main/java/com/processing/cardmanagement/services/OutboxEventProcessorImpl.java) |
| Обработка очередей, JDBC и WebSocket | [TransactionLogListener](../../../services/transaction-logger/src/main/java/com/processing/transactionlogger/listener/TransactionLogListener.java), [TransactionService](../../../services/transaction-logger/src/main/java/com/processing/transactionlogger/service/TransactionService.java), [CardEventListener](../../../services/notification-service/src/main/java/com/processing/notification/listener/CardEventListener.java) |
