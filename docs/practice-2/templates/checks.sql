-- Заменить PAN_КАРТЫ. Выполнять нужный запрос отдельно.

-- Карта: статус, срок, баланс и лимиты.
SELECT pan, status, expiry_date, available_balance, daily_limit, monthly_limit
FROM cards
WHERE pan = 'PAN_КАРТЫ';

-- Использованные суммы за день и месяц.
SELECT pan, usage_date, daily_amount, monthly_amount
FROM limit_usage
WHERE pan = 'PAN_КАРТЫ'
ORDER BY usage_date;

-- Резервирования по карте.
SELECT pan, rrn, reservation_amount, status
FROM reservations
WHERE pan = 'PAN_КАРТЫ';

-- Доступные BIN.
SELECT bin, issuer_id FROM bin_issuers;

-- Количество неудалённых карт.
SELECT COUNT(*) FROM cards WHERE status <> 'DELETED';

-- Изменить срок только тестовой карты. В БД формат MMYY.
-- Предыдущий месяц: '-1 month', текущий: '0 months', следующий: '1 month'.
UPDATE cards
SET expiry_date = to_char(
    (CURRENT_TIMESTAMP AT TIME ZONE 'UTC') + interval '-1 month', 'MMYY'
)
WHERE pan = 'PAN_КАРТЫ';
