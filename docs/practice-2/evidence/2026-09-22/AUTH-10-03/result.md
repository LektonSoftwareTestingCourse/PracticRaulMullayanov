# AUTH-10-03

Результат: **Passed**.

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
  "initialBalance": 100000
}'
```

Ответ: HTTP `201`.

```json
{
  "id": "4b29b834-0db8-45e1-b359-ec21c7ba8d23",
  "pan": "4000008496578878",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2029-09",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 500000,
  "monthlyLimit": 500000,
  "availableBalance": 100000,
  "issuerId": "ISS001"
}
```

## Подготовка данных

```sql
UPDATE cards SET status='ACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000008496578878';
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710014\",
  \"pan\": \"4000008496578878\",
  \"processingCode\": \"000000\",
  \"amount\": 100001,
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

Ответ: HTTP `422`.

```json
{
  "mti": "0100",
  "stan": "710014",
  "rrn": null,
  "authCode": null,
  "responseCode": "51",
  "status": "DECLINED",
  "declineReason": "INSUFFICIENT_FUNDS"
}
```

## Карта после операции

```sh
curl -i -X GET http://localhost:8081/api/cards/4000008496578878
```

Ответ: HTTP `200`.

```json
{
  "id": "4b29b834-0db8-45e1-b359-ec21c7ba8d23",
  "pan": "4000008496578878",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-10",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 500000,
  "monthlyLimit": 500000,
  "availableBalance": 100000,
  "issuerId": "ISS001"
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000008496578878';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000008496578878'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000008496578878';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | ACTIVE | 1026 | 100000 | 500000 | 500000 | 0 | 0 | 0 |
| После | ACTIVE | 1026 | 100000 | 500000 | 500000 | 0 | 0 | 0 |

## Проверки

- [x] Решение и причина отказа соответствуют ожидаемым.
- [x] Баланс соответствует результату операции.
- [x] Использование лимитов соответствует результату операции.
- [x] Настройки лимитов сохранены.
- [x] Резервирований нет.
