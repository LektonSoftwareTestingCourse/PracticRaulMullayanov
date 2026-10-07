package com.processing.authorization.services;

import com.processing.authorization.client.BinLookupClient;
import com.processing.authorization.client.CardManagementClient;
import com.processing.authorization.events.AuthorizationEventNotifier;
import com.processing.authorization.exceptions.ServiceUnavailableException;
import com.processing.authorization.repositories.LimitUsageRepository;
import com.processing.common.dto.authorization.AuthorizationRequest;
import com.processing.common.dto.authorization.AuthorizationResponse;
import com.processing.common.dto.cardmanagement.CardModel;
import com.processing.common.dto.cardmanagement.CardModelStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit regression-тесты Authorization по ТЗ, воспроизводящие BUG-P2-002, BUG-P2-003 и BUG-P2-004.
 * Ожидания намеренно не подстроены под ошибки реализации. Бизнес-код в практике 3 не исправляется.
 * По умолчанию этот отдельный набор пропускается; для воспроизведения из корня репозитория:
 * mvn -f services/pom.xml -Pauthorization -Dtest=AuthServiceKnownDefectsTest -DrunKnownDefects=true test
 * При включении набор должен показать дефекты, пока соответствующие правила не исправлены.
 * BUG-P2-005 требует проверки настоящей транзакции БД и здесь не имитируется Mockito.
 */
@ExtendWith(MockitoExtension.class)
@EnabledIfSystemProperty(named = "runKnownDefects", matches = "true",
        disabledReason = "Известные BUG-P2-002/003/004: для проверки ТЗ используйте -DrunKnownDefects=true")
class AuthServiceKnownDefectsTest {
    private static final String PAN = "4000001234567899";
    @Mock private LimitUsageRepository limits;
    @Mock private AuthorizationEventNotifier notifier;
    @Mock private BinLookupClient binLookup;
    @Mock private CardManagementClient cms;
    @InjectMocks private AuthServiceImpl service;

    // Authorization, BUG-P2-002: при одновременном превышении лимита и баланса приоритет имеет лимит (61).
    // Требование: tz/04-authorization.md:43 — раздел 2, строгий порядок шагов 4–6.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-11.
    // Связанный дефект: docs/practice-2/test-design.md:223 — BUG-P2-002.
    @Test
    void limitDeclineMustTakePriorityOverInsufficientBalance() {
        prepareCard(new BigDecimal("50"));
        // В дефектной реализации до этой зависимости не доходят: stub описывает требуемый ответ проверки лимитов.
        lenient().when(limits.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(0);
        AuthorizationResponse response = service.authorize(request(), Instant.now());
        assertEquals("DECLINED", response.status());
        assertEquals("61", response.responseCode(), "ТЗ: лимиты проверяются раньше баланса, BUG-P2-002");
        assertEquals("EXCEEDS_AMOUNT_LIMIT", response.declineReason());
        verify(cms, never()).reserve(any(), anyString(), anyString());
    }

    // Authorization, BUG-P2-004: недоступность CMS при GET и при reserve должна давать 05 / ISSUER_TIMEOUT.
    // Требование: tz/04-authorization.md:110 — раздел 5, CMS недоступен → 05 / ISSUER_TIMEOUT.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-12, AUTH-15.
    // Связанный дефект: docs/practice-2/test-design.md:255 — BUG-P2-004.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cmsUnavailableMustReturnIssuerTimeout(boolean failDuringReserve) {
        if (failDuringReserve) {
            prepareCard(new BigDecimal("1000"));
            when(limits.upsertLimitUsage(anyString(), any(), any(), any(), any())).thenReturn(1);
            when(limits.fetchRrnBlock()).thenReturn(1L);
            doThrow(new ServiceUnavailableException("CMS down"))
                    .when(cms).reserve(eq(new BigDecimal("100")), anyString(), eq(PAN));
        } else {
            when(cms.getCard(PAN)).thenThrow(new ServiceUnavailableException("CMS down"));
        }
        AuthorizationResponse response = service.authorize(request(), Instant.now());
        assertEquals("DECLINED", response.status());
        assertEquals("05", response.responseCode(), "ТЗ требует код 05, BUG-P2-004");
        assertEquals("ISSUER_TIMEOUT", response.declineReason());
    }

    // Authorization, BUG-P2-003: RRN должен содержать HHmmss времени генерации, а не цифры номера блока БД.
    // Требование: tz/04-authorization.md:114 — раздел 6, временные компоненты RRN: YDDD и HHmmss.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.1: AUTH-13.
    // Связанный дефект: docs/practice-2/test-design.md:239 — BUG-P2-003.
    @Test
    void rrnMustContainGenerationTime() {
        Instant instant = Instant.parse("2026-10-04T12:34:56Z");
        LocalDate date = LocalDate.of(2026, 10, 4);
        LocalDateTime dateTime = LocalDateTime.of(2026, 10, 4, 12, 34, 56);
        when(limits.fetchRrnBlock()).thenReturn(1L);
        try (MockedStatic<Instant> instants = mockStatic(Instant.class);
             MockedStatic<LocalDate> dates = mockStatic(LocalDate.class);
             MockedStatic<LocalDateTime> times = mockStatic(LocalDateTime.class)) {
            instants.when(Instant::now).thenReturn(instant);
            dates.when(LocalDate::now).thenReturn(date);
            times.when(LocalDateTime::now).thenReturn(dateTime);
            String rrn = service.generateRRN();
            assertTrue(rrn.matches("[0-9]{12}"));
            assertEquals("6277", rrn.substring(0, 4));
            assertEquals("123456", rrn.substring(4, 10), "ТЗ требует компонент HHmmss, BUG-P2-003");
        }
    }

    private void prepareCard(BigDecimal balance) {
        when(cms.getCard(PAN)).thenReturn(new CardModel(UUID.fromString("00000000-0000-0000-0000-000000000001"),
                PAN, "400000", "IVAN IVANOV", YearMonth.of(2029, 10), CardModelStatus.ACTIVE, "643",
                new BigDecimal("1000"), new BigDecimal("30000"), balance, "BANK001",
                Instant.parse("2026-10-04T12:00:00Z")));
        when(binLookup.getIssuerId(PAN)).thenReturn(Optional.empty());
    }

    private static AuthorizationRequest request() {
        return AuthorizationRequest.builder().mti("0100").stan("000001").pan(PAN).processingCode("000000")
                .amount(new BigDecimal("100")).currencyCode("643")
                .transmissionDateTime(Instant.parse("2026-10-04T12:00:00Z"))
                .terminalId("TERM0001").terminalType("POS").merchantId("MERCH0000000001")
                .mcc("5411").acquirerId("ACQ001").issuerId("BANK001").build();
    }
}
