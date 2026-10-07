package com.processing.service;

import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.transactionlogger.TransactionRequest;
import com.processing.common.dto.transactionlogger.TransactionStatus;
import com.processing.exception.AuthorizationException;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Практика 4: unit-тестирование СМП.
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Switch: маршрут BIN → Authorization → Logger проверяется с Mockito-клиентами.
 * Покрываем обогащение запроса, содержимое события и компенсацию при недоступности логирования.
 * Реальные HTTP, RabbitMQ и распределённая доставка не входят в эти unit-тесты.
 */
@ExtendWith(MockitoExtension.class)
class RouteServiceTest {
    @Mock private RoutingService routing;
    @Mock private AuthorizationClient authorization;
    @Mock private AcquiringFeeClient fees;
    @Mock private LoggerClient logger;
    @InjectMocks private RouteService service;

    // Switch: успешный маршрут передаёт issuerId и сохраняет бизнес-данные транзакции в событии Logger.
    // Требование: tz/03-switch.md:34 — раздел 2, шаги 3–7: авторизация и логирование.
    // Требование: tz/03-switch.md:65 — раздел 3, поля события транзакции.
    @Test
    void routesApprovedTransactionAndLogsItsBusinessFields() {
        AuthorizationRequest request = request();
        AuthorizationResponse approved = approved();
        prepareAuthorization(approved);
        when(fees.fetchAcquiringFee(request.transmissionDateTime(), request.stan(), request.pan(),
                request.terminalId(), request.amount())).thenReturn(new BigDecimal("2"));
        when(logger.log(any())).thenReturn(true);

        assertSame(approved, service.route(request));

        verify(authorization).authorize(request.withIssuerId("BANK001"));
        ArgumentCaptor<TransactionRequest> event = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(logger).log(event.capture());
        TransactionRequest transaction = event.getValue();
        assertAll(
                () -> assertNotNull(transaction.id()),
                () -> assertEquals(request.pan(), transaction.pan()),
                () -> assertEquals(request.amount(), transaction.amount()),
                () -> assertEquals(request.currencyCode(), transaction.currencyCode()),
                () -> assertEquals(request.stan(), transaction.stan()),
                () -> assertEquals(request.terminalId(), transaction.terminalId()),
                () -> assertEquals(request.merchantId(), transaction.merchantId()),
                () -> assertEquals("BANK001", transaction.issuerId()),
                () -> assertEquals(approved.rrn(), transaction.rrn()),
                () -> assertEquals(approved.authCode(), transaction.authCode()),
                () -> assertEquals(TransactionStatus.APPROVED, transaction.status()),
                () -> assertEquals(new BigDecimal("2"), transaction.acquiringFee()),
                () -> assertEquals(request.transmissionDateTime(), transaction.transmissionDateTime())
        );
        var order = inOrder(authorization, logger);
        order.verify(authorization).authorize(any());
        order.verify(logger).log(any());
        verify(authorization, never()).rollback(any(), anyString());
    }

    // Switch: неизвестный BIN даёт код 14 и не вызывает авторизацию или расчёт комиссии.
    // Требование: tz/03-switch.md:110 — раздел 4, неизвестный BIN даёт decline 14.
    @Test
    void declinesUnknownBinWithoutCallingAuthorization() {
        when(routing.getIssuerIdByPan(request().pan())).thenThrow(new UnknownBinException("400000"));
        when(logger.log(any())).thenReturn(true);
        AuthorizationResponse result = service.route(request());
        assertEquals("DECLINED", result.status());
        assertEquals("14", result.responseCode());
        assertEquals("CARD_NOT_FOUND", result.declineReason());
        verifyNoInteractions(authorization, fees);
        verify(logger).log(any(TransactionRequest.class));
    }

    // Switch: исчерпанные попытки Authorization преобразуются в decline 05, без компенсации.
    // Требование: tz/03-switch.md:110 — раздел 4, недоступность Authorization даёт decline 05.
    // Граница покрытия: исчерпание retry имитируется клиентом; сами три попытки здесь не проверяются.
    @Test
    void declinesWhenAuthorizationIsUnavailable() {
        when(routing.getIssuerIdByPan(request().pan())).thenReturn("BANK001");
        when(authorization.authorize(any())).thenThrow(new AuthorizationException("000001", 3, "timeout"));
        when(logger.log(any())).thenReturn(true);
        AuthorizationResponse result = service.route(request());
        assertEquals("DECLINED", result.status());
        assertEquals("05", result.responseCode());
        verifyNoInteractions(fees);
        verify(authorization, never()).rollback(any(), anyString());
    }

    // Switch: decline эмитента возвращается без изменения; отсутствие записи не требует возврата денег.
    // Требование: tz/03-switch.md:110 — раздел 4, пункт 3: отказ логирования для DECLINED.
    // Требование: tz/03-switch.md:34 — раздел 2, шаг 7: возврат ответа авторизации.
    @Test
    void preservesIssuerDeclineEvenWhenLoggingFails() {
        AuthorizationResponse declined = new AuthorizationResponse("0110", "000001", null, null,
                "51", "DECLINED", "INSUFFICIENT_FUNDS", 0);
        prepareAuthorization(declined);
        when(logger.log(any())).thenReturn(false);
        assertSame(declined, service.route(request()));
        ArgumentCaptor<TransactionRequest> event = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(logger).log(event.capture());
        assertEquals(TransactionStatus.DECLINED, event.getValue().status());
        assertEquals("INSUFFICIENT_FUNDS", event.getValue().declineReason());
        verify(authorization, never()).rollback(any(), anyString());
    }

    // Switch: отказ Logger после APPROVED инициирует rollback и код 96 при любом исходе компенсации.
    // Требование: tz/03-switch.md:110 — раздел 4, пункты 1–2: rollback и decline 96.
    // Требование: tz/03-switch.md:65 — раздел 3, компенсация при отсутствии подтверждения Logger.
    @ParameterizedTest
    @MethodSource("rollbackOutcomes")
    void compensatesApprovedTransactionWhenLoggingFails(RollbackResponse rollback) {
        prepareAuthorization(approved());
        when(logger.log(any())).thenReturn(false);
        when(authorization.rollback(any(), eq(approved().rrn()))).thenReturn(rollback);
        AuthorizationResponse result = service.route(request());
        assertEquals("DECLINED", result.status());
        assertEquals("96", result.responseCode());
        var order = inOrder(logger, authorization);
        order.verify(logger).log(any());
        order.verify(authorization).rollback(request().withIssuerId("BANK001"), approved().rrn());
    }

    static Stream<RollbackResponse> rollbackOutcomes() {
        return Stream.of(new RollbackResponse("627712000001", "00", "APPROVED", null, 0),
                new RollbackResponse("627712000001", "05", "DECLINED", "ROLLBACK_FAILED", 0), null);
    }

    private void prepareAuthorization(AuthorizationResponse response) {
        when(routing.getIssuerIdByPan(request().pan())).thenReturn("BANK001");
        when(authorization.authorize(any())).thenReturn(response);
    }

    private static AuthorizationResponse approved() {
        return new AuthorizationResponse("0110", "000001", "627712000001", "ABC123", "00", "APPROVED", null, 5);
    }

    private static AuthorizationRequest request() {
        return AuthorizationRequest.builder().mti("0100").stan("000001").pan("4000001234567899")
                .processingCode("000000").amount(new BigDecimal("100")).currencyCode("643")
                .transmissionDateTime(Instant.parse("2026-10-04T12:00:00Z"))
                .terminalId("TERM0001").terminalType("POS").merchantId("MERCH0000000001")
                .mcc("5411").acquirerId("ACQ001").issuerId("UNTRUSTED").build();
    }
}
