package com.processing.authorization.services;

import com.processing.authorization.client.BinLookupClient;
import com.processing.authorization.client.CardManagementClient;
import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.events.AuthServiceAuthorizeEvent;
import com.processing.authorization.exceptions.CardNotFoundException;
import com.processing.authorization.exceptions.InsufficientFundsException;
import com.processing.authorization.exceptions.InvalidGetCardRequestException;
import com.processing.authorization.exceptions.RollbackConflictException;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.authorization.RollbackRequest;
import com.processing.common.dto.authorization.RollbackResponse;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Практика 4: unit-тестирование СМП.
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Authorization: проверяют решение по карте, балансу и результату контроля лимитов.
 * Клиенты CMS/BIN, репозиторий и события изолированы Mockito; отказ не должен вызвать reserve.
 * SQL накопления лимитов и атомарность транзакций относятся к интеграционным тестам модуля 6.
 * Известные расхождения с ТЗ проверяются отдельно в AuthServiceKnownDefectsTest.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
    private static final String PAN = "4000001234567899";
    private static final BigDecimal AMOUNT = new BigDecimal("100");
    private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
    @Mock private LimitUsageRepository limits;
    @Mock private AuthorizationEventNotifier notifier;
    @Mock private BinLookupClient binLookup;
    @Mock private CardManagementClient cms;
    @InjectMocks private AuthServiceImpl service;

    // Authorization: активная карта с достаточными ресурсами одобряется, reserve получает тот же RRN и сумму.
    // Требование: tz/04-authorization.md:69 — раздел 2, шаг 7: одобрение транзакции.
    // Требование: tz/04-authorization.md:76 — раздел 3, резервирование суммы через CMS.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-01.
    @Test
    void approvesAndReservesExactAmountAfterCheckingLimits() {
        prepareCard(CardModelStatus.ACTIVE, new BigDecimal("1000"));
        allowLimits();
        when(limits.fetchRrnBlock()).thenReturn(1L);
        AuthorizationResponse response = authorize(request());
        assertAll(
                () -> assertEquals("APPROVED", response.status()),
                () -> assertEquals("00", response.responseCode()),
                () -> assertNull(response.declineReason()),
                () -> assertEquals(request().stan(), response.stan()),
                () -> assertNotNull(response.rrn()),
                () -> assertTrue(response.rrn().matches("[0-9]{12}")),
                () -> assertTrue(response.authCode().matches("[A-Z0-9]{6}"))
        );
        var order = inOrder(cms, limits, notifier);
        order.verify(cms).getCard(PAN);
        order.verify(limits).upsertLimitUsage(PAN, DATE, AMOUNT, new BigDecimal("1000"), new BigDecimal("30000"));
        order.verify(cms).reserve(AMOUNT, response.rrn(), PAN);
        order.verify(notifier).notify(new AuthServiceAuthorizeEvent(PAN));
    }

    // Authorization: отсутствующая карта и некорректный PAN отклоняются кодом 14 до лимитов и reserve.
    // Требование: tz/04-authorization.md:46 — раздел 2, шаг 1: карта не найдена → decline 14.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-02.
    @ParameterizedTest
    @MethodSource("missingCardErrors")
    void declinesMissingCard(RuntimeException error) {
        when(cms.getCard(PAN)).thenThrow(error);
        assertDecline(authorize(request()), "14", "CARD_NOT_FOUND");
        verifyNoInteractions(limits, binLookup);
        verifyNoReserve();
    }

    static Stream<RuntimeException> missingCardErrors() {
        return Stream.of(new CardNotFoundException("missing"), new InvalidGetCardRequestException("invalid PAN"));
    }

    // Authorization: каждый неактивный статус имеет свою причину отказа; деньги не резервируются.
    // Требование: tz/04-authorization.md:49 — раздел 2, шаг 2: отказ по статусу карты.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-03, AUTH-04, AUTH-05.
    @ParameterizedTest
    @CsvSource({"INACTIVE, 05, CARD_INACTIVE", "BLOCKED, 05, CARD_BLOCKED", "EXPIRED, 54, CARD_EXPIRED"})
    void declinesNonActiveCard(CardModelStatus status, String code, String reason) {
        prepareCard(status, new BigDecimal("1000"));
        assertDecline(authorize(request()), code, reason);
        verifyNoInteractions(limits);
        verifyNoReserve();
    }

    // Authorization: срок MMYY действует до конца указанного месяца включительно.
    // Требование: tz/04-authorization.md:55 — раздел 2, шаг 3: проверка срока действия.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-06, AUTH-07.
    @ParameterizedTest
    @CsvSource({"2026-10-31T23:59:59Z, true", "2026-11-01T00:00:00Z, false"})
    void checksExpiryAtMonthBoundary(String timestamp, boolean valid) {
        when(cms.getCard(PAN)).thenReturn(card(CardModelStatus.ACTIVE, new BigDecimal("1000"), YearMonth.of(2026, 10)));
        when(binLookup.getIssuerId(PAN)).thenReturn(Optional.empty());
        AuthorizationRequest request = requestAt(Instant.parse(timestamp));
        if (valid) {
            when(limits.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(1);
            when(limits.fetchRrnBlock()).thenReturn(1L);
            AuthorizationResponse response = authorize(request);
            assertEquals("APPROVED", response.status());
            verify(cms).reserve(AMOUNT, response.rrn(), PAN);
        } else {
            assertDecline(authorize(request), "54", "CARD_EXPIRED");
            verifyNoInteractions(limits);
            verifyNoReserve();
        }
    }

    // Authorization: amount <= balance допустимо; равенство не должно ошибочно давать insufficient funds.
    // Требование: tz/04-authorization.md:65 — раздел 2, шаг 6: amount <= availableBalance.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-10.
    @ParameterizedTest
    @ValueSource(strings = {"100", "101"})
    void acceptsBalanceAtOrAboveAmount(String balance) {
        prepareCard(CardModelStatus.ACTIVE, new BigDecimal(balance));
        allowLimits();
        when(limits.fetchRrnBlock()).thenReturn(1L);
        AuthorizationResponse response = authorize(request());
        assertEquals("APPROVED", response.status());
        verify(cms).reserve(AMOUNT, response.rrn(), PAN);
    }

    // Authorization: сумма на одну копейку выше доступного баланса отклоняется с кодом 51.
    // Требование: tz/04-authorization.md:65 — раздел 2, шаг 6: недостаток средств даёт decline 51.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-10.
    @Test
    void declinesAmountAboveAvailableBalance() {
        prepareCard(CardModelStatus.ACTIVE, new BigDecimal("99"));
        assertDecline(authorize(request()), "51", "INSUFFICIENT_FUNDS");
        verifyNoReserve();
    }

    // Authorization: репозиторий отклонил лимиты — сервис выдаёт код 61 и не резервирует деньги.
    // Этот тест проверяет реакцию на ответ репозитория, а не SQL сравнения daily/monthly.
    // Требование: tz/04-authorization.md:58 — раздел 2, шаги 4–5: отказ при превышении лимитов.
    // Требование: tz/04-authorization.md:85 — раздел 4, контроль использованных лимитов.
    // Связь со сценариями: docs/practice-2/test-design.md, раздел 6.1: AUTH-08, AUTH-09.
    // Граница покрытия: проверяется реакция на отказ репозитория; границы SQL требуют интеграционного теста.
    @Test
    void declinesWhenLimitRepositoryRejectsOperation() {
        prepareCard(CardModelStatus.ACTIVE, new BigDecimal("1000"));
        when(limits.upsertLimitUsage(PAN, DATE, AMOUNT, new BigDecimal("1000"), new BigDecimal("30000")))
                .thenReturn(0);
        assertDecline(authorize(request()), "61", "EXCEEDS_AMOUNT_LIMIT");
        verify(limits, never()).fetchRrnBlock();
        verifyNoReserve();
    }

    // Authorization: публичный контроль лимитов передаёт PAN, дату, сумму и оба лимита без искажения.
    // Требование: tz/04-authorization.md:85 — раздел 4, PAN, дата, сумма и лимиты из CMS.
    // Граница покрытия: корректность SQL накопления daily/monthly здесь не проверяется.
    @ParameterizedTest
    @CsvSource({"0, false", "1, true"})
    void mapsLimitUpdateResultAndPreservesArguments(int updated, boolean allowed) {
        CardModel card = card(CardModelStatus.ACTIVE, new BigDecimal("1000"), YearMonth.of(2029, 10));
        when(limits.upsertLimitUsage(PAN, DATE, AMOUNT, card.dailyLimit(), card.monthlyLimit())).thenReturn(updated);
        assertEquals(allowed, service.checkAndUpdateLimits(card, AMOUNT, DATE));
        verify(limits).upsertLimitUsage(PAN, DATE, AMOUNT, card.dailyLimit(), card.monthlyLimit());
        verifyNoInteractions(cms);
    }

    // Authorization: коллизия записи usage допускает одну повторную попытку, успешный повтор продолжает операцию.
    // Требование: tz/04-authorization.md:85 — раздел 4, отслеживание использованных лимитов.
    // Дополнительная проверка реализации: один повтор обновления после DuplicateKeyException.
    // Основание: services/authorization/src/main/java/com/processing/authorization/services/AuthServiceImpl.java:126.
    @Test
    void retriesLimitUpdateOnceAfterDuplicateKey() {
        prepareCard(CardModelStatus.ACTIVE, new BigDecimal("1000"));
        when(limits.upsertLimitUsage(anyString(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("concurrent insert")).thenReturn(1);
        when(limits.fetchRrnBlock()).thenReturn(1L);
        assertEquals("APPROVED", authorize(request()).status());
        verify(limits, times(2)).upsertLimitUsage(PAN, DATE, AMOUNT, new BigDecimal("1000"), new BigDecimal("30000"));
        verify(cms).reserve(eq(AMOUNT), anyString(), eq(PAN));
    }

    // Authorization: повторная ошибка usage прекращает операцию с кодом системной ошибки, reserve не вызывается.
    // Требование: tz/04-authorization.md:85 — раздел 4, отслеживание использованных лимитов.
    // Дополнительная проверка реализации: неудачный повтор прекращает операцию до reserve.
    // Основание: services/authorization/src/main/java/com/processing/authorization/services/AuthServiceImpl.java:126.
    @Test
    void stopsAfterFailedLimitUpdateRetry() {
        prepareCard(CardModelStatus.ACTIVE, new BigDecimal("1000"));
        when(limits.upsertLimitUsage(anyString(), any(), any(), any(), any()))
                .thenThrow(new DuplicateKeyException("concurrent insert"))
                .thenThrow(new IllegalStateException("database down"));
        AuthorizationResponse response = authorize(request());
        assertEquals("DECLINED", response.status());
        assertEquals("96", response.responseCode());
        verify(limits, times(2)).upsertLimitUsage(anyString(), any(), any(), any(), any());
        verifyNoReserve();
    }

    // Authorization: баланс мог измениться между GET и reserve; отказ CMS не превращается в APPROVED.
    // Требование: tz/04-authorization.md:65 — раздел 2, шаг 6: недостаток средств.
    // Требование: tz/04-authorization.md:122 — примечание об изменении баланса между GET карты и reserve.
    // Связанный сценарий недостатка средств: docs/practice-2/test-design.md, раздел 6.1: AUTH-10.
    @Test
    void declinesWhenReserveReportsInsufficientFunds() {
        prepareCard(CardModelStatus.ACTIVE, new BigDecimal("1000"));
        allowLimits();
        when(limits.fetchRrnBlock()).thenReturn(1L);
        doThrow(new InsufficientFundsException("balance changed")).when(cms).reserve(eq(AMOUNT), anyString(), eq(PAN));
        assertDecline(authorize(request()), "51", "INSUFFICIENT_FUNDS");
        verify(notifier, never()).notify(any(AuthServiceAuthorizeEvent.class));
    }

    // Authorization: отсутствие ответа BIN Lookup не мешает авторизации по данным карты и маршрута Switch.
    // Требование: tz/04-authorization.md:36 — раздел 2, обогащение issuerId и fallback BIN Lookup.
    @Test
    void approvesWithBinLookupFallback() {
        prepareCard(CardModelStatus.ACTIVE, new BigDecimal("1000"));
        when(binLookup.getIssuerId(PAN)).thenReturn(Optional.empty());
        allowLimits();
        when(limits.fetchRrnBlock()).thenReturn(1L);
        assertEquals("APPROVED", authorize(request()).status());
        verify(binLookup).getIssuerId(PAN);
    }

    // Authorization: успешный откат передаёт неизменные PAN, RRN и сумму и возвращает код 00.
    // Требование: tz/04-authorization.md:126 — дополнительные задания, Reversal: возврат зарезервированной суммы.
    // Контракт отката: docs/api/openapi.yaml:1192 — RollbackRequest.
    @Test
    void approvesSuccessfulRollback() {
        RollbackRequest request = new RollbackRequest("627712000001", PAN, AMOUNT);
        RollbackResponse response = service.rollback(request, Instant.now());
        assertEquals("APPROVED", response.status());
        assertEquals("00", response.responseCode());
        assertEquals(request.rrn(), response.rrn());
        verify(cms).rollback(request);
    }

    // Authorization: повторный откат отклоняется; сервис не должен сообщать об успешном возврате.
    // Требование идемпотентности: docs/api/openapi.yaml:1192 — RollbackRequest.
    // Дополнительная проверка реализации: RollbackConflictException даёт отказ ALREADY_ROLLED_BACK.
    // Основание: services/authorization/src/main/java/com/processing/authorization/services/AuthServiceImpl.java:232.
    @Test
    void declinesAlreadyRolledBackTransaction() {
        RollbackRequest request = new RollbackRequest("627712000001", PAN, AMOUNT);
        doThrow(new RollbackConflictException("already rolled back")).when(cms).rollback(request);
        RollbackResponse response = service.rollback(request, Instant.now());
        assertEquals("DECLINED", response.status());
        assertEquals("05", response.responseCode());
        assertEquals("ALREADY_ROLLED_BACK", response.declineReason());
    }

    // Authorization: последовательные RRN уникальны, включая переход к следующему блоку последовательности.
    // Временной формат ТЗ проверяет отдельный regression-тест известного BUG-P2-003.
    // Требование: tz/04-authorization.md:114 — раздел 6, уникальный RRN из 12 цифр.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-13.
    @Test
    void generatesUniqueRrnAcrossBlockBoundary() {
        when(limits.fetchRrnBlock()).thenReturn(1L, 2L);
        Set<String> identifiers = new HashSet<>();
        for (int i = 0; i < 1001; i++) {
            String rrn = service.generateRRN();
            assertTrue(rrn.matches("[0-9]{12}"), rrn);
            assertTrue(identifiers.add(rrn), "Duplicate RRN: " + rrn);
        }
        assertEquals(1001, identifiers.size());
        verify(limits, times(2)).fetchRrnBlock();
    }

    // Authorization: authCode имеет шесть букв/цифр; конкретный случайный код не фиксируется в ожидании.
    // Требование: tz/04-authorization.md:114 — раздел 6, authCode из шести букв и цифр.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-01.
    @Test
    void generatesAuthCodeInRequiredAlphabet() {
        assertTrue(service.generateAuthCode().matches("[A-Z0-9]{6}"));
    }

    private void prepareCard(CardModelStatus status, BigDecimal balance) {
        when(cms.getCard(PAN)).thenReturn(card(status, balance, YearMonth.of(2029, 10)));
        when(binLookup.getIssuerId(PAN)).thenReturn(Optional.of("BANK001"));
    }

    private void allowLimits() {
        when(limits.upsertLimitUsage(PAN, DATE, AMOUNT, new BigDecimal("1000"), new BigDecimal("30000"))).thenReturn(1);
    }

    private AuthorizationResponse authorize(AuthorizationRequest request) {
        return service.authorize(request, Instant.now());
    }

    private void verifyNoReserve() {
        verify(cms, never()).reserve(any(), anyString(), anyString());
    }

    private static void assertDecline(AuthorizationResponse response, String code, String reason) {
        assertEquals("DECLINED", response.status());
        assertEquals(code, response.responseCode());
        assertEquals(reason, response.declineReason());
        assertNull(response.rrn());
        assertNull(response.authCode());
    }

    private static AuthorizationRequest request() {
        return requestAt(Instant.parse("2026-10-04T12:00:00Z"));
    }

    private static AuthorizationRequest requestAt(Instant timestamp) {
        return AuthorizationRequest.builder().mti("0100").stan("000001").pan(PAN).processingCode("000000")
                .amount(AMOUNT).currencyCode("643").transmissionDateTime(timestamp).terminalId("TERM0001")
                .terminalType("POS").merchantId("MERCH0000000001").mcc("5411")
                .acquirerId("ACQ001").issuerId("BANK001").build();
    }

    private static CardModel card(CardModelStatus status, BigDecimal balance, YearMonth expiry) {
        return new CardModel(UUID.fromString("00000000-0000-0000-0000-000000000001"), PAN, "400000", "IVAN IVANOV",
                expiry, status, "643", new BigDecimal("1000"), new BigDecimal("30000"), balance,
                "BANK001", Instant.parse("2026-10-04T12:00:00Z"));
    }
}
