# CMS-12

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
  "id": "d175dd02-4cf9-474b-8bc9-a333a2f6fa8f",
  "pan": "4000008421448023",
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
UPDATE cards SET status='ACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000008421448023';
```

## Запрос

```sh
curl -i -X DELETE http://localhost:8081/api/cards/4000008421448023
```

Ответ: HTTP `204`.

## Запрос

```sh
curl -i -X GET http://localhost:8081/api/cards/4000008421448023
```

Ответ: HTTP `404`.

```json
{
  "error": "CardNotFoundException",
  "message": "Card with PAN 4000********8023 was not found",
  "serviceName": "card-management",
  "retryAfterMs": null
}
```

## Запрос

```sh
curl -i -X GET 'http://localhost:8081/api/cards?limit=10000'
```

Ответ: HTTP `200`.

Карт в ответе: `638`; `total=638`. [JSON ответа](response-4.json).

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710001\",
  \"pan\": \"4000008421448023\",
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

Ответ: HTTP `404`.

```json
{
  "mti": "0100",
  "stan": "710001",
  "rrn": null,
  "authCode": null,
  "responseCode": "14",
  "status": "DECLINED",
  "declineReason": "CARD_NOT_FOUND"
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000008421448023';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000008421448023'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000008421448023';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | ACTIVE | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |
| После | DELETED | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |

## Проверки

- [x] DELETE возвращает 204.
- [x] GET возвращает 404.
- [x] Карта исключена из полного списка.
- [x] Мягкое удаление, баланс не изменился.
- [x] Авторизация отклонена как CARD_NOT_FOUND / 14.
- [x] Использования и резервирований нет.
