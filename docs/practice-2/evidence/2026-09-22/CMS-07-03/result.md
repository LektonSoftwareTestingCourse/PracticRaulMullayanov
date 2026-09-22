# CMS-07-03

Результат: **Passed**.

## Контрольная страница

```sh
curl -i -X GET 'http://localhost:8081/api/cards?limit=2&offset=0'
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
    },
    {
      "id": "65a48add-db72-4525-916d-f10540afa57a",
      "pan": "4000041852722509",
      "bin": "400004",
      "cardholderName": "MRS. TANNER KING",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 10551456,
      "monthlyLimit": 316543680,
      "availableBalance": 7324233,
      "issuerId": "ISS005"
    }
  ]
}
```

## Запрос

```sh
curl -i -X GET 'http://localhost:8081/api/cards?limit=1&offset=1'
```

Ответ: HTTP `200`.

```json
{
  "total": 633,
  "cards": [
    {
      "id": "65a48add-db72-4525-916d-f10540afa57a",
      "pan": "4000041852722509",
      "bin": "400004",
      "cardholderName": "MRS. TANNER KING",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 10551456,
      "monthlyLimit": 316543680,
      "availableBalance": 7324233,
      "issuerId": "ISS005"
    }
  ]
}
```

## Проверка неизменности выборки

```sh
curl -i -X GET 'http://localhost:8081/api/cards?limit=2&offset=0'
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
    },
    {
      "id": "65a48add-db72-4525-916d-f10540afa57a",
      "pan": "4000041852722509",
      "bin": "400004",
      "cardholderName": "MRS. TANNER KING",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 10551456,
      "monthlyLimit": 316543680,
      "availableBalance": 7324233,
      "issuerId": "ISS005"
    }
  ]
}
```

## Проверки

- [x] Проверка offset.
- [x] Контрольная выборка не изменилась.
