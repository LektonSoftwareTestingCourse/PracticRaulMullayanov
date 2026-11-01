package com.processing.gateway.validation;

import com.processing.common.dto.authorization.AuthorizationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Практика 4: unit-тестирование СМП.
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Gateway: защищают входной контракт транзакции до обращения к Switch.
 * Покрывают обязательные поля, форматы и границу суммы; HTTP и Spring-контекст не нужны.
 */
class TransactionRequestValidatorTest {
    private final TransactionRequestValidator validator = new TransactionRequestValidator();

    // Gateway: корректный запрос должен пройти все проверки входного контракта.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    @Test
    void shouldAcceptTransactionWhenRequestIsValid() {
        // Arrange
        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(validRequest().build()));
    }

    // Gateway: отсутствие тела отклоняется понятной ошибкой вместо NullPointerException.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    @Test
    void shouldRejectTransactionWhenBodyIsMissing() {
        // Arrange
        // Act & Assert
        assertInvalid(null, "Request body");
    }

    // Gateway: каждое обязательное поле проверяется отдельно, чтобы не пропустить раннюю ветвь отказа.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    // Контракт обязательных полей: docs/api-spec.md:289 — модель AuthorizationRequest.
    @ParameterizedTest
    @MethodSource("missingRequiredFields")
    void shouldRejectTransactionWhenRequiredFieldIsMissing(AuthorizationRequest request, String field) {
        // Arrange
        // Act & Assert
        assertInvalid(request, field);
    }

    static Stream<Object[]> missingRequiredFields() {
        return Stream.of(
                new Object[]{validRequest().mti(null).build(), "mti"},
                new Object[]{validRequest().stan(null).build(), "stan"},
                new Object[]{validRequest().pan(null).build(), "pan"},
                new Object[]{validRequest().processingCode(null).build(), "processingCode"},
                new Object[]{validRequest().currencyCode(null).build(), "currencyCode"},
                new Object[]{validRequest().transmissionDateTime(null).build(), "transmissionDateTime"},
                new Object[]{validRequest().terminalId(null).build(), "terminalId"},
                new Object[]{validRequest().merchantId(null).build(), "merchantId"},
                new Object[]{validRequest().mcc(null).build(), "mcc"},
                new Object[]{validRequest().acquirerId(null).build(), "acquirerId"},
                new Object[]{validRequest().amount(null).build(), "amount"}
        );
    }

    // Gateway: пустые и состоящие из пробелов значения не считаются заполненными полями.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    // Контракт обязательных полей: docs/api-spec.md:289 — модель AuthorizationRequest.
    @ParameterizedTest
    @MethodSource("blankRequiredFields")
    void shouldRejectTransactionWhenRequiredFieldIsBlank(AuthorizationRequest request, String field) {
        // Arrange
        // Act & Assert
        assertInvalid(request, field);
    }

    static Stream<Object[]> blankRequiredFields() {
        return Stream.of("", " ", "\t").flatMap(blank -> Stream.of(
                new Object[]{validRequest().mti(blank).build(), "mti"},
                new Object[]{validRequest().stan(blank).build(), "stan"},
                new Object[]{validRequest().pan(blank).build(), "pan"},
                new Object[]{validRequest().processingCode(blank).build(), "processingCode"},
                new Object[]{validRequest().currencyCode(blank).build(), "currencyCode"},
                new Object[]{validRequest().terminalId(blank).build(), "terminalId"},
                new Object[]{validRequest().merchantId(blank).build(), "merchantId"},
                new Object[]{validRequest().mcc(blank).build(), "mcc"},
                new Object[]{validRequest().acquirerId(blank).build(), "acquirerId"}
        ));
    }

    // Gateway: PAN должен содержать ровно 16 цифр, поэтому проверяем обе границы длины и символы.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"400000123456789", "40000012345678901", "400000123456789X"})
    void shouldRejectTransactionWhenPanIsMalformed(String pan) {
        // Arrange
        // Act & Assert
        assertInvalid(validRequest().pan(pan).build(), "pan");
    }

    // Gateway: авторизационный вход допускает MTI 0100, другой тип операции не отправляется дальше.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    @ParameterizedTest
    @ValueSource(strings = {"0400", "0110", "100", "ABCD"})
    void shouldRejectTransactionWhenMtiIsUnsupported(String mti) {
        // Arrange
        // Act & Assert
        assertInvalid(validRequest().mti(mti).build(), "mti");
    }

    // Gateway: сумма покупки строго положительна; ноль и отрицательные значения запрещены.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1"})
    void shouldRejectTransactionWhenAmountIsNotPositive(String amount) {
        // Arrange
        // Act & Assert
        assertInvalid(validRequest().amount(new BigDecimal(amount)).build(), "amount");
    }

    // Gateway: положительная сумма на границе 1 копейки допустима.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    @Test
    void shouldAcceptTransactionWhenAmountIsOneKopeck() {
        // Arrange
        // Act & Assert
        assertDoesNotThrow(() -> validator.validate(validRequest().amount(BigDecimal.ONE).build()));
    }

    // Gateway: коды валюты и MCC должны соответствовать длинам входного контракта.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    @ParameterizedTest
    @ValueSource(strings = {"64", "6430"})
    void shouldRejectTransactionWhenCurrencyLengthIsInvalid(String currency) {
        // Arrange
        // Act & Assert
        assertInvalid(validRequest().currencyCode(currency).build(), "currencyCode");
    }

    // Gateway: MCC — четыре цифры; не допускаем неполный код или буквы.
    // Требование: tz/02-gateway.md:40 — раздел 2, валидация входной транзакции.
    @ParameterizedTest
    @ValueSource(strings = {"541", "54110", "ABCD"})
    void shouldRejectTransactionWhenMccIsMalformed(String mcc) {
        // Arrange
        // Act & Assert
        assertInvalid(validRequest().mcc(mcc).build(), "mcc");
    }

    private void assertInvalid(AuthorizationRequest request, String field) {
        TransactionValidationException error = assertThrows(
                TransactionValidationException.class, () -> validator.validate(request));
        assertTrue(error.getMessage().contains(field), error.getMessage());
    }

    private static AuthorizationRequest.AuthorizationRequestBuilder validRequest() {
        return AuthorizationRequest.builder().mti("0100").stan("000001").pan("4000001234567899")
                .processingCode("000000").amount(new BigDecimal("100")).currencyCode("643")
                .transmissionDateTime(Instant.parse("2026-10-04T12:00:00Z"))
                .terminalId("TERM0001").terminalType("POS").merchantId("MERCH0000000001")
                .mcc("5411").acquirerId("ACQ001");
    }
}
