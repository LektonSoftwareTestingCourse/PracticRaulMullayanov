# CMS-09-01

Результат: **Passed**.

## Запрос

```sh
curl -i -X GET 'http://localhost:8081/api/cards?bin=400000&limit=10000'
```

Ответ: HTTP `200`.

Карт в ответе: `157`; `total=157`. [JSON ответа](response-1.json).

## Проверки

- [x] Фильтр BIN и total.
