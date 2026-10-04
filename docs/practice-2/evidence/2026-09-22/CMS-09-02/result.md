# CMS-09-02

Результат: **Passed**.

## Запрос

```sh
curl -i -X GET 'http://localhost:8081/api/cards?bin=999999&limit=10000'
```

Ответ: HTTP `200`.

```json
{
  "total": 0,
  "cards": []
}
```

## Проверки

- [x] Фильтр BIN и total.
