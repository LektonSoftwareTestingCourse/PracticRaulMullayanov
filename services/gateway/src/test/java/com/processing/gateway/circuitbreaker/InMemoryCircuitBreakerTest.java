package com.processing.gateway.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
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
    @Test
    void opensAtFailureThresholdOnlyForFailedService() {
        assertTrue(breaker.allowRequest("switch"));
        breaker.recordFailure("switch");
        assertTrue(breaker.allowRequest("switch"));
        breaker.recordFailure("switch");
        assertFalse(breaker.allowRequest("switch"));
        assertTrue(breaker.allowRequest("cms"));
    }

    // Gateway: до истечения интервала запрос запрещён, на границе разрешён только один probe.
    // Требование: tz/02-gateway.md:115 — дополнительные задания, Circuit Breaker.
    // Дополнительная проверка реализации: после интервала ожидания допускается только один probe.
    // Основание: services/gateway/src/main/java/com/processing/gateway/circuitbreaker/InMemoryCircuitBreaker.java:65.
    @Test
    void allowsSingleProbeAtExactTimeoutBoundary() {
        openCircuit();
        when(clock.instant()).thenReturn(START.plusSeconds(10).minusNanos(1));
        assertFalse(breaker.allowRequest("switch"));
        when(clock.instant()).thenReturn(START.plusSeconds(10));
        assertTrue(breaker.allowRequest("switch"));
        assertFalse(breaker.allowRequest("switch"));
    }

    // Gateway: успешный probe закрывает контур и сбрасывает накопленные ошибки.
    // Требование: tz/02-gateway.md:115 — дополнительные задания, Circuit Breaker.
    // Дополнительная проверка реализации: успех закрывает контур и сбрасывает счётчик ошибок.
    // Основание: services/gateway/src/main/java/com/processing/gateway/circuitbreaker/InMemoryCircuitBreaker.java:93.
    @Test
    void closesAfterSuccessfulProbeAndResetsFailures() {
        openCircuit();
        when(clock.instant()).thenReturn(START.plusSeconds(10));
        assertTrue(breaker.allowRequest("switch"));
        breaker.recordSuccess("switch");
        assertTrue(breaker.allowRequest("switch"));
        assertTrue(breaker.allowRequest("switch"));
        breaker.recordFailure("switch");
        assertTrue(breaker.allowRequest("switch"));
    }

    // Gateway: неудачный probe начинает новый полный интервал ожидания.
    // Требование: tz/02-gateway.md:115 — дополнительные задания, Circuit Breaker.
    // Дополнительная проверка реализации: неудачный probe повторно открывает контур.
    // Основание: services/gateway/src/main/java/com/processing/gateway/circuitbreaker/InMemoryCircuitBreaker.java:110.
    @Test
    void reopensAfterFailedProbe() {
        openCircuit();
        when(clock.instant()).thenReturn(START.plusSeconds(10));
        assertTrue(breaker.allowRequest("switch"));
        breaker.recordFailure("switch");
        when(clock.instant()).thenReturn(START.plusSeconds(19));
        assertFalse(breaker.allowRequest("switch"));
        when(clock.instant()).thenReturn(START.plusSeconds(20));
        assertTrue(breaker.allowRequest("switch"));
    }

    // Gateway: отмена probe освобождает слот и не меняет состояние на успешное.
    // Требование: tz/02-gateway.md:115 — дополнительные задания, Circuit Breaker.
    // Дополнительная проверка реализации: отмена probe освобождает слот без изменения счётчиков.
    // Основание: services/gateway/src/main/java/com/processing/gateway/circuitbreaker/InMemoryCircuitBreaker.java:153.
    @Test
    void releasesCancelledProbe() {
        openCircuit();
        when(clock.instant()).thenReturn(START.plusSeconds(10));
        assertTrue(breaker.allowRequest("switch"));
        breaker.releaseRequest("switch");
        assertTrue(breaker.allowRequest("switch"));
        assertFalse(breaker.allowRequest("switch"));
    }

    private void openCircuit() {
        breaker.recordFailure("switch");
        breaker.recordFailure("switch");
    }
}
