package com.processing.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Практика 4: unit-тестирование СМП.
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Gateway: ограничение частоты защищает сервис от превышения нагрузки.
 * Проверяем границу bucket, восстановление и изоляцию клиентов с управляемым временем без sleep.
 */
class InMemoryRateLimiterTest {
    private final AtomicLong nanos = new AtomicLong();
    private InMemoryRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = InMemoryRateLimiter.forTesting(2, 1, Duration.ofMinutes(10), 100, nanos::get);
    }

    // Gateway: первые capacity запросов разрешены, следующий запрос отклоняется.
    // Требование: tz/02-gateway.md:80 — раздел 4, ограничение частоты транзакций.
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 100})
    void shouldRejectRequestWhenCapacityIsExhausted(int capacity) {
        // Arrange
        limiter = InMemoryRateLimiter.forTesting(capacity, 100, Duration.ofMinutes(10), 100, nanos::get);
        consumeTokens(capacity);

        // Act
        boolean allowed = limiter.allowRequest("client-a");

        // Assert
        assertFalse(allowed);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 100})
    void shouldAllowRequestWhenLastTokenIsAvailable(int capacity) {
        // Arrange
        limiter = InMemoryRateLimiter.forTesting(capacity, 100, Duration.ofMinutes(10), 100, nanos::get);
        consumeTokens(capacity - 1);

        // Act
        boolean allowed = limiter.allowRequest("client-a");

        // Assert
        assertTrue(allowed);
    }

    // Gateway: дробный токен недостаточен; ровно через секунду появляется один новый запрос.
    // Требование: tz/02-gateway.md:80 — раздел 4, ограничение частоты транзакций.
    // Дополнительная проверка реализации: восстановление квоты по времени.
    // Основание: services/gateway/src/main/java/com/processing/gateway/ratelimit/InMemoryRateLimiter.java:84.
    @ParameterizedTest
    @CsvSource({"999999999, false", "1000000000, true"})
    void shouldRefillTokenWhenEnoughTimeHasElapsed(long elapsedNanos, boolean expected) {
        // Arrange
        consumeTokens(2);
        nanos.set(elapsedNanos);

        // Act
        boolean allowed = limiter.allowRequest("client-a");

        // Assert
        assertEquals(expected, allowed);
    }

    @Test
    void shouldRejectRequestWhenRefilledTokenHasBeenConsumed() {
        // Arrange
        consumeTokens(2);
        nanos.set(1_000_000_000L);
        consumeTokens(1);

        // Act
        boolean allowed = limiter.allowRequest("client-a");

        // Assert
        assertFalse(allowed);
    }

    // Gateway: долгое ожидание не позволяет накопить больше capacity токенов.
    // Требование: tz/02-gateway.md:80 — раздел 4, ограничение частоты транзакций.
    // Дополнительная проверка реализации: ёмкость token bucket не превышается при восстановлении.
    // Основание: services/gateway/src/main/java/com/processing/gateway/ratelimit/InMemoryRateLimiter.java:127.
    @Test
    void shouldCapTokensWhenBucketIsRefilled() {
        // Arrange
        consumeTokens(2);
        nanos.set(Duration.ofMinutes(1).toNanos());
        consumeTokens(2);

        // Act
        boolean allowed = limiter.allowRequest("client-a");

        // Assert
        assertFalse(allowed);
    }

    // Gateway: один клиент не должен расходовать квоту другого клиента.
    // Требование: tz/02-gateway.md:80 — раздел 4, ограничение частоты транзакций.
    // Дополнительная проверка реализации: отдельная квота для каждого ключа клиента.
    // Основание: services/gateway/src/main/java/com/processing/gateway/ratelimit/InMemoryRateLimiter.java:84.
    @Test
    void shouldAllowOtherClientWhenFirstClientExhaustsQuota() {
        // Arrange
        consumeTokens(2);

        // Act
        boolean allowed = limiter.allowRequest("client-b");

        // Assert
        assertTrue(allowed);
    }

    // Gateway: при нулевой скорости квота не восстанавливается до удаления старого bucket.
    // Требование: tz/02-gateway.md:80 — раздел 4, ограничение частоты транзакций.
    // Дополнительная проверка реализации: удаление bucket после истечения expireAfterAccess.
    // Основание: services/gateway/src/main/java/com/processing/gateway/ratelimit/InMemoryRateLimiter.java:72.
    @Test
    void shouldRestoreQuotaWhenInactiveBucketExpires() {
        // Arrange
        limiter = InMemoryRateLimiter.forTesting(1, 0, Duration.ofSeconds(10), 100, nanos::get);
        consumeTokens(1);
        nanos.set(Duration.ofSeconds(10).toNanos());
        limiter.cleanUp();

        // Act
        boolean allowed = limiter.allowRequest("client-a");

        // Assert
        assertTrue(allowed);
    }

    private void consumeTokens(int count) {
        for (int index = 0; index < count; index++) {
            limiter.allowRequest("client-a");
        }
    }
}
