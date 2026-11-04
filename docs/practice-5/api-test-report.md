# API-тесты СМП

## Результат локальных проверок

| Проверка | Фактический результат |
|---|---|
| Regression-run 1 | 405 тестов, 0 failures/errors/skipped |
| Regression-run 2 | 405 тестов, 0 failures/errors/skipped |
| Regression-run 3 | 405 тестов, 0 failures/errors/skipped |
| HTTP/TCP fault-набор и компенсация | 26 тестов, 0 failures/errors/skipped |
| Checkstyle | 0 нарушений во всех итоговых verify |
| Намеренные мутации | 3 из 3 обнаружены именно assertions |
| Восстановленный Logger | исходный тест идемпотентности снова зелёный |
| Строгий контракт | 18 красных воспроизведений, 0 технических errors; это зарегистрированные дефекты |
| Ресурсный флаг `--suspend-main` | ещё 405/405; ровно те же 12 исходных контейнеров восстановлены и healthy |
| Статика | корректные YAML/JSON/XML/Python, bash -n, git diff --check |

Это разные результаты: зелёный набор проверяет реализованное поведение с явно описанными
ограничениями, строгий набор сохраняет первоначальные ожидания контракта. Красные
строгие проверки не включены в число зелёных и не замаскированы skip/xfail.
Доказательства: [local-checks.json](evidence/local-checks.json),
[mutations.json](evidence/mutations.json), [strict-contract.json](evidence/strict-contract.json),
[полный итоговый вывод](evidence/local-run.txt), [версии](evidence/versions.txt),
[восстановление исходного стенда](evidence/resource-management.json).

## 1. Покрытие

Модуль `services/api-tests` использует Java 21, Maven, JUnit Jupiter 5.11.4,
REST-assured/JSON Schema Validator 5.4.0, Hamcrest 2.2, Awaitility 4.2.2 и Jackson 2.18.2.
TestNG не подключён. Проверки проходят через HTTP поднятых сервисов. PostgreSQL и RabbitMQ
не используются как клиенты тестов; тесты не выполняют SQL и не читают очереди.

Перечень составлен из **трёх** источников:

1. `docs/api-spec.md` и `docs/api/openapi.yaml` — опубликованный контракт.
2. Контроллеры сервисов и аннотации DTO — реализованные маршруты, ограничения и ошибки.
3. `services/gateway/src/main/resources/application.yml` — реально доступные публичные маршруты.

Счётчики ниже относятся к HTTP-кейсам. Один параметризованный метод
даёт несколько самостоятельных JUnit-кейсов. Точные результаты сохранены в `evidence/local-checks.json`.
Число кейсов для каждого метода/пути и фактически наблюдаемые статусы приведены в
[сгенерированной матрице HTTP-покрытия](endpoint-coverage.md).

| Эндпоинт / группа | Проверяемые статусы | Основной класс |
|---|---|---|
| `GET /health` у Gateway и семи основных сервисов; health двух дополнительных сервисов | 200, тело и состояние зависимостей | `AuxiliaryApiTest` |
| `POST /api/transactions` | 200 APPROVED/DECLINED, 400, 429, 503 | `AuthorizationApiTest`, `GatewayValidationTest`, `FaultInjectionApiTest` |
| `POST /api/internal/route` (Switch) | 200 APPROVED/DECLINED, 400, fallback 05/96 | `AuthorizationApiTest`, `AuthorizationValidationTest`, `FaultInjectionApiTest` |
| `POST /api/internal/authorize` | 200, 400, 403, 404, 422, 503 | `AuthorizationApiTest`, `AuthorizationValidationTest`, `DependencyFaultApiTest` |
| `POST /api/internal/rollback` | 200, 400, 404, 409, 503 | `AuthorizationApiTest`, `AuthorizationValidationTest`, `DependencyFaultApiTest` |
| `POST /api/cards` | 201, 400, 404, 503 | `CardsApiTest`, `CardValidationTest`, `FaultInjectionApiTest` |
| `GET /api/cards` | 200, 400, 413, 503 | `CardsApiTest`, `CardValidationTest`, `FaultInjectionApiTest` |
| `GET /api/cards/{pan}` | 200, 400, 404, 503; X-Cache MISS/HIT | `CardsApiTest`, `CardValidationTest`, `FaultInjectionApiTest` |
| `PATCH /api/cards/{pan}` | 200, 400, 404, 503 | `CardsApiTest`, `CardValidationTest`, `FaultInjectionApiTest` |
| `DELETE /api/cards/{pan}` | 204, 400, 404, 503 | `CardsApiTest`, `CardValidationTest`, `FaultInjectionApiTest` |
| `POST /api/cards/{pan}/reserve` | 200, 400, 402, 404, 409; 500 при отсутствии amount — D08 | `CardsApiTest`, `CardValidationTest`, `StrictContractTest` |
| `POST /api/cards/{pan}/rollback` | 200, 400, 404, 409; переплата — D09 | `CardsApiTest`, `CardValidationTest`, `StrictContractTest` |
| `POST /api/cards/generate` | 201, 400, 404 | `CardsApiTest`, `CardValidationTest` |
| `POST /api/cards/bulk-update` | 200, 400 | `CardsApiTest`, `CardValidationTest` |
| `GET/POST /api/bins`, `GET/DELETE /api/bins/{bin}` — напрямую CMS | 200, 201, 204, 400, 404, 409 | `AuxiliaryApiTest` |
| `POST /api/internal/log` | 201, 200, 400, 409; GET того же пути — 405 | `LoggerApiTest` |
| `GET /api/transactions/search` | 200, 400, 503 | `TransactionSearchApiTest`, `FaultInjectionApiTest` |
| `GET /api/transactions/export` | 200 CSV, 400, 503 | `TransactionSearchApiTest`, `FaultInjectionApiTest` |
| `GET /api/dashboard/stats` | 200, 503 | `DashboardApiTest`, `FaultInjectionApiTest` |
| `GET /api/dashboard/recent` | 200, 400, 503 | `DashboardApiTest`, `FaultInjectionApiTest` |
| `GET /api/dashboard/charts` | 200, 400, 503 | `DashboardApiTest`, `FaultInjectionApiTest` |
| `POST /api/simulator/terminal/run` | 200, 400, 503 | `SimulatorApiTest`, `FaultInjectionApiTest` |
| `POST /api/simulator/terminal/start-continuous` | 200 с пустым телом, 400, 422 | `SimulatorApiTest` |
| `POST /api/simulator/terminal/stop` | 200 с пустым телом | `SimulatorApiTest` |
| `POST /api/simulator/merchant/run` | 200, 400, 404, 502, 503 | `SimulatorApiTest`, `FaultInjectionApiTest` |
| `POST /api/simulator/merchant/fee` | 200, 400, 404 | `SimulatorApiTest` |
| `GET /api/simulator/merchants` — напрямую Merchant | 200; Gateway 404 — D06 | `SimulatorApiTest`, `AuxiliaryApiTest` |
| `GET /api/bin/{bin}` — напрямую Bin Lookup | 200, 400, 404 с пустым телом, 500 через fail=true | `AuxiliaryApiTest` |
| `GET /api/notifications` — напрямую Notification | 200, пагинация, пустая страница; Gateway 404 — D06 | `AuxiliaryApiTest` |
| `GET /switch-docs`, `/logger-docs`, `/terminal-docs`, `/merchant-docs`, `/cards-docs` | 200, OpenAPI 3, скрытие внутренних путей, переписанный публичный POST | `AuxiliaryApiTest` |

Отдельно экспериментально проверена остановка настоящих контейнеров. Она выявила D13:
`NoRouteToHostException` приводит к 500 вместо заявленного 503.

## 2. Бизнес-отказы

Ожидания для реализованного контракта берутся из `DeclineOutcome`, `AuthControllerImpl`,
`CardManagementClientImpl`, `AuthServiceImpl` и `RouteService`. Отступления от ТЗ перечислены ниже.
Для отказов одновременно проверяются HTTP-статус, `status`, `responseCode`, `declineReason`,
эхо STAN, отсутствие RRN/authCode и неизменность баланса там, где списания быть не должно.

| Исход | Код / причина | Путь достижения / подготовка |
|---|---|---|
| Карта не найдена | `14 / CARD_NOT_FOUND` | неизвестный BIN через Gateway; отсутствующий PAN с известным BIN; удалённая карта; upstream GET/RESERVE 400/404 |
| Неактивная карта | `05 / CARD_INACTIVE` | создать ACTIVE карту, PATCH INACTIVE |
| Заблокированная карта | `05 / CARD_BLOCKED` | PATCH BLOCKED; отдельно upstream GET 402 при реально ACTIVE карте |
| Просроченная карта | `54 / CARD_EXPIRED` | PATCH EXPIRED; отдельно ACTIVE карта + дата запроса после её expiryDate |
| Недостаток средств | `51 / INSUFFICIENT_FUNDS` | сумма на 1 выше баланса; отдельно upstream reserve 402 после успешного GET карты |
| Превышение лимита | `61 / EXCEEDS_AMOUNT_LIMIT` | L−1/L/L+1; накопление за день; накопление за месяц в разные дни |
| Недоступность CMS | `96 / SERVICE_UNAVAILABLE` | реальный сетевой отказ listener; upstream 500/503 и непредусмотренный GET 409 |
| Сбой резерва | `96 / RESERVATION_FAILED` | upstream reserve 409 после успешных проверок карты и лимитов |
| Ошибка хранилища лимитов | ожидается `96 / DB_UNAVAILABLE`, фактически Spring 500 (D14) | upstream CardModel с PAN длиной 17; собственная реальная карта остаётся без списания |
| Неожиданное состояние карты | `05 / UNKNOWN_REASON` | upstream CardModel со status=null; публичным PATCH это состояние недостижимо |
| Возврат неизвестной операции | `14 / TRANSACTION_NOT_FOUND` | собственная карта + новый RRN; отдельно upstream rollback 400/404 |
| Повторный возврат | `05 / ALREADY_ROLLED_BACK` | авторизовать, вернуть, повторить; отдельно upstream rollback 409 |
| Технический сбой возврата | `96 / ROLLBACK_FAILED` | upstream rollback 402, не равный специальным кодам обработчика |
| Недоступность Authorization для Switch | `05 / Authorization service unavailable` | закрыть только TCP listener Authorization, сохранить остальные зависимости |
| Ошибка публикации после APPROVED | `96 / System error` | остановить RabbitMQ в **тестовом** проекте, проверить компенсацию и отсутствие записи |

Защитная ветвь `DB_UNAVAILABLE` дополнительно вызвана некорректным upstream CardModel с PAN длиной 17.
Тест обращается только к HTTP API; SQL не выполняет. При ошибке записи лимитов выявлен D14:
наружу уходит Spring 500 вместо доменного ответа `96 / DB_UNAVAILABLE`. Реальное отключение
PostgreSQL и проверка внутренних транзакций остаются модулем 6. Также не считаются реализованными исходы 12/30/41/43
только потому, что они перечислены в справочной таблице: алгоритм/enum карты не содержит
ветвей «lost/stolen», а Gateway возвращает 400 с ErrorResponse для ошибки формата.

## 3. Валидация и параметры

| Область / поля | Правила и классы | Границы / подготовка |
|---|---|---|
| Gateway: обязательные строки | null, пусто, пробел; по одному нарушению на запрос | mti, stan, pan, processingCode, currencyCode, terminalId, merchantId, mcc, acquirerId |
| Gateway: pan/mcc/currencyCode/mti | допустимый формат, короткое/длинное значение, буква вместо цифры, иной MTI | PAN 15/16/17; MCC 3/4/5; валюта 2/3/4; MTI 0100/0200 |
| Gateway: amount / timestamp / JSON | null; отрицательная сумма; ноль; минимальная целая сумма; некорректный Instant; null/массив/строка/сломанный JSON | −0.01 / 0 / 1; ISO-8601; дробная сумма отдельно D12 |
| Authorization DTO | обязательность, ExactSize, PositiveOrZero, Instant | длина n−1/n+1 для восьми полей; null/пусто/пробел; отрицательная сумма |
| Rollback DTO | обязательность PAN/RRN/amount, regex, положительная сумма | PAN 15/16/17, RRN 11/12/13, буквы; −1/0/1 |
| Создание карты | BIN, имя, валюта, неотрицательные лимиты | BIN 5/6/7; имя uppercase/lowercase/кириллица/цифра; валюта 2/3/4, RUB; лимит −1 |
| PATCH карты | неотрицательные лимиты, daily ≤ monthly, пустой PATCH | −1/0/1; daily > monthly; сохранение всех исходных значений при ошибке |
| Reserve/Rollback CMS | PAN в пути, RRN, положительная сумма | один неверный класс за раз; баланс после 400/402/404/409 не меняется |
| Генератор | count ≥ 1, не больше 10000; bins не пустой, корректный BIN | −1/0/1/3/10001; null/[]/невалидный BIN; положительный тест проверяет размер и все PAN |
| Bulk update | ровно один селектор, обязательный статус, валидные элементы | neither/both/null status/invalid PAN/BIN; успешные селекторы pans и bins |
| Logger DTO | обязательность, NotBlank, числовые regex, max size, amount > 0, fee/time ≥ 0 | все 21 поля DTO; null/пробел, n−1/n+1, буквы, ноль/отрицательное значение; nullable-поля отдельно |
| Поиск транзакций | все фильтры AND, даты в UTC, limit 1..500, offset ≥ 0 | limit −1/0/1/2/50/500/501; offset −1/0/1/3/1000000; enum/date/type ошибки; пустая выборка |
| Поиск карт | bin/status/issuerId/startDate/endDate/limit/offset | отдельный случайный BIN; limit 0/1/2/10000/10001; offset −1/0/1/3; даты до/после создания |
| Dashboard | recent limit 1..500; charts hour/day и [from,to) | −1/0/1/20/500/501; неверная гранулярность/дата; равные границы; исключение верхней границы |
| Симуляторы | count/tps ≥ 1, обязательный scenario, enum; управляющие режимы | −1/0/1; все 5 терминальных и 4 торговых сценария; повторный start → 422; stop в finally |
| Merchant fee | обязательные time/PAN/STAN/terminalId | каждое поле отсутствует отдельно; неизвестная комиссия; комиссия собственной сгенерированной операции |
| Bin Lookup / Notifications | допустимые delay/fail; page/size | известные 5 BIN; неизвестный BIN; fail=true; неверный тип delay/fail; первая/следующая/пустая страница |

**Попарное покрытие поиска.** PAN всегда собственный и служит ключом изоляции. Для девяти
остальных параметров (`status`, `merchantId`, `issuerId`, `mcc`, `declineReason`, `dateFrom`,
`dateTo`, `limit`, `offset`) используется covering array по уровням «параметр отсутствует / задан».
Это 144 пары уровней, проверяемые восемью строками вместо 512 комбинаций.
Для шести параметров поиска карт вокруг собственного BIN — ещё 60 пар уровней в семи строках
вместо 64 комбинаций. Оракул независимо
от API вычисляет ожидаемые ID, total, порядок и страницу по трём подготовленным записям.
Одномерные неверные значения и границы проверяются отдельно, они не заменяются pairwise.

**Повторная доставка.** Проверены первое сохранение 201, идентичный повтор 200 с полной
существующей записью, конфликт 409 при изменении каждого из 19 сравниваемых бизнес-полей,
численная эквивалентность `100`/`100.000`, намеренное игнорирование createdAt и отсутствие
дубликатов в поиске. Для reserve повторный RRN с той же и другой суммой даёт 409; повторный
rollback не возвращает деньги второй раз.

## 4. Расхождения контракта и реализации

| ID | Декларировано | Наблюдается / источник реализации | Классификация и влияние |
|---|---|---|---|
| D01 | Ответ Authorization имеет MTI `0110` (`docs/api-spec.md`) | `approved/declined` копируют MTI запроса `0100` | Дефект, средний: потребитель не различает запрос и ответ по MTI. Строгий тест красный. |
| D02 | Card.expiryDate — MMYY; пример содержит createdAt | Ответ CMS — `YYYY-MM`, createdAt отсутствует в CardModel | Несовместимость документации и DTO, средняя. Тесты текущего DTO проверяют `YYYY-MM`; перед изменением формата нужна согласованная версия контракта. |
| D03 | Длины STAN/processingCode/terminalId/merchantId ограничены публичным контрактом | Gateway проверяет только обязательность; часть значений нормализует Switch, часть превращается в fallback decline вместо ошибки поля | Дефект, высокий: невалидный публичный запрос может получить 200, неверный доменный исход или логирование. Восемь отдельных красных воспроизведений. |
| D04 | Ошибка валидации объясняет поле и правило | Authorization возвращает стандартное Spring-тело без serviceName/message; ошибки JSON enum называют Java-класс | Дефект диагностики, средний. Текущее Spring-тело проверяется явно; правильная форма отдельно проверяется красным тестом. |
| D05 | По телу ошибки понятно, какое поле невалидно | CMS возвращает только первый текст вроде `Value can not be negative`, без `dailyLimit` | Дефект диагностики, средний: потребитель не определяет поле. Строгий тест красный. |
| D06 | Контроллеры/Swagger объявляют BIN, notifications и список merchants | Gateway не маршрутизирует `/api/bins`, `/api/notifications`, `/api/simulator/merchants`; отвечает 404 | Известное ограничение публикации API. Проверено напрямую и через Gateway; прямые URL документированы, публичную доступность не предполагаем. |
| D07 | Терминальный пример запроса содержит count + scenario | TerminalRunRequest требует tps ≥ 1; без него 400 | Несовместимость документации, средняя. В рабочих сценариях tps задаётся явно, пример спецификации сохранён красным тестом. |
| D08 | Reserve принимает обязательную положительную сумму | На amount нет @NotNull; отсутствие amount даёт 500 | Дефект, высокий: ошибка клиента превращается во внутреннюю. Строгий тест ожидает 400 и объяснение amount. |
| D09 | Rollback освобождает зарезервированные средства | Возврат 101 после резерва 100 принимается, баланс становится больше исходного | Дефект, высокий: нарушение денежного инварианта. Строгий тест проверяет отказ и неизменность баланса. |
| D10 | ValidDateRange сообщает `dateFrom must not be after dateTo` | HTTP 400 возвращается, но GlobalExceptionHandler собирает только fieldErrors, теряя objectErrors; message пуст | Дефект диагностики, средний. Строгий тест красный. |
| D11 | IssuerIdValidator требует 6–10 uppercase букв/цифр | @IssuerId не содержит @Constraint/@Retention; `lower` принимается | Дефект валидации, средний. Строгий тест красный; тестовая запись удаляется. |
| D12 | BigDecimal amount без ограничения scale, баланс должен отражать списание | Для amount=0.01 ответ APPROVED, но CardRestMapper округляет availableBalance до целого HALF_EVEN | Известная потеря точности представления, средняя. Основные денежные сценарии используют целые копейки; дробный случай оставлен строгим красным тестом. |
| D13 | Недоступность downstream преобразуется в 503 с serviceName | Реальный stop контейнера может дать NoRouteToHostException → Spring 500; список DownstreamExceptionUtils этого типа не содержит | Дефект устойчивости, высокий. Доказательство сохранено из реального прогона; стабильные TCP-refused проверки не заменяют этот результат. |
| D14 | Ожидается доменный ответ `96 / DB_UNAVAILABLE` при ошибке хранилища лимитов | Ошибка SQL в authorize делает транзакцию rollback-only; на commit ответ заменяется Spring 500 | Дефект обработки отказа, высокий. Вызван исключительно HTTP-подменой PAN CardModel; баланс собственной карты не меняется. |

Дополнительные отличия ТЗ: `tz/04-authorization.md` описывает проверку лимитов перед балансом,
а `AuthServiceImpl` проверяет баланс первым; при одновременном нарушении меняется приоритет причины.
ТЗ для недоступного CMS обещает `05 / ISSUER_TIMEOUT`, реализация возвращает `96 / SERVICE_UNAVAILABLE`.
Оба значения не подменяются в отчёте одно другим: автоматические проверки явно закрепляют
текущую реализацию, а совместимость с ТЗ остаётся открытым вопросом контракта.
`GET /api/cards?limit=10001` даёт 413, хотя аннотация контроллера перечисляет только 200/400.
DELETE выполняет soft delete: следующий GET и повторный DELETE возвращают 404; значение DELETED
не принимается DTO CardModelStatus, поэтому его нельзя готовить через PATCH.

## 5. Обоснование достаточности и ограничения

Классы входов выведены из DTO/валидаторов и всех ветвей доменного алгоритма, а не из
заранее назначенного числа тестов. Для строки различаются отсутствие, пустота, пробел,
неверная длина и неверный алфавит; для числа — отрицательное, ноль, допустимая граница,
превышение и накопленное потребление. Для дат отделены неразбираемое значение, равные
границы, инверсия диапазона и включение/исключение границ. Успех и каждый бизнес-отказ
проверяются по содержимому, а не только по HTTP-статусу.

Изоляция обеспечивается новым PAN от API, UUID, STAN, RRN, случайным BIN и собственным
мерчантом в логгер-фикстурах. Симулятор сам выбирает STAN, поэтому его результат ищется
**одновременно по собственному PAN и STAN**, а не по общей истории. Подготовка баланса,
лимитов и статуса выполняется API; cleanup удаляет только собственные карты/маппинги.
Для поиска используется собственный набор записей; агрегаты Dashboard не сравниваются
с произвольным глобальным количеством. Временные метки фикстур округлены до миллисекунд,
чтобы точность HTTP и PostgreSQL не создавала ложных конфликтов повторной доставки.

Сквозная проверка сопоставляет одобрение Gateway, изменение баланса, RRN/authCode, issuerId
и сохранённую запись, найденную GPath-фильтром через публичный поиск. Ожидание появления:
atMost 15 s / pollInterval 200 ms. Отсутствие эффекта проверяется через **during**:
после невалидного запроса — непрерывно 2 s, при компенсации — 3 s до и 3 s после
восстановления брокера. Один ранний пустой ответ не считается доказательством отсутствия.

Осознанно вне доказанного покрытия этой работы:

- PostgreSQL unavailable/SQL rollback, исчерпание диска, повреждение данных, deadlock,
  миграции, AMQP topology/confirms/redelivery и прямые проверки очередей — модуль 6.
- Все произвольные внутренние 500, вызванные отказом БД каждого сервиса, не объявлены
  покрытыми только по факту наличия catch(Exception). В таблице перечислены реально выполненные коды.
- Границы генерации 10000 карт и CSV 50000 строк не создаются в API smoke-наборе:
  проверены ограничения сверху без массового изменения данных, малый успешный batch и схема ответа.
- Для карт дополнительно выполнен covering array по шести параметрам вокруг собственного BIN:
  status/issuerId/startDate/endDate/limit/offset. Порядок CMS не определён кодом/контрактом,
  поэтому проверяются множество собственных PAN, total, размер и отсутствие дубликатов;
  произвольный порядок по createdAt не навязывается.
- Вспомогательные Actuator metrics/prometheus/info, Swagger UI/статические ресурсы и WebSocket
  не являются синхронными бизнес-контрактами практики. Проверены health и публикация JSON OpenAPI.
- Конкурентная нагрузка, безопасность, RRN collision под нагрузкой и долгий soak-тест
  требуют отдельных прогонов; этот набор не доказывает их отсутствие.

Эти ограничения означают, что зелёный regression-run не является заявлением
«спецификация полностью реализована» или «любой возможный 500 покрыт». Известные дефекты
оставлены видимыми в строгом наборе и в доказательствах; production-код ради зелёных тестов не менялся.

## 6. Доказательства работоспособности проверок

Скрипт `scripts/check-api-mutations.sh` работает с временными копиями исходников в `target/`.
Рабочие production-файлы и тесты не переписываются. Проверяется наличие именно JUnit
assertion failure: ошибка компиляции, сети или контейнера не засчитывается как обнаруженная мутация.

| Тест | Намеренное изменение | Требуемое доказательство |
|---|---|---|
| `GatewayValidationTest#jsonNullBodyHasDedicatedError` | ожидаемый 400 заменён на 401 в копии теста | assertion failed: actual 400, expected 401 |
| `AuthorizationApiTest#purchaseTraversesGatewaySwitchAuthorizationAndLogger` | ожидаемый остаток 99900 заменён на 99901 в копии | assertion failed по availableBalance |
| `LoggerApiTest#sameIdAndSamePayloadAreIdempotent` | в копии TransactionService отключён ранний возврат существующей записи; собран отдельный Docker-образ | повтор возвращает 201 вместо 200, реальный неизменённый тест краснеет |
| Тот же Logger-тест после восстановления | возвращён исходный image, контейнер пересоздан | тест снова зелёный |

Реальные результаты и имена проваленных assertions: `evidence/mutations.json`.
Полные Maven/JUnit-артефакты: `services/api-tests/target/mutations/`.
Отдельный `StrictContractTest` воспроизводит зарегистрированные расхождения с исходными
ожиданиями; результаты этого красного прогона сохранены в `evidence/strict-contract.json`.

## 7. Порядок запуска

Для работы кнопками Run Test / Debug Test в VS Code откройте корень `PracticRaulMullayanov`.
Перед тестами выполните `./scripts/api-test-env.py start`, после работы —
`./scripts/api-test-env.py stop`. Команда `start` подготавливает тестовый стенд и ждёт
готовности; `stop` останавливает его и восстанавливает ранее работавшие контейнеры СМП.
В локально настроенном VS Code эти команды доступны через Terminal → Run Task как
`API: подготовить стенд` и `API: завершить работу`. Настройки `.vscode` исключены из Git;
для другого компьютера используйте приведённые команды из терминала.

Требуются Docker Compose, Python 3 и JDK 21. На macOS автоматически выбирается установленный
Homebrew JDK 21; Maven берётся из PATH или скачивается в игнорируемый target с проверкой SHA-512.

```bash
# На этой машине (Docker VM 6 ГБ): три regression-run, отказы, компенсация и мутации:
./scripts/run-api-tests.sh --suspend-main --faults --mutations

# На отдельной CI-машине или при достаточном запасе памяти:
./scripts/run-api-tests.sh --faults --mutations

# Только три regression-run:
./scripts/run-api-tests.sh

# Сохранить тестовый стенд после прогона для ручного исследования:
./scripts/run-api-tests.sh --keep-stack

# На уже поднятом стенде:
mvn -f services/pom.xml -Papi-tests verify
mvn -f services/pom.xml -Papi-tests,api-faults -Dapi.faults.enabled=true verify

# Строгий набор известных дефектов: ненулевой exit code ожидаем и должен анализироваться.
mvn -f services/pom.xml -Papi-tests,strict-contract test
```

Стенд `docker-compose.api-tests.yaml` имеет собственное имя `smp-api-tests`, собственную БД
и порты **127.0.0.1:28080–28099**. Основной compose/`.env` не переписывается. Default-скрипт
останавливает только тестовый проект в EXIT trap, сохраняя том БД и локальные доказательства.
`RUNS=1` позволяет выполнить один диагностический прогон; значение по умолчанию — 3.
URL сервисов можно переопределить через `-Dapi.<service>.url=...`; fault-профиль предназначен
только для отдельного стенда, а не для чужой среды.

JUnit XML, HTTP-запросы/ответы с привязкой к имени теста, логи сервиса и снимки каждого
прогона сохраняются под `services/api-tests/target/`. Краткие результаты и выбранные ответы
продублированы в `docs/practice-5/evidence/`, чтобы отчёт был проверяем после очистки target.

Автоматический CI описан в `.github/workflows/api-tests.yml`: push/PR по затронутым исходникам
запускает тот же скрипт, затем сохраняет артефакты даже при падении. В рамках этой локальной
работы workflow не запускался на GitHub, push/commit/Issue/PR не выполнялись.

На машине с уже работающим полным СМП нужен запас памяти ещё на один стек.
`--suspend-main` решает это одной командой: запоминает ID только работающих контейнеров
проекта `smp`, временно останавливает их без удаления данных и в EXIT trap запускает
ровно эти же ID, дожидаясь healthy. Восстановление выполняется и после ошибки/прерывания.
Этот флаг нельзя совмещать с `--keep-stack`, чтобы не восстановить два стека одновременно
в ограниченной памяти. Другие проекты не останавливаются. Флаг отдельно проверен
локальным прогоном 405 кейсов и сверкой ID восстановленных контейнеров.
