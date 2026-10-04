# CMS-03

Результат: **Passed**.

## Запрос

```sh
curl -i -X GET http://localhost:8081/api/cards/4000000000000002
```

Ответ: HTTP `404`.

```json
{
  "error": "CardNotFoundException",
  "message": "Card with PAN 4000********0002 was not found",
  "serviceName": "card-management",
  "retryAfterMs": null
}
```

## Проверки

- [x] HTTP 404.
