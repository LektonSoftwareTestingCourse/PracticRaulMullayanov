# AUTH-12

Результат: **Failed**.

Дефекты: [BUG-P2-004](../../../test-design.md#bug-p2-004).

- Отдельный контейнер Authorization из того же образа, что основной сервис; порт 18083.
- CARD_MGMT_URL=http://smp-p2-fault-proxy:8080; остальные настройки сохранены.
- Прокси возвращает HTTP 503 при чтении этой карты; остальные запросы передаются в CMS.

Образ Authorization: `sha256:59fb2436ccad5c21e4729284900bf0fa191ec443ca5481b251be2c976264c614`.

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
  "id": "30d2df4b-cadf-4630-a661-8d8f2f58dca0",
  "pan": "4000007778776846",
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
UPDATE cards SET status='ACTIVE', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000007778776846';
```

## Авторизация

```sh
curl -i -X POST http://localhost:18083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
  \"mti\": \"0100\",
  \"stan\": \"710001\",
  \"pan\": \"4000007778776846\",
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

Ответ: HTTP `503`.

```json
{
  "mti": "0100",
  "stan": "710001",
  "rrn": null,
  "authCode": null,
  "responseCode": "96",
  "status": "DECLINED",
  "declineReason": "SERVICE_UNAVAILABLE"
}
```

## Карта после операции

```sh
curl -i -X GET http://localhost:8081/api/cards/4000007778776846
```

Ответ: HTTP `200`.

```json
{
  "id": "30d2df4b-cadf-4630-a661-8d8f2f58dca0",
  "pan": "4000007778776846",
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
FROM cards WHERE pan = '4000007778776846';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000007778776846'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000007778776846';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | ACTIVE | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |
| После | ACTIVE | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |

## Настройка отказа

```nginx
server { listen 8080;
location = /api/cards/4000007778776846 { default_type application/json; return 503 '{"error":"ServiceUnavailable"}'; }
location = /api/cards/4000007633220675/reserve { default_type application/json; return 503 '{"error":"ServiceUnavailable"}'; }
location / { proxy_pass http://card-management:8080; }
}
```

## Ответы прокси

```text
GET /api/cards/4000007778776846 → 503
```

Временные контейнеры удалены; основные CMS и Authorization доступны.

## Проверки

- [ ] Решение и причина отказа соответствуют ожидаемым.
- [x] Баланс соответствует результату операции.
- [x] Использование лимитов соответствует результату операции.
- [x] Настройки лимитов сохранены.
- [x] Резервирований нет.
