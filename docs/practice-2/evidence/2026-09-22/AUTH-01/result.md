# AUTH-01 — успешная авторизация

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
{"id":"8b7d332e-a05c-4f7d-a919-cfaf4febc9ce","pan":"4000004489453039","bin":"400000","cardholderName":"IVAN IVANOV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"643","dailyLimit":150000,"monthlyLimit":300000,"availableBalance":100000,"issuerId":"ISS001"}
```

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
    \"mti\": \"0100\",
    \"stan\": \"$(date -u +%H%M%S)\",
    \"pan\": \"4000004489453039\",
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
{"mti":"0100","stan":"205323","rrn":"626500001447","authCode":"C8S9XX","responseCode":"00","status":"APPROVED","declineReason":null}
```

## Карта после операции

```sh
curl -i http://localhost:8081/api/cards/4000004489453039
```

Ответ: HTTP `200`.

```json
{"id":"8b7d332e-a05c-4f7d-a919-cfaf4febc9ce","pan":"4000004489453039","bin":"400000","cardholderName":"IVAN IVANOV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"643","dailyLimit":150000,"monthlyLimit":300000,"availableBalance":9997,"issuerId":"ISS001"}
```

## Использованные суммы

```sql
SELECT pan, usage_date, daily_amount, monthly_amount
FROM limit_usage
WHERE pan = '4000004489453039'
ORDER BY usage_date;
```

Проверяемые поля результата SELECT:

| pan | daily_amount | monthly_amount |
|---|---:|---:|
| 4000004489453039 | 90003.00 | 90003.00 |

Для теста создал новую карту. Снимок счётчиков до операции не сохранил.

## Проверки

- `status=APPROVED`, `responseCode=00`, причина отказа отсутствует.
- RRN `626500001447` содержит 12 цифр; authCode `C8S9XX` — 6 символов `A-Z0-9`.
- Баланс: `100000 - 90003 = 9997` коп.
- Использованные суммы за день и месяц: `90003` коп.
- Лимиты не изменились: `dailyLimit=150000`, `monthlyLimit=300000`.
