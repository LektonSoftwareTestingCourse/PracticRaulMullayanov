# CMS-04-03

Результат: **Passed**.

## Запрос

```sh
curl -i -X GET http://localhost:8081/api/cards/400000000000000A
```

Ответ: HTTP `400`.

```json
{
  "error": "ConstraintViolationException",
  "message": "getCard.pan: PAN number must contain exactly 16 digits",
  "serviceName": "card-management",
  "retryAfterMs": null
}
```

## Проверки

- [x] HTTP 400.
