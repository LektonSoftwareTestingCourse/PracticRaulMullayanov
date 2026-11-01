package com.processing.gateway.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Практика 4: unit-тестирование СМП.
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Gateway: circuit breaker прекращает вызовы недоступного downstream-сервиса.
 * Проверяем порог ошибок, пробный запрос, восстановление и изоляцию сервисов; Clock заменён Mockito.
 */
class InMemoryCircuitBreakerTest {
    private static final Instant START = Instant.parse("2026-10-04T12:00:00Z");
    private final Clock clock = mock(Clock.class);
    private InMemoryCircuitBreaker breaker;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(START);
        breaker = InMemoryCircuitBreaker.forTesting(Duration.ofSeconds(10), 2, clock);
    }

    // Gateway: контур открывается ровно при достижении порога, другой сервис остаётся доступен.
    // Требование: tz/02-gateway.md:115 — дополнительные задания, Circuit Breaker.
    // Дополнительная проверка реализации: порог ошибок и независимое состояние downstream-сервисов.
    // Основание: services/gateway/src/main/java/com/processing/gateway/circuitbreaker/InMemoryCircuitBreaker.java:110.
    @ParameterizedTest
    @CsvSource({"0, true", "1, true", "2, false"})
    void shouldOpenCircuitWhenFailureThresholdIsReached(int failures, boolean expected) {
        // Arrange
        recordFailures(failures);

        // Act
        boolean allowed = breaker.allowRequest("switch");

        // Assert
        assertEquals(expected, allowed);
    }

    @Test
    void shouldAllowOtherServiceWhenSwitchCircuitIsOpen() {
        // Arrange
        openCircuit();

        // Act
        boolean allowed = breaker.allowRequest("cms");

        // Assert
        assertTrue(allowed);
    }

    // Gateway: до истечения интервала запрос запрещён, на границе разрешён только один probe.
    // Требование: tz/02-gateway.md:115 — дополнительные задания, Circuit Breaker.
    // Дополнительная проверка реализации: после интервала ожидания допускается только один probe.
    // Основание: services/gateway/src/main/java/com/processing/gateway/circuitbreaker/InMemoryCircuitBreaker.java:65.
    @ParameterizedTest
    @CsvSource({"9999999999, false", "10000000000, true"})
    void shouldAllowProbeWhenOpenTimeoutHasElapsed(long elapsedNanos, boolean expected) {
        // Arrange
        openCircuit();
        when(clock.instant()).thenReturn(START.plusNanos(elapsedNanos));

        // Act
        boolean allowed = breaker.allowRequest("switch");

        // Assert
        assertEquals(expected, allowed);
    }

    @Test
    void shouldRejectProbeWhenAnotherProbeIsInFlight() {
        // Arrange
        prepareProbe();

        // Act
        boolean allowed = breaker.allowRequest("switch");

        // Assert
        assertFalse(allowed);
    }

    // Gateway: успешный probe закрывает контур и сбрасывает накопленные ошибки.
    // Требование: tz/02-gateway.md:115 — дополнительные задания, Circuit Breaker.
    // Дополнительная проверка реализации: успех закрывает контур и сбрасывает счётчик ошибок.
    // Основание: services/gateway/src/main/java/com/processing/gateway/circuitbreaker/InMemoryCircuitBreaker.java:93.
    @Test
    void shouldResetFailuresWhenProbeSucceeds() {
        // Arrange
        prepareProbe();
        breaker.recordSuccess("switch");
        breaker.recordFailure("switch");

        // Act
        boolean allowed = breaker.allowRequest("switch");

        // Assert
        assertTrue(allowed);
    }

    // Gateway: неудачный probe начинает новый полный интервал ожидания.
    // Требование: tz/02-gateway.md:115 — дополнительные задания, Circuit Breaker.
    // Дополнительная проверка реализации: неудачный probe повторно открывает контур.
    // Основание: services/gateway/src/main/java/com/processing/gateway/circuitbreaker/InMemoryCircuitBreaker.java:110.
    @ParameterizedTest
    @CsvSource({"9, false", "10, true"})
    void shouldRestartTimeoutWhenProbeFails(long secondsSinceFailure, boolean expected) {
        // Arrange
        prepareProbe();
        breaker.recordFailure("switch");
        when(clock.instant()).thenReturn(START.plusSeconds(10 + secondsSinceFailure));

        // Act
        boolean allowed = breaker.allowRequest("switch");

        // Assert
        assertEquals(expected, allowed);
    }

    // Gateway: отмена probe освобождает слот и не меняет состояние на успешное.
    // Требование: tz/02-gateway.md:115 — дополнительные задания, Circuit Breaker.
    // Дополнительная проверка реализации: отмена probe освобождает слот без изменения счётчиков.
    // Основание: services/gateway/src/main/java/com/processing/gateway/circuitbreaker/InMemoryCircuitBreaker.java:153.
    @Test
    void shouldReleaseProbeSlotWhenProbeIsCancelled() {
        // Arrange
        prepareProbe();
        breaker.releaseRequest("switch");

        // Act
        boolean allowed = breaker.allowRequest("switch");

        // Assert
        assertTrue(allowed);
    }

    private void recordFailures(int failures) {
        for (int index = 0; index < failures; index++) {
            breaker.recordFailure("switch");
        }
    }

    private void prepareProbe() {
        openCircuit();
        when(clock.instant()).thenReturn(START.plusSeconds(10));
        breaker.allowRequest("switch");
    }

    private void openCircuit() {
        breaker.recordFailure("switch");
        breaker.recordFailure("switch");
    }
}
