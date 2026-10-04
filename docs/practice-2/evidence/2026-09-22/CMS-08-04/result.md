# CMS-08-04

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
  "id": "ff25e6e1-94f1-4cf6-97c9-579e696413b6",
  "pan": "4000009840924826",
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
UPDATE cards SET status='EXPIRED', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000009840924826';
```

## Запрос

```sh
curl -i -X GET 'http://localhost:8081/api/cards?status=EXPIRED&limit=10000'
```

Ответ: HTTP `200`.

```json
{
  "total": 6,
  "cards": [
    {
      "id": "fb3c39ab-0235-47f6-89eb-b3263f81839e",
      "pan": "4000008625809459",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-08",
      "status": "EXPIRED",
      "currencyCode": "643",
      "dailyLimit": 100000,
      "monthlyLimit": 400000,
      "availableBalance": 100000,
      "issuerId": "ISS001"
    },
    {
      "id": "0d005eb1-7056-4362-b876-7e83fd087326",
      "pan": "4000004068007446",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-10",
      "status": "EXPIRED",
      "currencyCode": "643",
      "dailyLimit": 150000,
      "monthlyLimit": 300000,
      "availableBalance": 100000,
      "issuerId": "ISS001"
    },
    {
      "id": "a74f082e-5a33-4406-aff2-355a25799dbb",
      "pan": "4000001882366834",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-08",
      "status": "EXPIRED",
      "currencyCode": "643",
      "dailyLimit": 200000,
      "monthlyLimit": 400000,
      "availableBalance": 100000,
      "issuerId": "ISS001"
    },
    {
      "id": "4a8c6ab3-c54d-483c-860c-46a23c36e8ea",
      "pan": "4000002146377724",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-08",
      "status": "EXPIRED",
      "currencyCode": "643",
      "dailyLimit": 100000,
      "monthlyLimit": 400000,
      "availableBalance": 200000,
      "issuerId": "ISS001"
    },
    {
      "id": "fb396723-36e6-4f4a-acd9-ff22b33fe7a4",
      "pan": "4000005975010456",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-08",
      "status": "EXPIRED",
      "currencyCode": "643",
      "dailyLimit": 100000,
      "monthlyLimit": 500000,
      "availableBalance": 100000,
      "issuerId": "ISS001"
    },
    {
      "id": "ff25e6e1-94f1-4cf6-97c9-579e696413b6",
      "pan": "4000009840924826",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-10",
      "status": "EXPIRED",
      "currencyCode": "643",
      "dailyLimit": 150000,
      "monthlyLimit": 300000,
      "availableBalance": 100000,
      "issuerId": "ISS001"
    }
  ]
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000009840924826';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000009840924826'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000009840924826';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | EXPIRED | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |

## Проверки

- [x] Все карты имеют запрошенный статус.
- [x] Полнота выборки.
