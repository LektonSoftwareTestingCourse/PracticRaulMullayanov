# CMS-05

Результат: **Failed**.

Дефекты: [BUG-P2-001](../../../test-design.md#bug-p2-001).

- В БД 633 неудалённых карт.

## Запрос

```sh
curl -i -X GET http://localhost:8081/api/cards
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
    },
    {
      "id": "6a352f89-5e30-4c83-bfe8-36dda42761f0",
      "pan": "4000006901750124",
      "bin": "400000",
      "cardholderName": "KARMA BEDNAR",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 12856672,
      "monthlyLimit": 385700160,
      "availableBalance": 15254429,
      "issuerId": "ISS001"
    },
    {
      "id": "29c82757-a04a-4dca-8c73-d116931971e6",
      "pan": "4000021427605603",
      "bin": "400002",
      "cardholderName": "DAMON KEMMER",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 20393448,
      "monthlyLimit": 611803440,
      "availableBalance": 15740721,
      "issuerId": "ISS003"
    },
    {
      "id": "f5842fc2-7c9b-495a-9b73-97e544465eac",
      "pan": "4000034777366718",
      "bin": "400003",
      "cardholderName": "DR. FLETCHER KEMMER",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 25271747,
      "monthlyLimit": 758152410,
      "availableBalance": 22177432,
      "issuerId": "ISS004"
    },
    {
      "id": "c99d709f-4842-4e70-b78a-0a7c467e6a92",
      "pan": "4000044418120846",
      "bin": "400004",
      "cardholderName": "GAIL HAYES",
      "expiryDate": "2029-09",
      "status": "INACTIVE",
      "currencyCode": "643",
      "dailyLimit": 26471173,
      "monthlyLimit": 794135190,
      "availableBalance": 17735578,
      "issuerId": "ISS005"
    },
    {
      "id": "c15773b2-26bd-430f-a728-ee60f1563f35",
      "pan": "4000006617142087",
      "bin": "400000",
      "cardholderName": "DEVORAH HOMENICK III",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 11759688,
      "monthlyLimit": 352790640,
      "availableBalance": 11760268,
      "issuerId": "ISS001"
    },
    {
      "id": "9b84971e-797b-46ae-93f6-ec74994253fe",
      "pan": "4000036551642099",
      "bin": "400003",
      "cardholderName": "CAPRICE KOCH",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 5929037,
      "monthlyLimit": 177871110,
      "availableBalance": 4603825,
      "issuerId": "ISS004"
    },
    {
      "id": "138a3b08-ad19-487b-89d0-025f5e8c6139",
      "pan": "4000006202773387",
      "bin": "400000",
      "cardholderName": "MR. MALCOLM HARBER",
      "expiryDate": "2029-09",
      "status": "ACTIVE",
      "currencyCode": "643",
      "dailyLimit": 28288038,
      "monthlyLimit": 848641140,
      "availableBalance": 26996827,
      "issuerId": "ISS001"
    },
    {
      "id": "263a97ac-8629-4429-a4b4-11bfa3d115c2",
      "pan": "4000031921079791",
      "bin": "400003",
      "cardholderName": "ARACELIS CARTWRIGHT I",
      "expiryDate": "2029-09",
      "status": "INACTIVE",
      "currencyCode": "643",
      "dailyLimit": 13608046,
      "monthlyLimit": 408241380,
      "availableBalance": 30634131,
      "issuerId": "ISS004"
    }
  ]
}
```

## Проверки

- [ ] Размер страницы по умолчанию — 50 по ТЗ.
- [x] total соответствует БД.
