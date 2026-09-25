# Практика 2 — результаты прогона тестов

## Authorization

Общие предусловия — из раздела 6.1 тест-дизайна. Данные ниже уточняют отдельные варианты. Все суммы указаны в копейках. Для AUTH-11 задать одновременно превышенный дневной лимит и недостаточный баланс. Для AUTH-07 остальные проверки должны пропускать операцию.

| ID | Данные / сценарий | Ожидаемый результат | Фактический результат | Статус | Материалы / дефект |
|---|---|---|---|---|---|
| AUTH-01 | PAN `4000004489453039`, `A=90003`, `B=100000`, `DL=150000`, `ML=300000`; новая карта | `APPROVED`, код `00`; RRN — 12 цифр, authCode — 6 символов `A-Z0-9`; баланс уменьшается на `A`, использованные суммы за день и месяц увеличиваются на `A`; лимиты карты не изменяются | HTTP `200`, `APPROVED`, `00`; RRN `626500001447`, authCode `C8S9XX`; B=9997, D=M=90003; DL/ML не изменены | Passed | [Ответы API и SQL](evidence/2026-09-22/AUTH-01/result.md) |
| AUTH-02 | PAN `4000000000000002`, `A=90003`; GET карты → `404` | `DECLINED`, `CARD_NOT_FOUND`, код `14`; резервирование не вызывается | HTTP `404`, `DECLINED`, `14`, `CARD_NOT_FOUND`; SQL — 0 строк; отказ до reserve по ветви кода и логам | Passed | [Ответы API, SQL и логи](evidence/2026-09-22/AUTH-02/result.md) |
| AUTH-03 | Статус `INACTIVE` | `DECLINED`, `CARD_INACTIVE`, код `05` | HTTP 403, DECLINED / 05 / CARD_INACTIVE; B=100000, D=0, M=0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-03/result.md) |
| AUTH-04 | Статус `BLOCKED` | `DECLINED`, `CARD_BLOCKED`, код `05` | HTTP 403, DECLINED / 05 / CARD_BLOCKED; B=100000, D=0, M=0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-04/result.md) |
| AUTH-05 | Статус `EXPIRED` | `DECLINED`, `CARD_EXPIRED`, код `54` | HTTP 403, DECLINED / 54 / CARD_EXPIRED; B=100000, D=0, M=0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-05/result.md) |
| AUTH-06 | `ACTIVE`, expiryDate — предыдущий месяц | `DECLINED`, `CARD_EXPIRED`, код `54` | HTTP 403, DECLINED / 54 / CARD_EXPIRED; B=100000, D=0, M=0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-06/result.md) |
| AUTH-07 | `ACTIVE`, expiryDate — текущий месяц | Проверка срока пройдена; решение зависит от лимитов и баланса | HTTP 200, APPROVED / 00 / —; B=9997, D=90003.0, M=90003.0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-07/result.md) |
| AUTH-08-01 | `B=ML=500000, DL=150000, D=M=0, A=149999` | `APPROVED / 00`; B уменьшается, D и M увеличиваются на A; DL/ML прежние | HTTP 200, APPROVED / 00 / —; B=350001, D=149999.0, M=149999.0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-08-01/result.md) |
| AUTH-08-02 | `B=ML=500000, DL=150000, D=M=0, A=150000` | `APPROVED / 00`; B уменьшается, D и M увеличиваются на A; DL/ML прежние | HTTP 200, APPROVED / 00 / —; B=350000, D=150000.0, M=150000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-08-02/result.md) |
| AUTH-08-03 | `B=ML=500000, DL=150000, D=M=0, A=150001` | `DECLINED / 61 / EXCEEDS_AMOUNT_LIMIT`; B, D, M не изменены | HTTP 400, DECLINED / 61 / EXCEEDS_AMOUNT_LIMIT; B=500000, D=0, M=0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-08-03/result.md) |
| AUTH-09-01 | `B=500000, DL=200000, ML=400000, D=0, M=300000, A=99999` | `APPROVED / 00`; B уменьшается, D и M увеличиваются на A; DL/ML прежние | HTTP 200, APPROVED / 00 / —; B=400001, D=99999.0, M=399999.0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-09-01/result.md) |
| AUTH-09-02 | `B=500000, DL=200000, ML=400000, D=0, M=300000, A=100000` | `APPROVED / 00`; B уменьшается, D и M увеличиваются на A; DL/ML прежние | HTTP 200, APPROVED / 00 / —; B=400000, D=100000.0, M=400000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-09-02/result.md) |
| AUTH-09-03 | `B=500000, DL=200000, ML=400000, D=0, M=300000, A=100001` | `DECLINED / 61 / EXCEEDS_AMOUNT_LIMIT`; B, D, M не изменены | HTTP 400, DECLINED / 61 / EXCEEDS_AMOUNT_LIMIT; B=500000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-09-03/result.md) |
| AUTH-10-01 | `B=100000, DL=ML=500000, D=M=0, A=99999` | `APPROVED / 00`; B уменьшается, D и M увеличиваются на A; DL/ML прежние | HTTP 200, APPROVED / 00 / —; B=1, D=99999.0, M=99999.0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-10-01/result.md) |
| AUTH-10-02 | `B=100000, DL=ML=500000, D=M=0, A=100000` | `APPROVED / 00`; B уменьшается, D и M увеличиваются на A; DL/ML прежние | HTTP 200, APPROVED / 00 / —; B=0, D=100000.0, M=100000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-10-02/result.md) |
| AUTH-10-03 | `B=100000, DL=ML=500000, D=M=0, A=100001` | `DECLINED / 51 / INSUFFICIENT_FUNDS`; B, D, M не изменены | HTTP 422, DECLINED / 51 / INSUFFICIENT_FUNDS; B=100000, D=0, M=0 | Passed | [Запросы и результаты](evidence/2026-09-22/AUTH-10-03/result.md) |
| AUTH-11 | Одновременно `D+A > DL` и `A > B` | По строгому порядку проверок код `61` | HTTP 422, DECLINED / 51 / INSUFFICIENT_FUNDS; B=50, D=0, M=0 | Failed | [Запросы и результаты](evidence/2026-09-22/AUTH-11/result.md); [BUG-P2-002](test-design.md#bug-p2-002) |
| AUTH-12 | CMS недоступен при чтении карты | `DECLINED`, `ISSUER_TIMEOUT`, код `05` | HTTP 503, DECLINED / 96 / SERVICE_UNAVAILABLE; B=100000, D=0, M=0 | Failed | [Запросы и результаты](evidence/2026-09-22/AUTH-12/result.md); [BUG-P2-004](test-design.md#bug-p2-004) |
| AUTH-13 | `B=DL=ML=500000`, `D=M=0`; два запроса по `90003` в одну секунду | Оба запроса одобрены; оба RRN уникальны и соответствуют формату из ТЗ | Оба APPROVED / 00; RRN 626500001459, 626500001460 уникальны, но не соответствуют формату времени из ТЗ; B=319994, D=M=180006 | Failed | [Запросы и результаты](evidence/2026-09-22/AUTH-13/result.md); [BUG-P2-003](test-design.md#bug-p2-003) |
| AUTH-15 | CMS отвечает на чтение карты, но недоступен при reserve | `DECLINED`, `ISSUER_TIMEOUT`, код `05`; баланс и использованные суммы не изменены | HTTP 503, DECLINED / 96 / SERVICE_UNAVAILABLE; B=100000, D=90003.0, M=90003.0 | Failed | [Запросы и результаты](evidence/2026-09-22/AUTH-15/result.md); [BUG-P2-004](test-design.md#bug-p2-004), [BUG-P2-005](test-design.md#bug-p2-005) |

## Card Management

Общие предусловия — из раздела 6.2 тест-дизайна. Для CMS-07 подготовить неизменяемую выборку с известным порядком; иначе проверку конкретного смещения нельзя однозначно оценить.

Шесть [PICT-вариантов CMS-01](pict/cms-cases.txt) — **6/6 Passed**: POST `201`, GET `200`, поля в БД совпадают. [Запросы и результаты по строкам набора](evidence/2026-09-25/CMS-01/result.md). Строка CMS-01 ниже сохраняет результат исходного запроса.

| ID | Данные / сценарий | Ожидаемый результат | Фактический результат | Статус | Материалы / дефект |
|---|---|---|---|---|---|
| CMS-01 | `POST /api/cards`, BIN `400000`, B=100000, DL=150000, ML=300000 | `201`; PAN из 16 цифр проходит Луна; expiryDate = текущий месяц + 3 года; статус `ACTIVE` | HTTP `201`; PAN `4000002848766224`, 16 цифр, Луна пройдена; `ACTIVE`, срок `2029-09`; B=100000, DL=150000, ML=300000 | Passed | [Запрос, ответ и проверки](evidence/2026-09-22/CMS-01/result.md) |
| CMS-02 | `GET /api/cards/4000002848766224` | `200`, поля совпадают с созданными | HTTP `200`; поля совпадают с CMS-01; B=100000, DL=150000, ML=300000 | Passed | [Запрос, ответ и проверки](evidence/2026-09-22/CMS-02/result.md) |
| CMS-03 | `GET /api/cards/{pan}` с отсутствующим корректным PAN | `404` | HTTP 404 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-03/result.md) |
| CMS-04-01 | GET карты: PAN из 15 цифр | `400`, ошибка валидации | HTTP 400 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-04-01/result.md) |
| CMS-04-02 | GET карты: PAN из 17 цифр | `400`, ошибка валидации | HTTP 400 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-04-02/result.md) |
| CMS-04-03 | GET карты: PAN длиной 16 символов с буквой | `400`, ошибка валидации | HTTP 400 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-04-03/result.md) |
| CMS-05 | `GET /api/cards` без query-параметров при наличии >50 карт | При наличии >50 карт возвращаются 50; `total` отражает полное количество | HTTP 200; total=633, возвращено 10 вместо 50 | Failed | [Запросы и результаты](evidence/2026-09-22/CMS-05/result.md); [BUG-P2-001](test-design.md#bug-p2-001) |
| CMS-06-01 | `GET /api/cards?limit=0` | Запрос отклонён | HTTP 400 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-06-01/result.md) |
| CMS-06-02 | `GET /api/cards?limit=1` | Список содержит не более 1 карт | HTTP 200; карт: 1 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-06-02/result.md) |
| CMS-06-03 | `GET /api/cards?limit=2` | Список содержит не более 2 карт | HTTP 200; карт: 2 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-06-03/result.md) |
| CMS-07-01 | `GET /api/cards?limit=1&offset=-1` | Запрос отклонён | HTTP 400 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-07-01/result.md) |
| CMS-07-02 | `GET /api/cards?limit=1&offset=0` | Первая запись выборки | HTTP 200; PAN 4000019534601507 совпадает с элементом 1 контрольной страницы | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-07-02/result.md) |
| CMS-07-03 | `GET /api/cards?limit=1&offset=1` | Пропущена одна запись выборки | HTTP 200; PAN 4000041852722509 совпадает с элементом 2 контрольной страницы | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-07-03/result.md) |
| CMS-08-01 | `GET /api/cards?status=ACTIVE`; есть карты каждого статуса | В ответе только карты `ACTIVE` | HTTP 200; 590 карт, все ACTIVE; total=590 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-08-01/result.md) |
| CMS-08-02 | `GET /api/cards?status=INACTIVE`; есть карты каждого статуса | В ответе только карты `INACTIVE` | HTTP 200; 23 карт, все INACTIVE; total=23 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-08-02/result.md) |
| CMS-08-03 | `GET /api/cards?status=BLOCKED`; есть карты каждого статуса | В ответе только карты `BLOCKED` | HTTP 200; 18 карт, все BLOCKED; total=18 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-08-03/result.md) |
| CMS-08-04 | `GET /api/cards?status=EXPIRED`; есть карты каждого статуса | В ответе только карты `EXPIRED` | HTTP 200; 6 карт, все EXPIRED; total=6 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-08-04/result.md) |
| CMS-09-01 | Фильтр по BIN с существующими картами | Только карты указанного BIN | HTTP 200; BIN 400000, total=157, карт: 157 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-09-01/result.md) |
| CMS-09-02 | Фильтр по корректному BIN без карт | `total=0`, пустой массив | HTTP 200; BIN 999999, total=0, карт: 0 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-09-02/result.md) |
| CMS-10 | `PATCH /api/cards/{pan}` с `dailyLimit=160000` | Меняется только `dailyLimit`, остальные поля сохраняются | HTTP 200; DL=160000; остальные поля совпадают | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-10/result.md) |
| CMS-12 | `DELETE /api/cards/{pan}`, затем GET карты и `GET /api/cards` | `204`; карта больше не выдаётся и не участвует в авторизации | DELETE 204, GET 404; DELETED, в списке отсутствует; авторизация DECLINED / 14 / CARD_NOT_FOUND | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-12/result.md) |
| CMS-13 | `POST /api/cards/generate`, `count=100`, пять существующих BIN | `201`, `generated=100`; распределение по BIN равномерное, PAN валидны | HTTP 201; generated=100; по 20 карт на каждый BIN; 100 PAN валидны и уникальны | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-13/result.md) |
| CMS-14 | `POST /api/cards/generate` с `count=100`; подсчитать статусы карт в ответе | Ожидаемое распределение: 95 `ACTIVE`, 3 `INACTIVE`, 2 `BLOCKED` | HTTP 201; generated=100; статусы: {'ACTIVE': 95, 'INACTIVE': 3, 'BLOCKED': 2} | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-14/result.md) |
| CMS-15 | `POST /api/cards/{pan}/reserve`, `amount=B`, новый RRN из 12 цифр | `200`, доступный баланс становится `0` | HTTP 200; B=0; резервирований: 1 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-15/result.md) |
| CMS-16 | `POST /api/cards/{pan}/reserve`, `amount=B+1`, новый RRN из 12 цифр | Средства не списаны; ошибка недостаточного баланса | HTTP 402; B=100000; резервирований: 0 | Passed | [Запросы и результаты](evidence/2026-09-22/CMS-16/result.md) |

## Попарные сценарии PICT

Строки соответствуют порядку в [pict/cases.txt](pict/cases.txt). RD и RM — остатки лимитов. Перед каждой операцией D=0, M=300000, DL=RD, ML=300000+RM; дата и история операций готовятся по разделу 5 тест-дизайна. После изменения модели и повторной генерации набора эти строки нужно обновить.

| ID | Данные / сценарий | Ожидаемый результат | Фактический результат | Статус | Материалы / дефект |
|---|---|---|---|---|---|
| PW-01 | `EXPIRED`, `PAST`, A=90003, B=100000, RD=100000, RM=100000, ECOM, MCC=5411 | `DECLINED / 54 / CARD_EXPIRED`; B=100000, D=0, M=300000 без изменений | HTTP `403`, `DECLINED / 54 / CARD_EXPIRED`; B=100000, D=0, M=300000; резервирований нет | Passed | [Ответ API и SQL](evidence/2026-09-22/PW-01/result.md) |
| PW-02 | `INACTIVE`, `CURRENT`, A=100000, B=200000, RD=200000, RM=200000, ATM, MCC=5812 | `DECLINED / 05 / CARD_INACTIVE`; B=200000, D=0, M=300000 без изменений | HTTP `403`, `DECLINED / 05 / CARD_INACTIVE`; B=200000, D=0, M=300000; резервирований нет | Passed | [Ответ API и SQL](evidence/2026-09-22/PW-02/result.md) |
| PW-03 | `INACTIVE`, `FUTURE`, A=100001, B=200000, RD=100000, RM=200000, POS, MCC=5411 | `DECLINED / 05 / CARD_INACTIVE`; B=200000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 05 / CARD_INACTIVE; B=200000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-03/result.md) |
| PW-04 | `EXPIRED`, `PAST`, A=100001, B=100000, RD=200000, RM=100000, ATM, MCC=5812 | `DECLINED / 54 / CARD_EXPIRED`; B=100000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 54 / CARD_EXPIRED; B=100000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-04/result.md) |
| PW-05 | `EXPIRED`, `PAST`, A=100000, B=200000, RD=100000, RM=100000, POS, MCC=5812 | `DECLINED / 54 / CARD_EXPIRED`; B=200000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 54 / CARD_EXPIRED; B=200000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-05/result.md) |
| PW-06 | `BLOCKED`, `CURRENT`, A=90003, B=100000, RD=200000, RM=200000, POS, MCC=5411 | `DECLINED / 05 / CARD_BLOCKED`; B=100000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 05 / CARD_BLOCKED; B=100000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-06/result.md) |
| PW-07 | `ACTIVE`, `FUTURE`, A=100000, B=100000, RD=100000, RM=100000, ATM, MCC=5411 | `APPROVED / 00`; B=0, D=100000, M=400000; DL/ML прежние | HTTP 200, APPROVED / 00 / —; B=0, D=100000.0, M=400000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-07/result.md) |
| PW-08 | `ACTIVE`, `FUTURE`, A=90003, B=200000, RD=200000, RM=200000, POS, MCC=5812 | `APPROVED / 00`; B=109997, D=90003, M=390003; DL/ML прежние | HTTP 200, APPROVED / 00 / —; B=109997, D=90003.0, M=390003.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-08/result.md) |
| PW-09 | `ACTIVE`, `PAST`, A=100001, B=200000, RD=200000, RM=200000, ECOM, MCC=5812 | `DECLINED / 54 / CARD_EXPIRED`; B=200000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 54 / CARD_EXPIRED; B=200000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-09/result.md) |
| PW-10 | `BLOCKED`, `FUTURE`, A=100000, B=200000, RD=100000, RM=100000, ECOM, MCC=5812 | `DECLINED / 05 / CARD_BLOCKED`; B=200000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 05 / CARD_BLOCKED; B=200000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-10/result.md) |
| PW-11 | `BLOCKED`, `PAST`, A=90003, B=200000, RD=200000, RM=200000, ATM, MCC=5812 | `DECLINED / 05 / CARD_BLOCKED`; B=200000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 05 / CARD_BLOCKED; B=200000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-11/result.md) |
| PW-12 | `INACTIVE`, `PAST`, A=90003, B=100000, RD=100000, RM=100000, ECOM, MCC=5411 | `DECLINED / 05 / CARD_INACTIVE`; B=100000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 05 / CARD_INACTIVE; B=100000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-12/result.md) |
| PW-13 | `BLOCKED`, `CURRENT`, A=100001, B=100000, RD=100000, RM=100000, ECOM, MCC=5411 | `DECLINED / 05 / CARD_BLOCKED`; B=100000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 05 / CARD_BLOCKED; B=100000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-13/result.md) |
| PW-14 | `EXPIRED`, `PAST`, A=100000, B=100000, RD=100000, RM=200000, POS, MCC=5411 | `DECLINED / 54 / CARD_EXPIRED`; B=100000, D=0, M=300000 без изменений | HTTP 403, DECLINED / 54 / CARD_EXPIRED; B=100000, D=0, M=300000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-14/result.md) |
| PW-15 | `ACTIVE`, `CURRENT`, A=100000, B=100000, RD=100000, RM=100000, POS, MCC=5812 | `APPROVED / 00`; B=0, D=100000, M=400000; DL/ML прежние | HTTP 200, APPROVED / 00 / —; B=0, D=100000.0, M=400000.0 | Passed | [Запросы и результаты](evidence/2026-09-22/PW-15/result.md) |

## Итоги исходного прогона

| Показатель | Количество |
|---|---:|
| Passed | 55 |
| Failed | 5 |
| Blocked | 0 |
| Not run | 0 |

- Выполнены все 60 строк журнала, включая 15 PW-сценариев.
- С отклонениями: AUTH-11, AUTH-12, AUTH-13, AUTH-15, CMS-05. Дефекты: [BUG-P2-001—BUG-P2-005](test-design.md#7-баг-репорты).
- Все 60 исходных строк выполнены. Дополнительно выполнены шесть PICT-вариантов CMS-01: 6 Passed, 0 Failed; в итоги исходного прогона они не включены.
