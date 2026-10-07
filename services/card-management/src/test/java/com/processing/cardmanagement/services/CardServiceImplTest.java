package com.processing.cardmanagement.services;

import com.processing.cardmanagement.events.CardEventNotifier;
import com.processing.cardmanagement.events.CardServiceCreationEvent;
import com.processing.cardmanagement.events.CardServiceDeletionEvent;
import com.processing.cardmanagement.events.CardServicePatchEvent;
import com.processing.cardmanagement.events.CardServiceReserveEvent;
import com.processing.cardmanagement.events.CardServiceRollbackEvent;
import com.processing.cardmanagement.exceptions.CardNotFoundException;
import com.processing.cardmanagement.exceptions.InsufficientFundsException;
import com.processing.cardmanagement.exceptions.ReservationAlreadyExistsException;
import com.processing.cardmanagement.exceptions.ReservationNotFoundException;
import com.processing.cardmanagement.exceptions.RollbackAlreadySatisfiedException;
import com.processing.cardmanagement.exceptions.TooLargeLimitException;
import com.processing.cardmanagement.models.Card;
import com.processing.cardmanagement.models.CardStatus;
import com.processing.cardmanagement.models.Reservation;
import com.processing.cardmanagement.models.ReservationRollback;
import com.processing.cardmanagement.models.ReservationStatus;
import com.processing.cardmanagement.options.CardServiceDefaults;
import com.processing.cardmanagement.options.CardServiceSettings;
import com.processing.cardmanagement.repositories.CardRepository;
import com.processing.cardmanagement.repositories.ReservationRepository;
import com.processing.cardmanagement.repositories.ReservationRollbackRepository;
import com.processing.cardmanagement.services.retries.RetryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Практика 4: unit-тестирование СМП.
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Card Management: проверяют оркестрацию CRUD, reserve и rollback, а не работу PostgreSQL.
 * Mockito изолирует репозитории и события; TransactionRunner выполняет callback без реальной транзакции.
 * Проверяем суммы, параметры сохранения и отсутствие записей/событий при отказе.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {
    private static final String PAN = "4000001234567899";
    private static final String RRN = "627712000001";
    private static final BigDecimal AMOUNT = new BigDecimal("40");
    @Mock private CardRepository cards;
    @Mock private ReservationRepository reservations;
    @Mock private ReservationRollbackRepository rollbacks;
    @Mock private CardServiceSettings settings;
    @Mock private CardServiceDefaults defaults;
    @Mock private PanGenerator panGenerator;
    @Mock private CardEventNotifier notifier;
    @Mock private BinIssuerService binIssuerService;
    @Mock private RetryService retryService;
    @Mock private TransactionRunner transactions;
    @InjectMocks private CardServiceImpl service;

    // Card Management: создание назначает ACTIVE и срок +3 года, сохраняя BIN, эмитента, валюту и суммы.
    // Требование: tz/05-card-management.md:32 — раздел 2, POST: PAN, ACTIVE, срок +3 года и сохранение.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-01.
    @Test
    void createsActiveCardWithRequestedBusinessFields() {
        YearMonth creationMonth = YearMonth.of(2026, 10);
        YearMonth expiry = YearMonth.of(2029, 10);
        when(binIssuerService.getIssuerId("400000")).thenReturn("BANK001");
        when(panGenerator.generatePan("400000")).thenReturn(PAN);
        when(settings.cardValidityPeriod()).thenReturn(3);
        when(settings.maxCardCreationRetries()).thenReturn(3);
        when(retryService.supply(eq(3), any())).thenAnswer(call -> call.<Supplier<?>>getArgument(1).get());
        when(cards.create(any(Card.class))).thenAnswer(call -> call.getArgument(0));
        try (MockedStatic<YearMonth> months = mockStatic(YearMonth.class, CALLS_REAL_METHODS)) {
            months.when(YearMonth::now).thenReturn(creationMonth);
            Card created = service.createCard("400000", "IVAN IVANOV", "643",
                    new BigDecimal("1000"), new BigDecimal("30000"), new BigDecimal("100"));
            assertEquals(PAN, created.pan());
            assertEquals("400000", created.bin());
            assertEquals("BANK001", created.issuerId());
            assertEquals("IVAN IVANOV", created.cardholderName());
            assertEquals("643", created.currencyCode());
            assertEquals(CardStatus.ACTIVE, created.status());
            assertEquals(expiry, created.expiryDate());
            assertEquals(new BigDecimal("1000"), created.dailyLimit());
            assertEquals(new BigDecimal("30000"), created.monthlyLimit());
            assertEquals(new BigDecimal("100"), created.availableBalance());
            var order = inOrder(cards, notifier);
            order.verify(cards).create(created);
            order.verify(notifier).notifyListeners(new CardServiceCreationEvent(1));
        }
    }

    // Card Management: найденная карта возвращается вызывающему коду без изменения.
    // Требование: tz/05-card-management.md:54 — раздел 2, GET /api/cards/{pan}: получение карты.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-02.
    @Test
    void returnsExistingCard() {
        Card card = card();
        when(cards.findByPan(PAN)).thenReturn(Optional.of(card));
        assertSame(card, service.getCard(PAN));
        verifyNoInteractions(notifier);
    }

    // Card Management: отсутствующая карта даёт предметную ошибку, а не null.
    // Требование: tz/05-card-management.md:54 — раздел 2, GET /api/cards/{pan}: карта не найдена.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-03.
    @Test
    void rejectsMissingCard() {
        when(cards.findByPan(PAN)).thenReturn(Optional.empty());
        assertThrows(CardNotFoundException.class, () -> service.getCard(PAN));
    }

    // Card Management: параметры пагинации по умолчанию передаются из конфигурации в репозиторий.
    // Требование: tz/05-card-management.md:58 — раздел 2, GET /api/cards: limit=50, offset=0 по умолчанию.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-05.
    // Граница покрытия: проверяется передача настройки, а не чтение окружения Docker Compose.
    @Test
    void usesConfiguredPaginationDefaults() {
        when(defaults.pageLimit()).thenReturn(50);
        when(defaults.pageOffset()).thenReturn(0L);
        when(cards.findCards(50, 0L, null, null, null, null, null)).thenReturn(List.of(card()));
        assertEquals(List.of(card()), service.getCards(null, null, null, null, null, null, null));
        verify(cards).findCards(50, 0L, null, null, null, null, null);
    }

    // Card Management: верхняя граница страницы допустима, фильтры сохраняются при передаче.
    // Требование: tz/05-card-management.md:58 — раздел 2, GET /api/cards: пагинация и фильтры.
    // Дополнительная проверка реализации: настроенный максимальный размер страницы включён в допустимый диапазон.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/services/CardServiceImpl.java:119.
    @Test
    void acceptsMaximumPageSizeAndPassesFilters() {
        when(settings.maxPageLimit()).thenReturn(100);
        when(cards.findCards(100, 5L, CardStatus.ACTIVE, "400000", "BANK001", null, null))
                .thenReturn(List.of(card()));
        assertEquals(List.of(card()), service.getCards(100, 5L, CardStatus.ACTIVE,
                "400000", "BANK001", null, null));
        verify(cards).findCards(100, 5L, CardStatus.ACTIVE, "400000", "BANK001", null, null);
    }

    // Card Management: превышение размера страницы отклоняется до обращения к БД.
    // Требование: tz/05-card-management.md:58 — раздел 2, GET /api/cards: пагинация.
    // Дополнительная проверка реализации: превышение настроенного maxPageLimit отклоняется до запроса БД.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/services/CardServiceImpl.java:119.
    @Test
    void rejectsPageAboveMaximum() {
        when(settings.maxPageLimit()).thenReturn(100);
        assertThrows(TooLargeLimitException.class, () -> service.getCards(101, null, null, null, null, null, null));
        verifyNoInteractions(cards);
    }

    // Card Management: PATCH сохраняет поля, отсутствующие в запросе, и публикует событие после сохранения.
    // Требование: tz/05-card-management.md:79 — раздел 2, PATCH: изменяются только переданные поля.
    // Требование: tz/05-card-management.md:216 — Outbox: событие изменения карты.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-10.
    @Test
    void patchesOnlySuppliedFields() {
        prepareTransaction();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card()));
        when(cards.update(any(Card.class))).thenAnswer(call -> call.getArgument(0));
        Card updated = service.patchCard(PAN, CardStatus.BLOCKED, null, null, null);
        assertEquals(CardStatus.BLOCKED, updated.status());
        assertEquals(card().dailyLimit(), updated.dailyLimit());
        assertEquals(card().monthlyLimit(), updated.monthlyLimit());
        assertEquals(card().availableBalance(), updated.availableBalance());
        var order = inOrder(cards, notifier);
        order.verify(cards).update(updated);
        order.verify(notifier).notifyListeners(new CardServicePatchEvent(PAN));
    }

    // Card Management: reserve сохраняет удержание, списывает точную сумму и только потом отправляет событие.
    // Требование: tz/05-card-management.md:134 — раздел 5, резервирование и сохранение баланса.
    // Требование: tz/05-card-management.md:216 — Outbox: событие изменения карты.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-15.
    @Test
    void reservesAndPublishesEventAfterSaving() {
        prepareTransaction();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card()));
        when(reservations.isUnique(RRN, PAN)).thenReturn(true);
        when(reservations.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));
        when(cards.update(any(Card.class))).thenAnswer(call -> call.getArgument(0));
        Card updated = service.reserve(PAN, AMOUNT, RRN);
        assertEquals(new BigDecimal("60"), updated.availableBalance());
        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        var order = inOrder(reservations, cards, notifier);
        order.verify(reservations).save(saved.capture());
        assertEquals(PAN, saved.getValue().pan());
        assertEquals(RRN, saved.getValue().rrn());
        assertEquals(AMOUNT, saved.getValue().reservationAmount());
        order.verify(cards).update(updated);
        order.verify(notifier).notifyListeners(new CardServiceReserveEvent(PAN, RRN, AMOUNT));
    }

    // Card Management: повторный RRN для той же карты не должен повторно списать баланс.
    // Требование идемпотентности: docs/api/openapi.yaml:1177 — ReserveRequest, поле rrn.
    @Test
    void rejectsDuplicateReservationWithoutSavingOrPublishing() {
        prepareTransaction();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card()));
        when(reservations.isUnique(RRN, PAN)).thenReturn(false);
        assertThrows(ReservationAlreadyExistsException.class, () -> service.reserve(PAN, AMOUNT, RRN));
        verify(reservations, never()).save(any());
        verify(cards, never()).update(any());
        verifyNoInteractions(notifier);
    }

    // Card Management: недостаток денег обнаруживается до сохранения удержания и изменения баланса.
    // Требование: tz/04-authorization.md:65 — раздел 2, шаг 6: недостаток средств.
    // Контракт CMS: services/card-management/README.md:281 — ошибка недостатка средств.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-16.
    @Test
    void rejectsInsufficientBalanceWithoutSideEffects() {
        prepareTransaction();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card()));
        assertThrows(InsufficientFundsException.class,
                () -> service.reserve(PAN, new BigDecimal("101"), RRN));
        verify(cards, never()).update(any());
        verifyNoInteractions(reservations, notifier);
    }

    // Card Management: ошибка сохранения удержания не должна приводить к обновлению карты или событию.
    // Требование: tz/05-card-management.md:134 — раздел 5, резервирование должно быть сохранено.
    // Требование: tz/05-card-management.md:216 — Outbox: событие связано с изменением карты.
    // Граница покрытия: запрет последующих вызовов после ошибки; атомарность БД здесь не проверяется.
    @Test
    void stopsReservationWhenPersistenceFails() {
        prepareTransaction();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card()));
        when(reservations.isUnique(RRN, PAN)).thenReturn(true);
        when(reservations.save(any())).thenThrow(new IllegalStateException("storage unavailable"));
        assertThrows(IllegalStateException.class, () -> service.reserve(PAN, AMOUNT, RRN));
        verify(cards, never()).update(any());
        verifyNoInteractions(notifier);
    }

    // Card Management: rollback помечает удержание возвращённым и возвращает деньги ровно один раз.
    // Требование: tz/04-authorization.md:126 — дополнительные задания, Reversal: возврат средств.
    // Контракт CMS: services/card-management/README.md:244 — POST /api/cards/{pan}/rollback.
    // Требование: tz/05-card-management.md:216 — Outbox: событие изменения карты.
    @Test
    void rollsBackReservationAndPublishesEventAfterSaving() {
        prepareTransaction();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card()));
        when(reservations.findByRrnAndPanForUpdate(RRN, PAN))
                .thenReturn(Optional.of(new Reservation(PAN, AMOUNT, RRN)));
        when(rollbacks.save(any(ReservationRollback.class))).thenAnswer(call -> call.getArgument(0));
        when(cards.update(any(Card.class))).thenAnswer(call -> call.getArgument(0));
        Card updated = service.rollback(PAN, AMOUNT, RRN);
        assertEquals(new BigDecimal("140"), updated.availableBalance());
        ArgumentCaptor<Reservation> saved = ArgumentCaptor.forClass(Reservation.class);
        var order = inOrder(rollbacks, reservations, cards, notifier);
        order.verify(rollbacks).save(any(ReservationRollback.class));
        order.verify(reservations).save(saved.capture());
        assertEquals(ReservationStatus.ROLLED_BACK, saved.getValue().status());
        order.verify(cards).update(updated);
        order.verify(notifier).notifyListeners(new CardServiceRollbackEvent(PAN, RRN, AMOUNT));
    }

    // Card Management: неизвестное удержание не создаёт возврат и не меняет баланс.
    // Требование: tz/04-authorization.md:126 — дополнительные задания, Reversal: поиск операции по RRN.
    // Дополнительная проверка реализации: отсутствующее удержание не создаёт возврат.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/services/CardServiceImpl.java:213.
    @Test
    void rejectsRollbackOfMissingReservation() {
        prepareTransaction();
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card()));
        when(reservations.findByRrnAndPanForUpdate(RRN, PAN)).thenReturn(Optional.empty());
        assertThrows(ReservationNotFoundException.class, () -> service.rollback(PAN, AMOUNT, RRN));
        verify(cards, never()).update(any());
        verifyNoInteractions(rollbacks, notifier);
    }

    // Card Management: уже возвращённое удержание не создаёт второе зачисление.
    // Требование идемпотентности: docs/api/openapi.yaml:1192 — RollbackRequest.
    @Test
    void rejectsRepeatedRollbackWithoutSideEffects() {
        prepareTransaction();
        Reservation reservation = new Reservation(PAN, AMOUNT, RRN);
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card()));
        when(reservations.findByRrnAndPanForUpdate(RRN, PAN))
                .thenReturn(Optional.of(reservation.rolledBack(reservation.startRollback(AMOUNT))));
        assertThrows(RollbackAlreadySatisfiedException.class, () -> service.rollback(PAN, AMOUNT, RRN));
        verify(cards, never()).update(any());
        verifyNoInteractions(rollbacks, notifier);
    }

    // Card Management: DELETE сохраняет DELETED и событие удаления, а не удаляет данные физически.
    // Требование: tz/05-card-management.md:83 — раздел 2, DELETE: мягкое удаление.
    // Требование: tz/05-card-management.md:216 — Outbox: событие удаления карты.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-12.
    @Test
    void softDeletesThroughRepository() {
        doAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return null;
        }).when(transactions).run(any(Runnable.class));
        when(cards.findByPanForUpdate(PAN)).thenReturn(Optional.of(card()));
        service.deleteCard(PAN);
        ArgumentCaptor<Card> saved = ArgumentCaptor.forClass(Card.class);
        var order = inOrder(cards, notifier);
        order.verify(cards).update(saved.capture());
        assertEquals(CardStatus.DELETED, saved.getValue().status());
        assertEquals(card().availableBalance(), saved.getValue().availableBalance());
        order.verify(notifier).notifyListeners(new CardServiceDeletionEvent(PAN));
    }

    // Это только выполнение callback: атомарность и rollback реальной БД проверяются в модуле 6.
    private void prepareTransaction() {
        when(transactions.runSupplier(any())).thenAnswer(call -> call.<Supplier<?>>getArgument(0).get());
    }

    private static Card card() {
        return new Card(UUID.fromString("00000000-0000-0000-0000-000000000001"), PAN, "400000", "IVAN IVANOV",
                YearMonth.of(2029, 10), CardStatus.ACTIVE, "643", new BigDecimal("1000"),
                new BigDecimal("30000"), new BigDecimal("100"), "BANK001", Instant.parse("2026-10-04T12:00:00Z"));
    }
}
