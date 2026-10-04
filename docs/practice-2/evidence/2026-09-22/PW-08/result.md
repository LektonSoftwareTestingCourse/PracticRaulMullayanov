# PW-08

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
  "id": "227419d9-12ac-4175-bfe6-1ec40b21d4d6",
  "pan": "4000007287496373",
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
UPDATE cards SET status='ACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000007287496373';
INSERT INTO limit_usage(id,pan,usage_date,daily_amount,monthly_amount) VALUES(gen_random_uuid(),'4000007287496373',(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1,300000,300000);
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710006\",
  \"pan\": \"4000007287496373\",
  \"processingCode\": \"000000\",
  \"amount\": 90003,
  \"currencyCode\": \"643\",
  \"transmissionDateTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
  \"terminalId\": \"TERM0001\",
  \"terminalType\": \"POS\",
  \"merchantId\": \"MERCH0000000002\",
  \"mcc\": \"5812\",
  \"acquirerId\": \"ACQ002\",
  \"issuerId\": \"ISS001\"
}"
```

Ответ: HTTP `200`.

```json
{
  "mti": "0100",
  "stan": "710006",
  "rrn": "626500001457",
  "authCode": "OZK6NS",
  "responseCode": "00",
  "status": "APPROVED",
  "declineReason": null
}
```

## Карта после операции

```sh
curl -i -X GET http://localhost:8081/api/cards/4000007287496373
```

Ответ: HTTP `200`.

```json
{
  "id": "227419d9-12ac-4175-bfe6-1ec40b21d4d6",
  "pan": "4000007287496373",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-10",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 200000,
  "monthlyLimit": 500000,
  "availableBalance": 109997,
  "issuerId": "ISS001"
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000007287496373';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000007287496373'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000007287496373';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | ACTIVE | 1026 | 200000 | 200000 | 500000 | 0 | 300000 | 0 |
| После | ACTIVE | 1026 | 109997 | 200000 | 500000 | 90003 | 390003 | 1 |

Использование после запроса (`day_offset`: `0` — день прогона, `-1` — предыдущий день):

```json
[
  {
    "day_offset": -1,
    "daily_amount": 300000.0,
    "monthly_amount": 300000.0
  },
  {
    "day_offset": 0,
    "daily_amount": 90003.0,
    "monthly_amount": 390003.0
  }
]
```

Резервирования после запроса:

```json
[
  {
    "rrn": "626500001457",
    "amount": 90003,
    "status": "RESERVED"
  }
]
```

## Проверки

- [x] Решение и причина отказа соответствуют ожидаемым.
- [x] Баланс соответствует результату операции.
- [x] Использование лимитов соответствует результату операции.
- [x] Настройки лимитов сохранены.
- [x] RRN — 12 цифр, authCode — 6 символов A-Z0-9.
- [x] Резервирование сохранено.
