# CMS-08-02

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
  "id": "20015bb2-8686-4f2b-868c-9b3dda95588a",
  "pan": "4000006587765081",
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
UPDATE cards SET status='INACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000006587765081';
```

## Запрос

```sh
curl -i -X GET 'http://localhost:8081/api/cards?status=INACTIVE&limit=10000'
```

Ответ: HTTP `200`.

Карт в ответе: `23`; `total=23`. [JSON ответа](response-2.json).

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000006587765081';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000006587765081'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000006587765081';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | INACTIVE | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |

## Проверки

- [x] Все карты имеют запрошенный статус.
- [x] Полнота выборки.
