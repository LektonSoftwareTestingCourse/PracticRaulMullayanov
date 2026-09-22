# CMS-07-01

Результат: **Passed**.

## Запрос

```sh
curl -i -X GET 'http://localhost:8081/api/cards?limit=1&offset=-1'
```

Ответ: HTTP `400`.

```json
{
  "error": "ConstraintViolationException",
  "message": "getCards.offset: Value can not be negative",
  "serviceName": "card-management",
  "retryAfterMs": null
}
```

## Проверки

- [x] Проверка offset.
