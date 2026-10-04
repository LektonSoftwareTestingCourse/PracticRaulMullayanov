# CMS-02 — получение карты

Результат: **Passed**.

## Запрос

```sh
curl -i http://localhost:8081/api/cards/4000002848766224
```

## Ответ

HTTP `200`.

```json
{"id":"8986a612-e34e-4a11-a55d-463d01c87fd9","pan":"4000002848766224","bin":"400000","cardholderName":"IVAN IVANOV","expiryDate":"2029-09","status":"ACTIVE","currencyCode":"643","dailyLimit":150000,"monthlyLimit":300000,"availableBalance":100000,"issuerId":"ISS001"}
```

## Проверки

- HTTP `200`.
- ID, PAN, BIN, имя держателя, срок, статус, валюта и issuerId совпадают с ответом создания в [CMS-01](../CMS-01/result.md).
- Баланс `100000`, дневной лимит `150000`, месячный лимит `300000` — без изменений.
