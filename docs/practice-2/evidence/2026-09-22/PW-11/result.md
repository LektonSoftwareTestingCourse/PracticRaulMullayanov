# PW-11

Результат: **Passed**.

## Создание карты

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "currencyCode": "643",
  "dailyLimit": 200000,
  "monthlyLimit": 500000,
  "initialBalance": 200000
}'
```

Ответ: HTTP `201`.

```json
{
  "id": "4cfd75c2-edbc-4dd8-8cd0-2a227251a34b",
  "pan": "4000003906069154",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2029-09",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 200000,
  "monthlyLimit": 500000,
  "availableBalance": 200000,
  "issuerId": "ISS001"
}
```

## Подготовка данных

```sql
UPDATE cards SET status='BLOCKED', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '-1 month', 'MMYY') WHERE pan='4000003906069154';
INSERT INTO limit_usage(id,pan,usage_date,daily_amount,monthly_amount) VALUES(gen_random_uuid(),'4000003906069154',(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1,300000,300000);
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710009\",
  \"pan\": \"4000003906069154\",
  \"processingCode\": \"000000\",
  \"amount\": 90003,
  \"currencyCode\": \"643\",
  \"transmissionDateTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
  \"terminalId\": \"TERM0001\",
  \"terminalType\": \"ATM\",
  \"merchantId\": \"MERCH0000000002\",
  \"mcc\": \"5812\",
  \"acquirerId\": \"ACQ002\",
  \"issuerId\": \"ISS001\"
}"
```

Ответ: HTTP `403`.

```json
{
  "mti": "0100",
  "stan": "710009",
  "rrn": null,
  "authCode": null,
  "responseCode": "05",
  "status": "DECLINED",
  "declineReason": "CARD_BLOCKED"
}
```

## Карта после операции

```sh
curl -i -X GET http://localhost:8081/api/cards/4000003906069154
```

Ответ: HTTP `200`.

```json
{
  "id": "4cfd75c2-edbc-4dd8-8cd0-2a227251a34b",
  "pan": "4000003906069154",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-08",
  "status": "BLOCKED",
  "currencyCode": "643",
  "dailyLimit": 200000,
  "monthlyLimit": 500000,
  "availableBalance": 200000,
  "issuerId": "ISS001"
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000003906069154';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000003906069154'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000003906069154';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | BLOCKED | 0826 | 200000 | 200000 | 500000 | 0 | 300000 | 0 |
| После | BLOCKED | 0826 | 200000 | 200000 | 500000 | 0 | 300000 | 0 |

Использование после запроса (`day_offset`: `0` — день прогона, `-1` — предыдущий день):

```json
[
  {
    "day_offset": -1,
    "daily_amount": 300000.0,
    "monthly_amount": 300000.0
  }
]
```

## Проверки

- [x] Решение и причина отказа соответствуют ожидаемым.
- [x] Баланс соответствует результату операции.
- [x] Использование лимитов соответствует результату операции.
- [x] Настройки лимитов сохранены.
- [x] Резервирований нет.
