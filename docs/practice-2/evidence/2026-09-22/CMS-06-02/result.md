# CMS-06-02

Результат: **Passed**.

## Запрос

```sh
curl -i -X GET 'http://localhost:8081/api/cards?limit=1'
```

Ответ: HTTP `200`.

```json
{
  "total": 633,
  "cards": [
    {
      "id": "9cd7ff78-504c-49fd-a4c0-bf3c64eaf963",
      "pan": "4000019534601507",
      "bin": "400001",
      "cardholderName": "ISABEL REINGER",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 23209200,
      "monthlyLimit": 696276000,
      "availableBalance": 43464165,
      "issuerId": "ISS002"
    }
  ]
}
```

## Проверки

- [x] Проверка границы limit.
