# CMS-16

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
  "id": "9e8d8ce2-3e46-42d3-b57e-9f27aaef544e",
  "pan": "4000006067186667",
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
UPDATE cards SET status='ACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000006067186667';
```

## Запрос

```sh
curl -i -X POST http://localhost:8081/api/cards/4000006067186667/reserve \
  -H 'Content-Type: application/json' \
  -d '{
  "amount": 100001,
  "rrn": "999999990016"
}'
```

Ответ: HTTP `402`.

```json
{
  "error": "InsufficientFundsException",
  "message": "Not enough funds on this account",
  "serviceName": "card-management",
  "retryAfterMs": null
}
```

## Карта после reserve

```sh
curl -i -X GET http://localhost:8081/api/cards/4000006067186667
```

Ответ: HTTP `200`.

```json
{
  "id": "9e8d8ce2-3e46-42d3-b57e-9f27aaef544e",
  "pan": "4000006067186667",
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

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000006067186667';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000006067186667'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000006067186667';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | ACTIVE | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |
| После | ACTIVE | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |

## Проверки

- [x] Отказ из-за недостаточного баланса.
- [x] Настройки лимитов не изменились.
