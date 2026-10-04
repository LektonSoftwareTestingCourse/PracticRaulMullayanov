# AUTH-13

Результат: **Failed**.

Дефекты: [BUG-P2-003](../../../test-design.md#bug-p2-003).

- Оба запроса и ответа попали в одну секунду UTC.
- В сегменте HHmmss возвращённых RRN нет времени обработки по ТЗ.

## Создание карты

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "currencyCode": "643",
  "dailyLimit": 500000,
  "monthlyLimit": 500000,
  "initialBalance": 500000
}'
```

Ответ: HTTP `201`.

```json
{
  "id": "ddbb6d9b-e3ed-47f7-a960-56b13da8e206",
  "pan": "4000004244716910",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2029-09",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 500000,
  "monthlyLimit": 500000,
  "availableBalance": 500000,
  "issuerId": "ISS001"
}
```

## Подготовка данных

```sql
UPDATE cards SET status='ACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000004244716910';
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710001\",
  \"pan\": \"4000004244716910\",
  \"processingCode\": \"000000\",
  \"amount\": 90003,
  \"currencyCode\": \"643\",
  \"transmissionDateTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
  \"terminalId\": \"TERM0001\",
  \"terminalType\": \"POS\",
  \"merchantId\": \"MERCH0000000002\",
  \"mcc\": \"5411\",
  \"acquirerId\": \"ACQ002\",
  \"issuerId\": \"ISS001\"
}"
```

Ответ: HTTP `200`.

```json
{
  "mti": "0100",
  "stan": "710001",
  "rrn": "626500001459",
  "authCode": "QG242S",
  "responseCode": "00",
  "status": "APPROVED",
  "declineReason": null
}
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710002\",
  \"pan\": \"4000004244716910\",
  \"processingCode\": \"000000\",
  \"amount\": 90003,
  \"currencyCode\": \"643\",
  \"transmissionDateTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
  \"terminalId\": \"TERM0001\",
  \"terminalType\": \"POS\",
  \"merchantId\": \"MERCH0000000002\",
  \"mcc\": \"5411\",
  \"acquirerId\": \"ACQ002\",
  \"issuerId\": \"ISS001\"
}"
```

Ответ: HTTP `200`.

```json
{
  "mti": "0100",
  "stan": "710002",
  "rrn": "626500001460",
  "authCode": "LK9VZ0",
  "responseCode": "00",
  "status": "APPROVED",
  "declineReason": null
}
```

## Карта после двух операций

```sh
curl -i -X GET http://localhost:8081/api/cards/4000004244716910
```

Ответ: HTTP `200`.

```json
{
  "id": "ddbb6d9b-e3ed-47f7-a960-56b13da8e206",
  "pan": "4000004244716910",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-10",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 500000,
  "monthlyLimit": 500000,
  "availableBalance": 319994,
  "issuerId": "ISS001"
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000004244716910';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000004244716910'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000004244716910';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | ACTIVE | 1026 | 500000 | 500000 | 500000 | 0 | 0 | 0 |
| После | ACTIVE | 1026 | 319994 | 500000 | 500000 | 180006 | 180006 | 2 |

Использование после запроса (`day_offset`: `0` — день прогона, `-1` — предыдущий день):

```json
[
  {
    "day_offset": 0,
    "daily_amount": 180006.0,
    "monthly_amount": 180006.0
  }
]
```

Резервирования после запроса:

```json
[
  {
    "rrn": "626500001459",
    "amount": 90003,
    "status": "RESERVED"
  },
  {
    "rrn": "626500001460",
    "amount": 90003,
    "status": "RESERVED"
  }
]
```

## Проверки

- [x] Оба запроса обработаны в одну секунду UTC.
- [x] Оба запроса одобрены.
- [x] RRN уникальны и содержат 12 цифр.
- [x] authCode соответствует формату.
- [ ] RRN содержит YDDD и HHmmss по ТЗ.
- [x] Состояние после двух одобрений.
