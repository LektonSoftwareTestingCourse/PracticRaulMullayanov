# CMS-14

Результат: **Passed**.

- Распределение по BIN: {'400000': 20, '400001': 20, '400002': 20, '400003': 20, '400004': 20}
- Распределение по статусам: {'ACTIVE': 95, 'INACTIVE': 3, 'BLOCKED': 2}

## Запрос

```sh
curl -i -X POST http://localhost:8081/api/cards/generate \
  -H 'Content-Type: application/json' \
  -d '{
  "count": 100,
  "bins": [
    "400000",
    "400001",
    "400002",
    "400003",
    "400004"
  ]
}'
```

Ответ: HTTP `201`.

Карт в ответе: `100`; `generated=100`. [JSON ответа](response-1.json).

## Проверки

- [x] HTTP 201, generated=100, 100 карт сохранены.
- [x] 95 ACTIVE, 3 INACTIVE, 2 BLOCKED.
