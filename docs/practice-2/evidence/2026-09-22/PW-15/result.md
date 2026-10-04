# PW-15

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
  "id": "83bfb637-db31-42af-8488-3645c17bebf2",
  "pan": "4000009200617010",
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
UPDATE cards SET status='ACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '0 month', 'MMYY') WHERE pan='4000009200617010';
INSERT INTO limit_usage(id,pan,usage_date,daily_amount,monthly_amount) VALUES(gen_random_uuid(),'4000009200617010',(CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date - 1,300000,300000);
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710013\",
  \"pan\": \"4000009200617010\",
  \"processingCode\": \"000000\",
  \"amount\": 100000,
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
  "stan": "710013",
  "rrn": "626500001458",
  "authCode": "ESX14U",
  "responseCode": "00",
  "status": "APPROVED",
  "declineReason": null
}
```

## Карта после операции

```sh
curl -i -X GET http://localhost:8081/api/cards/4000009200617010
```

Ответ: HTTP `200`.

```json
{
  "id": "83bfb637-db31-42af-8488-3645c17bebf2",
  "pan": "4000009200617010",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-09",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 100000,
  "monthlyLimit": 400000,
  "availableBalance": 0,
  "issuerId": "ISS001"
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000009200617010';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000009200617010'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000009200617010';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | ACTIVE | 0926 | 100000 | 100000 | 400000 | 0 | 300000 | 0 |
| После | ACTIVE | 0926 | 0 | 100000 | 400000 | 100000 | 400000 | 1 |

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
    "daily_amount": 100000.0,
    "monthly_amount": 400000.0
  }
]
```

Резервирования после запроса:

```json
[
  {
    "rrn": "626500001458",
    "amount": 100000,
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
