# AUTH-11

Результат: **Failed**.

Дефекты: [BUG-P2-002](../../../test-design.md#bug-p2-002).

## Создание карты

```sh
curl -i -X POST http://localhost:8081/api/cards \
  -H 'Content-Type: application/json' \
  -d '{
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "currencyCode": "643",
  "dailyLimit": 100,
  "monthlyLimit": 100,
  "initialBalance": 50
}'
```

Ответ: HTTP `201`.

```json
{
  "id": "a6d147c2-d904-46ed-a660-2725046a1a07",
  "pan": "4000009970922624",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2029-09",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 100,
  "monthlyLimit": 100,
  "availableBalance": 50,
  "issuerId": "ISS001"
}
```

## Подготовка данных

```sql
UPDATE cards SET status='ACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000009970922624';
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710015\",
  \"pan\": \"4000009970922624\",
  \"processingCode\": \"000000\",
  \"amount\": 200,
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
  "stan": "710015",
  "rrn": null,
  "authCode": null,
  "responseCode": "51",
  "status": "DECLINED",
  "declineReason": "INSUFFICIENT_FUNDS"
}
```

## Карта после операции

```sh
curl -i -X GET http://localhost:8081/api/cards/4000009970922624
```

Ответ: HTTP `200`.

```json
{
  "id": "a6d147c2-d904-46ed-a660-2725046a1a07",
  "pan": "4000009970922624",
  "bin": "400000",
  "cardholderName": "IVAN IVANOV",
  "expiryDate": "2026-10",
  "status": "ACTIVE",
  "currencyCode": "643",
  "dailyLimit": 100,
  "monthlyLimit": 100,
  "availableBalance": 50,
  "issuerId": "ISS001"
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000009970922624';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000009970922624'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000009970922624';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | ACTIVE | 1026 | 50 | 100 | 100 | 0 | 0 | 0 |
| После | ACTIVE | 1026 | 50 | 100 | 100 | 0 | 0 | 0 |

## Проверки

- [ ] Решение и причина отказа соответствуют ожидаемым.
- [x] Баланс соответствует результату операции.
- [x] Использование лимитов соответствует результату операции.
- [x] Настройки лимитов сохранены.
- [x] Резервирований нет.
