# CMS-01 — попарные варианты создания карты

Результат: **6/6 Passed**. Номера вариантов соответствуют строкам [cms-cases.txt](../../../pict/cms-cases.txt) после заголовка.

| Строка PICT | PAN | POST | GET | БД | Статус |
|---|---|---|---|---|---|
| 1 | `4000007322603728` | 201 | 200 | Совпадает | Passed |
| 2 | `4000017538670197` | 201 | 200 | Совпадает | Passed |
| 3 | `4000008030158666` | 201 | 200 | Совпадает | Passed |
| 4 | `4000001711435834` | 201 | 200 | Совпадает | Passed |
| 5 | `4000012522582961` | 201 | 200 | Совпадает | Passed |
| 6 | `4000011844325786` | 201 | 200 | Совпадает | Passed |

## Вариант 1

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "currencyCode": "840",
  "dailyLimit": 200000,
  "monthlyLimit": 300000,
  "initialBalance": 100000
}'
```

HTTP `201`.

```json
{"id":"9aae7c6c-5690-411c-ba2e-6bb1683c718e","pan":"4000007322603728","bin":"400000","cardholderName":"IVAN IVANOV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"840","dailyLimit":200000,"monthlyLimit":300000,"availableBalance":100000,"issuerId":"ISS001"}
```

```sh
curl -i http://localhost:8081/api/cards/4000007322603728
```

HTTP `200`; ID, PAN, BIN, имя, статус, срок, валюта, лимиты, баланс и issuerId совпадают с ответом создания.

## Вариант 2

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400001",
  "cardholderName": "PETR PETROV",
  "currencyCode": "643",
  "dailyLimit": 100000,
  "monthlyLimit": 300000,
  "initialBalance": 200000
}'
```

HTTP `201`.

```json
{"id":"0e20c556-55e5-4893-8136-33eb19e80363","pan":"4000017538670197","bin":"400001","cardholderName":"PETR PETROV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"643","dailyLimit":100000,"monthlyLimit":300000,"availableBalance":200000,"issuerId":"ISS002"}
```

```sh
curl -i http://localhost:8081/api/cards/4000017538670197
```

HTTP `200`; ID, PAN, BIN, имя, статус, срок, валюта, лимиты, баланс и issuerId совпадают с ответом создания.

## Вариант 3

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400000",
  "cardholderName": "PETR PETROV",
  "currencyCode": "840",
  "dailyLimit": 200000,
  "monthlyLimit": 500000,
  "initialBalance": 200000
}'
```

HTTP `201`.

```json
{"id":"81574cc1-1d8d-4e54-9aef-ee496b59b7bc","pan":"4000008030158666","bin":"400000","cardholderName":"PETR PETROV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"840","dailyLimit":200000,"monthlyLimit":500000,"availableBalance":200000,"issuerId":"ISS001"}
```

```sh
curl -i http://localhost:8081/api/cards/4000008030158666
```

HTTP `200`; ID, PAN, BIN, имя, статус, срок, валюта, лимиты, баланс и issuerId совпадают с ответом создания.

## Вариант 4

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400000",
  "cardholderName": "PETR PETROV",
  "currencyCode": "643",
  "dailyLimit": 100000,
  "monthlyLimit": 500000,
  "initialBalance": 100000
}'
```

HTTP `201`.

```json
{"id":"587addb9-7330-4191-90a3-69764e06494e","pan":"4000001711435834","bin":"400000","cardholderName":"PETR PETROV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"643","dailyLimit":100000,"monthlyLimit":500000,"availableBalance":100000,"issuerId":"ISS001"}
```

```sh
curl -i http://localhost:8081/api/cards/4000001711435834
```

HTTP `200`; ID, PAN, BIN, имя, статус, срок, валюта, лимиты, баланс и issuerId совпадают с ответом создания.

## Вариант 5

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400001",
  "cardholderName": "IVAN IVANOV",
  "currencyCode": "840",
  "dailyLimit": 100000,
  "monthlyLimit": 500000,
  "initialBalance": 100000
}'
```

HTTP `201`.

```json
{"id":"93f11730-97db-4edf-8a53-d649a1b0768f","pan":"4000012522582961","bin":"400001","cardholderName":"IVAN IVANOV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"840","dailyLimit":100000,"monthlyLimit":500000,"availableBalance":100000,"issuerId":"ISS002"}
```

```sh
curl -i http://localhost:8081/api/cards/4000012522582961
```

HTTP `200`; ID, PAN, BIN, имя, статус, срок, валюта, лимиты, баланс и issuerId совпадают с ответом создания.

## Вариант 6

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400001",
  "cardholderName": "IVAN IVANOV",
  "currencyCode": "643",
  "dailyLimit": 200000,
  "monthlyLimit": 300000,
  "initialBalance": 200000
}'
```

HTTP `201`.

```json
{"id":"85624653-258e-4342-ae3e-33030c9493fa","pan":"4000011844325786","bin":"400001","cardholderName":"IVAN IVANOV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"643","dailyLimit":200000,"monthlyLimit":300000,"availableBalance":200000,"issuerId":"ISS002"}
```

```sh
curl -i http://localhost:8081/api/cards/4000011844325786
```

HTTP `200`; ID, PAN, BIN, имя, статус, срок, валюта, лимиты, баланс и issuerId совпадают с ответом создания.

## Проверка БД

```sql
SELECT pan, bin, cardholder_name, status, expiry_date, currency_code,
       daily_limit, monthly_limit, available_balance, issuer_id
FROM cards
WHERE pan IN (
    '4000007322603728',
    '4000017538670197',
    '4000008030158666',
    '4000001711435834',
    '4000012522582961',
    '4000011844325786'
)
ORDER BY pan;
```

| pan | bin | cardholder_name | status | expiry_date | currency_code | daily_limit | monthly_limit | available_balance | issuer_id |
|---|---|---|---|---|---|---|---|---|---|
| 4000001711435834 | 400000 | PETR PETROV | ACTIVE | 0929 | 643 | 100000 | 500000 | 100000 | ISS001 |
| 4000007322603728 | 400000 | IVAN IVANOV | ACTIVE | 0929 | 840 | 200000 | 300000 | 100000 | ISS001 |
| 4000008030158666 | 400000 | PETR PETROV | ACTIVE | 0929 | 840 | 200000 | 500000 | 200000 | ISS001 |
| 4000011844325786 | 400001 | IVAN IVANOV | ACTIVE | 0929 | 643 | 200000 | 300000 | 200000 | ISS002 |
| 4000012522582961 | 400001 | IVAN IVANOV | ACTIVE | 0929 | 840 | 100000 | 500000 | 100000 | ISS002 |
| 4000017538670197 | 400001 | PETR PETROV | ACTIVE | 0929 | 643 | 100000 | 300000 | 200000 | ISS002 |

## Проверки

- Во всех шести вариантах: POST `201`, GET `200`.
- PAN уникальны, состоят из 16 цифр, начинаются с заданного BIN и проходят алгоритм Луна.
- Статус `ACTIVE`, срок `2029-09` — текущий месяц + 3 года.
- Имя, валюта, дневной и месячный лимиты совпадают с запросом; баланс равен `initialBalance`.
- `issuerId` соответствует BIN: `400000 → ISS001`, `400001 → ISS002`.
- Все шесть записей БД совпадают с запросами и ответами API.
