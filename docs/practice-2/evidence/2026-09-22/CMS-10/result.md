# CMS-10

Результат: **Passed**.

## Создание карты

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "currencyCode": "643",
  "dailyLimit": 150000,
  "monthlyLimit": 300000,
  "initialBalance": 100000
}'
```

Ответ: HTTP `201`.

```json
{
  "id": "dc80d9dd-5bd0-4537-bfe1-a9d47dfb1110",
  "pan": "4000001417272077",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2029-09",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 150000,
  "monthlyLimit": 300000,
  "availableBalance": 100000,
  "issuerId": "ISS001"
}
```

## Подготовка данных

```sql
UPDATE cards SET status='ACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000001417272077';
```

## Карта до PATCH

```sh
curl -i -X GET http://localhost:8081/api/cards/4000001417272077
```

Ответ: HTTP `200`.

```json
{
  "id": "dc80d9dd-5bd0-4537-bfe1-a9d47dfb1110",
  "pan": "4000001417272077",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-10",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 150000,
  "monthlyLimit": 300000,
  "availableBalance": 100000,
  "issuerId": "ISS001"
}
```

## Запрос

```sh
curl -i -X PATCH http://localhost:8081/api/cards/4000001417272077 \
  -H 'Content-Type: application/json' \
  -d '{
  "dailyLimit": 160000
}'
```

Ответ: HTTP `200`.

```json
{
  "id": "dc80d9dd-5bd0-4537-bfe1-a9d47dfb1110",
  "pan": "4000001417272077",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-10",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 160000,
  "monthlyLimit": 300000,
  "availableBalance": 100000,
  "issuerId": "ISS001"
}
```

## Карта после PATCH

```sh
curl -i -X GET http://localhost:8081/api/cards/4000001417272077
```

Ответ: HTTP `200`.

```json
{
  "id": "dc80d9dd-5bd0-4537-bfe1-a9d47dfb1110",
  "pan": "4000001417272077",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-10",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 160000,
  "monthlyLimit": 300000,
  "availableBalance": 100000,
  "issuerId": "ISS001"
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000001417272077';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000001417272077'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000001417272077';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | ACTIVE | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |
| После | ACTIVE | 1026 | 100000 | 160000 | 300000 | 0 | 0 | 0 |

## Проверки

- [x] Обновлён только dailyLimit.
