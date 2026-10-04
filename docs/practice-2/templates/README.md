# Общие запросы для практики 2

Заменить `PAN_КАРТЫ` и `ISSUER_ID` значениями из ответа создания карты. Суммы — в копейках. Выполнять нужный запрос отдельно.

## Создать карту

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

## Получить карту

```sh
curl -i http://localhost:8081/api/cards/PAN_КАРТЫ
```

## Получить список карт

```sh
curl -i 'http://localhost:8081/api/cards?limit=50&offset=0'
```

Фильтры можно добавлять к адресу: `&status=ACTIVE`, `&bin=400000`. Для проверки значений по умолчанию убрать всё после `/api/cards`.

## Изменить карту

Оставить в JSON только поля, которые нужно изменить.

```sh
curl -i -X PATCH http://localhost:8081/api/cards/PAN_КАРТЫ \
  -H 'Content-Type: application/json' \
  -d '{
    "status": "ACTIVE",
    "dailyLimit": 150000,
    "monthlyLimit": 300000,
    "availableBalance": 100000
  }'
```

## Авторизация

Заменить PAN, issuerId и сумму. STAN и время запроса подставляются командой `date`.

```sh
curl -i -X POST http://localhost:8083/api/internal/authorize \
  -H 'Content-Type: application/json' \
  -d "{
    \"mti\": \"0100\",
    \"stan\": \"$(date -u +%H%M%S)\",
    \"pan\": \"PAN_КАРТЫ\",
    \"processingCode\": \"000000\",
    \"amount\": 90003,
    \"currencyCode\": \"643\",
    \"transmissionDateTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"terminalId\": \"TERM0001\",
    \"terminalType\": \"POS\",
    \"merchantId\": \"MERCH0000000002\",
    \"mcc\": \"5411\",
    \"acquirerId\": \"ACQ002\",
    \"issuerId\": \"ISSUER_ID\"
  }"
```

Для двух запросов в одну секунду указать разные шестизначные `stan` вручную.

## Зарезервировать средства

Для каждой новой операции указать новый RRN из 12 цифр.

```sh
curl -i -X POST http://localhost:8081/api/cards/PAN_КАРТЫ/reserve \
  -H 'Content-Type: application/json' \
  -d '{"amount": 100000, "rrn": "012345678901"}'
```

## Сгенерировать карты

```sh
curl -i -X POST http://localhost:8081/api/cards/generate \
  -H 'Content-Type: application/json' \
  -d '{"count": 100, "bins": ["400000", "400001", "400002", "400003", "400004"]}'
```

## Удалить карту

```sh
curl -i -X DELETE http://localhost:8081/api/cards/PAN_КАРТЫ
```

## SQL

[checks.sql](checks.sql) — просмотр карты, использованных сумм и резервирований; изменение срока действия. Заменить `PAN_КАРТЫ` и выполнить нужный блок в клиенте БД.
