# PW-02 — неактивная карта

Результат: **Passed**.

## Данные

PAN `4000004015615663`; статус `INACTIVE`, срок `0926` (`CURRENT`); A=100000, B=200000, DL=200000, ML=500000, D=0, M=300000; RD=RM=200000; `terminalType=ATM`, `mcc=5812`, `currencyCode=643`, `issuerId=ISS001`.

## Авторизация

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
    \"mti\": \"0100\",
    \"stan\": \"$(date -u +%H%M%S)\",
    \"pan\": \"4000004015615663\",
    \"processingCode\": \"000000\",
    \"amount\": 100000,
    \"currencyCode\": \"643\",
    \"transmissionDateTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"terminalId\": \"TERM0001\",
    \"terminalType\": \"ATM\",
    \"merchantId\": \"MERCH0000000002\",
    \"mcc\": \"5812\",
    \"acquirerId\": \"ACQ002\",
    \"issuerId\": \"ISS001\"
  }"
```

Ответ: HTTP `403`.

```json
{"mti":"0100","stan":"215019","rrn":null,"authCode":null,"responseCode":"05","status":"DECLINED","declineReason":"CARD_INACTIVE"}
```

## Состояние после запроса

```sql
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards
WHERE pan = '4000004015615663';

SELECT pan, usage_date, daily_amount, monthly_amount
FROM limit_usage
WHERE pan = '4000004015615663'
ORDER BY usage_date;

SELECT pan, rrn, reservation_amount, status
FROM reservations
WHERE pan = '4000004015615663';
```

Проверяемые поля карты:

| status | expiry_date | available_balance | daily_limit | monthly_limit |
|---|---|---|---|---|
| INACTIVE | 0926 | 200000 | 200000 | 500000 |

Использование лимитов: одна запись за день перед авторизацией, `daily_amount=300000`, `monthly_amount=300000`. За день авторизации записей нет: D=0, M=300000.

Резервирования: **0 строк**.

## Проверки

- `DECLINED`, код `05`, причина `CARD_INACTIVE`.
- `rrn` и `authCode` равны `null`.
- B=200000, D=0, M=300000 соответствуют исходным значениям сценария.
- DL=200000, ML=500000; резервирований нет.
