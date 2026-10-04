# PW-12

Результат: **Passed**.

## Создание карты

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "currencyCode": "643",
  "dailyLimit": 100000,
  "monthlyLimit": 400000,
  "initialBalance": 100000
}'
```

Ответ: HTTP `201`.

```json
{
  "id": "281bfadf-0d39-4465-ad50-85e6638bb5f0",
  "pan": "4000005859015829",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2029-09",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 100000,
  "monthlyLimit": 400000,
  "availableBalance": 100000,
  "issuerId": "ISS001"
}
```

## Подготовка данных

```sql
UPDATE cards SET status='INACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '-1 month', 'MMYY') WHERE pan='4000005859015829';
INSERT INTO limit_usage(id,pan,usage_date,daily_amount,monthly_amount) VALUES(gen_random_uuid(),'4000005859015829',(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1,300000,300000);
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710010\",
  \"pan\": \"4000005859015829\",
  \"processingCode\": \"000000\",
  \"amount\": 90003,
  \"currencyCode\": \"643\",
  \"transmissionDateTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
  \"terminalId\": \"TERM0001\",
  \"terminalType\": \"ECOM\",
  \"merchantId\": \"MERCH0000000002\",
  \"mcc\": \"5411\",
  \"acquirerId\": \"ACQ002\",
  \"issuerId\": \"ISS001\"
}"
```

Ответ: HTTP `403`.

```json
{
  "mti": "0100",
  "stan": "710010",
  "rrn": null,
  "authCode": null,
  "responseCode": "05",
  "status": "DECLINED",
  "declineReason": "CARD_INACTIVE"
}
```

## Карта после операции

```sh
curl -i -X GET http://localhost:8081/api/cards/4000005859015829
```

Ответ: HTTP `200`.

```json
{
  "id": "281bfadf-0d39-4465-ad50-85e6638bb5f0",
  "pan": "4000005859015829",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-08",
  "status": "INACTIVE",
  "currencyCode": "643",
  "dailyLimit": 100000,
  "monthlyLimit": 400000,
  "availableBalance": 100000,
  "issuerId": "ISS001"
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000005859015829';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000005859015829'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000005859015829';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | INACTIVE | 0826 | 100000 | 100000 | 400000 | 0 | 300000 | 0 |
| После | INACTIVE | 0826 | 100000 | 100000 | 400000 | 0 | 300000 | 0 |

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
