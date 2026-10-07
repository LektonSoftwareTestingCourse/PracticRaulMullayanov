package com.processing.gateway.ratelimit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processing.gateway.metrics.GatewayMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Gateway: фильтр переводит превышение квоты в требуемый HTTP 429 / RATE_LIMIT_EXCEEDED.
 * MockServerWebExchange заменяет HTTP-сервер, Mockito — limiter, resolver, метрики и downstream-цепочку.
 * Покрываем отказ, разрешённый запрос и обход лимитера для других endpoint/HTTP-методов.
 */
@ExtendWith(MockitoExtension.class)
class TransactionRateLimitFilterTest {
    private static final String KEY = "POST /api/transactions:127.0.0.1";
    @Mock private InMemoryRateLimiter limiter;
    @Mock private ClientIpResolver resolver;
    @Mock private GatewayMetrics metrics;
    @Mock private GatewayFilterChain chain;
    private final ObjectMapper mapper = new ObjectMapper();
    private TransactionRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TransactionRateLimitFilter(limiter, resolver, mapper, metrics);
        when(resolver.resolve(any())).thenReturn("127.0.0.1");
    }

    // Gateway: отказ лимитера завершает запрос без downstream-вызова и сообщает время повторной попытки.
    // Требование: tz/02-gateway.md:80 — раздел 4, HTTP 429 и retryAfterMs при превышении лимита.
    @Test
    void rejectsExceededQuotaWithRequiredResponse() throws Exception {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/transactions"));
        when(limiter.allowRequest(KEY)).thenReturn(false);
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, exchange.getResponse().getHeaders().getContentType());
        assertEquals("1", exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        JsonNode body = mapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals("RATE_LIMIT_EXCEEDED", body.get("error").asText());
        assertEquals(1000, body.get("retryAfterMs").asInt());
        verify(metrics).recordRateLimitRejected();
        verifyNoInteractions(chain);
    }

    // Gateway: разрешённый запрос передаётся ровно в исходную цепочку без метрики отказа.
    // Требование: tz/02-gateway.md:40 — раздел 2, передача допустимой транзакции в Switch.
    // Требование: tz/02-gateway.md:80 — раздел 4, применение лимита к транзакциям.
    @Test
    void forwardsAllowedTransaction() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/transactions"));
        when(limiter.allowRequest(KEY)).thenReturn(true);
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(exchange);
        verifyNoInteractions(metrics);
    }

    // Gateway: лимит транзакций не расходуется при health-check, чтении транзакций или CRUD карт.
    // Требование: tz/02-gateway.md:80 — раздел 4, лимит для POST /api/transactions.
    @ParameterizedTest
    @CsvSource({"GET, /api/transactions", "GET, /health", "POST, /api/cards"})
    void bypassesUnrelatedRequests(String method, String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path));
        when(chain.filter(exchange)).thenReturn(Mono.empty());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        verify(chain).filter(exchange);
        verifyNoInteractions(limiter, metrics);
    }
}
