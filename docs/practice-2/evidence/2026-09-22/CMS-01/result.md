# CMS-01 — создание карты

Результат: **Passed**.

## Запрос

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

## Ответ

HTTP `201`.

```json
{"id":"8986a612-e34e-4a11-a55d-463d01c87fd9","pan":"4000002848766224","bin":"400000","cardholderName":"IVAN IVANOV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"643","dailyLimit":150000,"monthlyLimit":300000,"availableBalance":100000,"issuerId":"ISS001"}
```

## Проверки

- PAN `4000002848766224`: 16 цифр, начинается с BIN `400000`.
- Алгоритм Луна: сумма `60`, остаток от деления на 10 равен `0`.
- `status=ACTIVE`.
- `expiryDate=2029-09` — месяц создания + 3 года.
- `availableBalance=100000`, `dailyLimit=150000`, `monthlyLimit=300000`.
- Имя держателя и валюта совпадают с запросом.
