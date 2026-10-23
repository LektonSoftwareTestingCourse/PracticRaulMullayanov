package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.config.SwitchProperties;
import com.processing.exception.AuthorizationException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Unit-проверки ответов клиента; HTTP и Spring-контекст не запускаются. */
@ExtendWith(MockitoExtension.class)
class AuthorizationClientTest {
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec httpRequest;
    @Mock private RestClient.ResponseSpec httpResponse;
    private AuthorizationClient client;

    @BeforeEach
    void setUp() {
        SwitchProperties properties = new SwitchProperties("test", Map.of(), "http://authorization",
                null, null, null, new SwitchProperties.RetryProperties(1, List.of()), null);
        Retry retry = Retry.of("test", RetryConfig.custom().maxAttempts(1).build());
        client = new AuthorizationClient(properties, restClient, retry, CircuitBreaker.ofDefaults("test"));
        when(restClient.post()).thenReturn(httpRequest);
        when(httpRequest.uri(anyString())).thenReturn(httpRequest);
        when(httpRequest.body(any(Object.class))).thenReturn(httpRequest);
        when(httpRequest.retrieve()).thenReturn(httpResponse);
        when(httpResponse.onStatus(any(), any())).thenReturn(httpResponse);
    }

    @ParameterizedTest
    @CsvSource({"APPROVED", "DECLINED"})
    void shouldReturnAuthorizationWhenResponseHasValidStatus(String status) {
        // Arrange
        AuthorizationResponse expected = authorizationResponse(status);
        when(httpResponse.body(AuthorizationResponse.class)).thenReturn(expected);

        // Act
        AuthorizationResponse actual = client.authorize(request());

        // Assert
        assertSame(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("invalidAuthorizationResponses")
    void shouldRejectAuthorizationWhenResponseIsInvalid(AuthorizationResponse response) {
        // Arrange
        when(httpResponse.body(AuthorizationResponse.class)).thenReturn(response);

        // Act & Assert
        assertThrows(AuthorizationException.class, () -> client.authorize(request()));
    }

    @ParameterizedTest
    @CsvSource({"APPROVED", "DECLINED"})
    void shouldReturnRollbackWhenResponseHasValidStatus(String status) {
        // Arrange
        RollbackResponse expected = new RollbackResponse("627712000001", "00", status, null, 0);
        when(httpResponse.body(RollbackResponse.class)).thenReturn(expected);

        // Act
        RollbackResponse actual = client.rollback(request(), expected.rrn());

        // Assert
        assertSame(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("invalidRollbackResponses")
    void shouldReturnNoRollbackWhenResponseIsInvalid(RollbackResponse response) {
        // Arrange
        when(httpResponse.body(RollbackResponse.class)).thenReturn(response);

        // Act
        RollbackResponse actual = client.rollback(request(), "627712000001");

        // Assert
        assertNull(actual);
    }

    static Stream<Arguments> invalidAuthorizationResponses() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(authorizationResponse(null)),
                Arguments.of(authorizationResponse("UNKNOWN")));
    }

    static Stream<Arguments> invalidRollbackResponses() {
        return Stream.of(Arguments.of((Object) null),
                Arguments.of(new RollbackResponse("627712000001", "00", null, null, 0)),
                Arguments.of(new RollbackResponse("627712000001", "00", "UNKNOWN", null, 0)));
    }

    private static AuthorizationResponse authorizationResponse(String status) {
        return new AuthorizationResponse("0110", "000001", "627712000001", "ABC123", "00", status, null, 0);
    }

    private static AuthorizationRequest request() {
        return AuthorizationRequest.builder().mti("0100").stan("000001").pan("4000001234567899")
                .processingCode("000000").amount(new BigDecimal("100")).currencyCode("643")
                .transmissionDateTime(Instant.parse("2026-10-04T12:00:00Z"))
                .terminalId("TERM0001").terminalType("POS").merchantId("MERCH0000000001")
                .mcc("5411").acquirerId("ACQ001").issuerId("BANK001").build();
    }
}
