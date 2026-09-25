# PW-01 — просроченная карта

Результат: **Passed**.

## Данные

PAN `4000008625809459`; статус `EXPIRED`, срок `0826` (`PAST`); A=90003, B=100000, DL=100000, ML=400000, D=0, M=300000; RD=RM=100000; `terminalType=ECOM`, `mcc=5411`, `currencyCode=643`, `issuerId=ISS001`.

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
    \"mti\": \"0100\",
    \"stan\": \"$(date -u +%H%M%S)\",
    \"pan\": \"4000008625809459\",
    \"processingCode\": \"000000\",
    \"amount\": 90003,
    \"currencyCode\": \"643\",
    \"transmissionDateTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"terminalId\": \"TERM0001\",
    \"terminalType\": \"ECOM\",
    \"merchantId\": \"MERCH0000000002\",
    \"mcc\": \"5411\",
    \"acquirerId\": \"ACQ002\",
    \"issuerId\": \"ISS001\"
  }"
```

Ответ: HTTP `403`.

```json
{"mti":"0100","stan":"214557","rrn":null,"authCode":null,"responseCode":"54","status":"DECLINED","declineReason":"CARD_EXPIRED"}
```

## Состояние после запроса

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards
WHERE pan = '4000008625809459';

SELECT pan, usage_date, daily_amount, monthly_amount
FROM limit_usage
WHERE pan = '4000008625809459'
ORDER BY usage_date;

SELECT pan, rrn, reservation_amount, status
FROM reservations
WHERE pan = '4000008625809459';
```

Проверяемые поля карты:

| status | expiry_date | available_balance | daily_limit | monthly_limit |
|---|---|---|---|---|
| EXPIRED | 0826 | 100000 | 100000 | 400000 |

Использование лимитов: одна запись за день перед авторизацией, `daily_amount=300000`, `monthly_amount=300000`. За день авторизации записей нет: D=0, M=300000.

Резервирования: **0 строк**.

## Проверки

- `DECLINED`, код `54`, причина `CARD_EXPIRED`.
- `rrn` и `authCode` равны `null`.
- B=100000, D=0, M=300000 соответствуют исходным значениям сценария.
- DL=100000, ML=400000; резервирований нет.
