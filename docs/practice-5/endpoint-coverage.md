# Фактическая матрица HTTP-покрытия — практика 5

Источник: итоговый regression-run, отдельный fault-run и строгий контрактный прогон.
Колонка «кейсы» — количество различных JUnit invocation labels, выполнивших HTTP-вызов с assertions.
Подготовка/cleanup тоже проверяют ответы; поэтому значения разных строк не складываются в число тестов.
Повторы Awaitility не увеличивают число кейсов. Статусы строгого набора включают обнаруженные нарушения.

| Сервис | Метод / путь | Наблюдаемые статусы | Кейсы |
|---|---|---|---:|
| Gateway | `DELETE /api/cards/400000123456789` | 400 | 1 |
| Gateway | `DELETE /api/cards/40000012345678901` | 400 | 1 |
| Gateway | `DELETE /api/cards/400000123456789A` | 400 | 1 |
| Gateway | `DELETE /api/cards/{pan}` | 204, 404, 503 | 4 |
| Gateway | `GET /api/bins` | 404 | 1 |
| Gateway | `GET /api/cards` | 200, 400, 413, 503 | 23 |
| Gateway | `GET /api/cards/400000123456789` | 400 | 1 |
| Gateway | `GET /api/cards/40000012345678901` | 400 | 1 |
| Gateway | `GET /api/cards/400000123456789A` | 400 | 1 |
| Gateway | `GET /api/cards/{pan}` | 200, 404, 503 | 5 |
| Gateway | `GET /api/dashboard/charts` | 200, 400, 503 | 7 |
| Gateway | `GET /api/dashboard/recent` | 200, 400, 503 | 7 |
| Gateway | `GET /api/dashboard/stats` | 200, 503 | 2 |
| Gateway | `GET /api/notifications` | 404 | 1 |
| Gateway | `GET /api/simulator/merchants` | 404 | 1 |
| Gateway | `GET /api/transactions/export` | 200, 400, 503 | 11 |
| Gateway | `GET /api/transactions/search` | 200, 400, 503 | 107 |
| Gateway | `GET /cards-docs` | 200 | 1 |
| Gateway | `GET /health` | 200 | 1 |
| Gateway | `GET /logger-docs` | 200 | 1 |
| Gateway | `GET /merchant-docs` | 200 | 1 |
| Gateway | `GET /switch-docs` | 200 | 1 |
| Gateway | `GET /terminal-docs` | 200 | 1 |
| Gateway | `PATCH /api/cards/400000123456789` | 400 | 1 |
| Gateway | `PATCH /api/cards/40000012345678901` | 400 | 1 |
| Gateway | `PATCH /api/cards/400000123456789A` | 400 | 1 |
| Gateway | `PATCH /api/cards/{pan}` | 200, 400, 404, 503 | 36 |
| Gateway | `POST /api/cards` | 201, 400, 404, 503 | 120 |
| Gateway | `POST /api/cards/400000123456789/reserve` | 400 | 1 |
| Gateway | `POST /api/cards/400000123456789/rollback` | 400 | 1 |
| Gateway | `POST /api/cards/40000012345678901/reserve` | 400 | 1 |
| Gateway | `POST /api/cards/40000012345678901/rollback` | 400 | 1 |
| Gateway | `POST /api/cards/400000123456789A/reserve` | 400 | 1 |
| Gateway | `POST /api/cards/400000123456789A/rollback` | 400 | 1 |
| Gateway | `POST /api/cards/bulk-update` | 200, 400 | 6 |
| Gateway | `POST /api/cards/generate` | 201, 400, 404 | 8 |
| Gateway | `POST /api/cards/{pan}/reserve` | 200, 400, 402, 404, 409, 500 | 12 |
| Gateway | `POST /api/cards/{pan}/rollback` | 200, 400, 404, 409 | 9 |
| Gateway | `POST /api/simulator/merchant/fee` | 200, 400, 404 | 6 |
| Gateway | `POST /api/simulator/merchant/run` | 200, 400, 404, 503 | 13 |
| Gateway | `POST /api/simulator/terminal/run` | 200, 400, 503 | 13 |
| Gateway | `POST /api/simulator/terminal/start-continuous` | 200, 400, 422 | 4 |
| Gateway | `POST /api/simulator/terminal/stop` | 200 | 1 |
| Gateway | `POST /api/transactions` | 200, 400, 429, 503 | 68 |
| CMS | `DELETE /api/bins/{bin}` | 204, 404 | 17 |
| CMS | `DELETE /api/cards/{pan}` | 204, 404 | 102 |
| CMS | `GET /api/bins` | 200 | 1 |
| CMS | `GET /api/bins/{bin}` | 200, 404 | 1 |
| CMS | `GET /api/cards/{pan}` | 200, 404 | 68 |
| CMS | `GET /health` | 200 | 1 |
| CMS | `POST /api/bins` | 201, 400, 409 | 20 |
| CMS | `POST /api/cards/{pan}/reserve` | 200 | 1 |
| Switch | `GET /actuator/health` | 200, 503 | 1 |
| Switch | `GET /health` | 200 | 1 |
| Switch | `POST /api/internal/route` | 200, 400 | 5 |
| Authorization | `GET /health` | 200 | 1 |
| Authorization | `POST /api/internal/authorize` | 200, 400, 403, 404, 422, 500, 503 | 81 |
| Authorization | `POST /api/internal/rollback` | 200, 400, 404, 409, 503 | 24 |
| Merchant | `GET /api/simulator/merchants` | 200 | 1 |
| Merchant | `GET /health` | 200 | 1 |
| Merchant | `POST /api/simulator/merchant/run` | 502 | 1 |
| Terminal | `GET /health` | 200 | 1 |
| Logger | `GET /api/internal/log` | 405 | 1 |
| Logger | `GET /health` | 200 | 1 |
| Logger | `POST /api/internal/log` | 200, 201, 400, 409 | 100 |
| Bin Lookup | `GET /actuator/health` | 200 | 1 |
| Bin Lookup | `GET /api/bin/{bin}` | 200, 400, 404, 500 | 8 |
| Notification | `GET /actuator/health` | 200 | 1 |
| Notification | `GET /api/notifications` | 200 | 3 |
