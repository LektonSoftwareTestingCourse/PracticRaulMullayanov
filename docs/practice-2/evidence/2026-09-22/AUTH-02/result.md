# AUTH-02 — карта не найдена

Результат: **Passed**.

## Проверка отсутствия карты

```sh
curl -i http://localhost:8081/api/cards/4000000000000002
```

Ответ: HTTP `404`.

```json
{"error":"CardNotFoundException","message":"Card with PAN 4000********0002 was not found","serviceName":"card-management"}
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
    \"mti\": \"0100\",
    \"stan\": \"$(date -u +%H%M%S)\",
    \"pan\": \"4000000000000002\",
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
{"mti":"0100","stan":"211021","rrn":null,"authCode":null,"responseCode":"14","status":"DECLINED","declineReason":"CARD_NOT_FOUND"}
```

## Использованные суммы

```sql
SELECT pan, daily_amount, monthly_amount
FROM limit_usage
WHERE pan = '4000000000000002';
```

Результат: **0 строк**.

## Проверка резервирования

Проверил сообщения в логах за интервал запроса. Сообщение Authorization:

```text
AUTHORIZATION DECLINED WITH CARD NOT FOUND for pan 4000********0002
```

CMS записал сообщение об отсутствии карты при предварительном GET и при запросе из Authorization:

```text
Card with PAN 4000********0002 was not found
```

В [AuthServiceImpl.authorize](../../../../../services/authorization/src/main/java/com/processing/authorization/services/AuthServiceImpl.java) обработчик `CardNotFoundException` при чтении карты возвращает отказ до вызова reserve. Отсутствие вызова reserve оценил по этой ветви кода и сообщению об отказе; отдельную трассировку HTTP-вызовов не собирал.

## Проверки

- Карта не найдена: GET вернул `404`.
- Авторизация отклонена: `status=DECLINED`, `responseCode=14`, `declineReason=CARD_NOT_FOUND`.
- `rrn` и `authCode` равны `null`.
- Записи использования лимитов для PAN отсутствуют.
- Отказ соответствует ветви, завершающей обработку до резервирования.
