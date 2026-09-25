# CMS-08-03

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
{
  "id": "22a98960-a6a5-429d-ab0d-3b476cfe8a5a",
  "pan": "4000009767388229",
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
UPDATE cards SET status='BLOCKED', expiry_date=to_char((CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '1 month', 'MMYY') WHERE pan='4000009767388229';
```

## Запрос

```sh
curl -i -X GET 'http://localhost:8081/api/cards?status=BLOCKED&limit=10000'
```

Ответ: HTTP `200`.

```json
{
  "total": 18,
  "cards": [
    {
      "id": "dab0bcc8-b0b2-4f80-b2d4-73ff404cb442",
      "pan": "4000033119271800",
      "bin": "400003",
      "cardholderName": "LAKESHIA MITCHELL JR.",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 8012179,
      "monthlyLimit": 240365370,
      "availableBalance": 49044097,
      "issuerId": "ISS004"
    },
    {
      "id": "6b0efb95-c056-483a-8498-7585b88e0aac",
      "pan": "4000021784444224",
      "bin": "400002",
      "cardholderName": "DANTE FERRY",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 24333421,
      "monthlyLimit": 730002630,
      "availableBalance": 23089227,
      "issuerId": "ISS003"
    },
    {
      "id": "5c6ac159-f084-43c4-990e-f98ffa92bb03",
      "pan": "4000027142777114",
      "bin": "400002",
      "cardholderName": "MISS XIOMARA GREEN",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 14025232,
      "monthlyLimit": 420756960,
      "availableBalance": 38844811,
      "issuerId": "ISS003"
    },
    {
      "id": "29e0a462-b4a9-496c-adc3-38b5b780f080",
      "pan": "4000023347429593",
      "bin": "400002",
      "cardholderName": "FLORANCE MCDERMOTT",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 6027709,
      "monthlyLimit": 180831270,
      "availableBalance": 47572159,
      "issuerId": "ISS003"
    },
    {
      "id": "8d9d0038-a0bc-4523-90d9-3488194c11d7",
      "pan": "4000035430840478",
      "bin": "400003",
      "cardholderName": "MR. GRADY KIRLIN",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 22747744,
      "monthlyLimit": 682432320,
      "availableBalance": 18225539,
      "issuerId": "ISS004"
    },
    {
      "id": "33f0e289-3473-4d44-8819-252aebfd897c",
      "pan": "4000036921677973",
      "bin": "400003",
      "cardholderName": "JORGE FERRY JR.",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 11570057,
      "monthlyLimit": 347101710,
      "availableBalance": 39540203,
      "issuerId": "ISS004"
    },
    {
      "id": "2ee22ffd-ea41-4f69-8724-12b825858aaf",
      "pan": "4000048474933810",
      "bin": "400004",
      "cardholderName": "SHAMIKA GREEN DVM",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 9031400,
      "monthlyLimit": 270942000,
      "availableBalance": 43156547,
      "issuerId": "ISS005"
    },
    {
      "id": "4f42c9da-9035-48bd-a57f-a93f74379184",
      "pan": "4000017471557492",
      "bin": "400001",
      "cardholderName": "ANDRES LEFFLER IV",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 22521337,
      "monthlyLimit": 675640110,
      "availableBalance": 10724380,
      "issuerId": "ISS002"
    },
    {
      "id": "fb536a1b-f007-4c5a-9877-8ad69c7e57ed",
      "pan": "4000033356721533",
      "bin": "400003",
      "cardholderName": "BOYD THIEL JR.",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 29380712,
      "monthlyLimit": 881421360,
      "availableBalance": 3185119,
      "issuerId": "ISS004"
    },
    {
      "id": "6e849ca7-c08a-43b3-840a-08516b46e4d6",
      "pan": "4000011423787158",
      "bin": "400001",
      "cardholderName": "JARED GOYETTE",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 8530448,
      "monthlyLimit": 255913440,
      "availableBalance": 1841610,
      "issuerId": "ISS002"
    },
    {
      "id": "491afc20-a457-4503-9c6b-174465fd9f53",
      "pan": "4000044724161286",
      "bin": "400004",
      "cardholderName": "DARIN BALISTRERI",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 9187551,
      "monthlyLimit": 275626530,
      "availableBalance": 3800206,
      "issuerId": "ISS005"
    },
    {
      "id": "d0985b97-61df-4bf7-a829-b4cf281e25c4",
      "pan": "4000005112025763",
      "bin": "400000",
      "cardholderName": "MS. AUGUSTINE ARMSTRONG",
      "expiryDate": "2029-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 15674621,
      "monthlyLimit": 470238630,
      "availableBalance": 49468566,
      "issuerId": "ISS001"
    },
    {
      "id": "cb4dbbc8-680d-4f75-a912-4d1d87aba9a2",
      "pan": "4000001912571114",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-10",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 150000,
      "monthlyLimit": 300000,
      "availableBalance": 100000,
      "issuerId": "ISS001"
    },
    {
      "id": "bbcb7be0-3384-42f4-ab19-fc6efb949ba8",
      "pan": "4000008586569183",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 200000,
      "monthlyLimit": 500000,
      "availableBalance": 100000,
      "issuerId": "ISS001"
    },
    {
      "id": "1e8255bb-cce5-400d-8dbe-ae7d0ab35cc6",
      "pan": "4000003521492567",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-10",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 100000,
      "monthlyLimit": 400000,
      "availableBalance": 200000,
      "issuerId": "ISS001"
    },
    {
      "id": "4cfd75c2-edbc-4dd8-8cd0-2a227251a34b",
      "pan": "4000003906069154",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-08",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 200000,
      "monthlyLimit": 500000,
      "availableBalance": 200000,
      "issuerId": "ISS001"
    },
    {
      "id": "f8e09379-1022-4aca-9f71-be105ea30d7b",
      "pan": "4000001557058500",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-09",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 100000,
      "monthlyLimit": 400000,
      "availableBalance": 100000,
      "issuerId": "ISS001"
    },
    {
      "id": "22a98960-a6a5-429d-ab0d-3b476cfe8a5a",
      "pan": "4000009767388229",
      "bin": "400000",
      "cardholderName": "IVAN IVANOV",
      "expiryDate": "2026-10",
      "status": "BLOCKED",
      "currencyCode": "643",
      "dailyLimit": 150000,
      "monthlyLimit": 300000,
      "availableBalance": 100000,
      "issuerId": "ISS001"
    }
  ]
}
```

## Проверка БД

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards WHERE pan = '4000009767388229';

SELECT usage_date - (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')::date AS day_offset,
       daily_amount, monthly_amount
FROM limit_usage WHERE pan = '4000009767388229'
ORDER BY usage_date;

SELECT rrn, reservation_amount, status
FROM reservations WHERE pan = '4000009767388229';
```

| Состояние | Статус | Срок MMYY | B | DL | ML | D | M | Резервирований |
|---|---|---|---:|---:|---:|---:|---:|---:|
| До | BLOCKED | 1026 | 100000 | 150000 | 300000 | 0 | 0 | 0 |

## Проверки

- [x] Все карты имеют запрошенный статус.
- [x] Полнота выборки.
