package com.processing.cardmanagement.models;

import com.processing.cardmanagement.exceptions.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Card Management: денежные операции и изменения карты проверяются без БД.
 * Покрываем границу баланса, запрет операций для неактивных карт, лимиты и мягкое удаление.
 */
class CardTest {
    private static final String PAN = "4000001234567899";
    private static final String RRN = "627712000001";
    private final Card card = new Card(UUID.fromString("00000000-0000-0000-0000-000000000001"),
            PAN, "400000", "IVAN IVANOV", YearMonth.of(2029, 10), CardStatus.ACTIVE, "643",
            new BigDecimal("1000"), new BigDecimal("30000"), new BigDecimal("100"), "BANK001",
            Instant.parse("2026-10-04T12:00:00Z"));

    // Card Management: покупка ниже баланса и ровно на весь баланс допустима, сумма списывается точно.
    // Требование: tz/05-card-management.md:134 — раздел 5, уменьшение availableBalance на сумму reserve.
    // Требование: tz/04-authorization.md:65 — раздел 2, шаг 6: amount <= availableBalance.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-15.
    @ParameterizedTest
    @CsvSource({"99, 1", "100, 0"})
    void reservesUpToAvailableBalance(String amount, String remaining) {
        Reservation reservation = card.startReservation(new BigDecimal(amount), RRN);
        Card updated = card.withReservation(reservation);
        assertAll(
                () -> assertEquals(0, updated.availableBalance().compareTo(new BigDecimal(remaining))),
                () -> assertEquals(PAN, reservation.pan()),
                () -> assertEquals(RRN, reservation.rrn()),
                () -> assertEquals(ReservationStatus.RESERVED, reservation.status()),
                () -> assertEquals(card.dailyLimit(), updated.dailyLimit()),
                () -> assertEquals(card.monthlyLimit(), updated.monthlyLimit()),
                () -> assertEquals(card.id(), updated.id()),
                () -> assertEquals(card.createdAt(), updated.createdAt()),
                () -> assertEquals(new BigDecimal("100"), card.availableBalance())
        );
    }

    // Card Management: превышение баланса даже на одну копейку не создаёт удержание.
    // Требование: tz/04-authorization.md:65 — раздел 2, шаг 6: запрет покупки при недостатке средств.
    // Контракт CMS: services/card-management/README.md:281 — ошибка недостатка средств.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-16.
    @Test
    void rejectsAmountAboveBalance() {
        assertThrows(InsufficientFundsException.class,
                () -> card.startReservation(new BigDecimal("101"), RRN));
        assertEquals(new BigDecimal("100"), card.availableBalance());
    }

    // Card Management: все статусы кроме ACTIVE запрещают резервирование.
    // Требование: tz/04-authorization.md:49 — раздел 2, шаг 2: неактивная карта не участвует в авторизации.
    // Требование: tz/05-card-management.md:83 — раздел 2, DELETE: удалённая карта не участвует в транзакциях.
    // Граница покрытия: здесь проверяется защита модели CMS; ответ Authorization проверяется отдельно.
    @ParameterizedTest
    @EnumSource(value = CardStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void rejectsReservationForNonActiveCards(CardStatus status) {
        Card inactive = card.withData(status, card.dailyLimit(), card.monthlyLimit(), card.availableBalance());
        assertThrows(IllegalStateException.class, () -> inactive.startReservation(BigDecimal.ONE, RRN));
    }

    // Card Management: возврат увеличивает баланс на сумму удержания, исходная карта остаётся неизменной.
    // Требование: tz/04-authorization.md:126 — дополнительные задания, Reversal: возврат средств.
    // Контракт CMS: services/card-management/README.md:244 — POST /api/cards/{pan}/rollback.
    @Test
    void restoresReservedBalance() {
        Reservation reservation = card.startReservation(new BigDecimal("40"), RRN);
        Card reserved = card.withReservation(reservation);
        Card restored = reserved.withRollback(reservation.startRollback(new BigDecimal("40")));
        assertEquals(card, restored);
        assertEquals(new BigDecimal("60"), reserved.availableBalance());
    }

    // Card Management: чужое удержание или возврат нельзя применить к балансу другой карты.
    // Контракт привязки операции к карте: docs/api/openapi.yaml:1192 — PAN в RollbackRequest.
    // Дополнительная проверка реализации: удержание и возврат нельзя применить к чужому PAN.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/models/Card.java:115.
    @Test
    void rejectsMoneyOperationsForDifferentPan() {
        Reservation foreign = new Reservation("4000011234567899", BigDecimal.ONE, RRN);
        assertThrows(IllegalArgumentException.class, () -> card.withReservation(foreign));
        assertThrows(IllegalArgumentException.class, () -> card.withRollback(foreign.startRollback(BigDecimal.ONE)));
    }

    // Card Management: отрицательные лимиты и monthlyLimit < dailyLimit противоречат правилам карты.
    // Требование: tz/05-card-management.md:79 — раздел 2, PATCH: изменение лимитов.
    // Дополнительная проверка реализации: лимиты неотрицательны, monthlyLimit >= dailyLimit.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/models/Card.java:199.
    @ParameterizedTest
    @CsvSource({"-1, 1000", "1000, -1", "1001, 1000"})
    void rejectsInvalidLimits(String daily, String monthly) {
        assertThrows(IllegalArgumentException.class, () -> card.withData(CardStatus.ACTIVE,
                new BigDecimal(daily), new BigDecimal(monthly), card.availableBalance()));
    }

    // Card Management: нулевые и равные лимиты допустимы; проверяем включённую границу сравнения.
    // Требование: tz/05-card-management.md:79 — раздел 2, PATCH: изменение лимитов.
    // Дополнительная проверка реализации: нулевые и равные лимиты разрешены.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/models/Card.java:199.
    @ParameterizedTest
    @ValueSource(strings = {"0", "1000"})
    void acceptsEqualNonNegativeLimits(String limit) {
        Card updated = card.withData(CardStatus.BLOCKED, new BigDecimal(limit), new BigDecimal(limit),
                card.availableBalance());
        assertEquals(new BigDecimal(limit), updated.dailyLimit());
        assertEquals(new BigDecimal(limit), updated.monthlyLimit());
        assertEquals(CardStatus.BLOCKED, updated.status());
    }

    // Card Management: мягкое удаление меняет только статус, повторное удаление не допускается.
    // Требование: tz/05-card-management.md:83 — раздел 2, DELETE: мягкое удаление со статусом DELETED.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-12.
    // Дополнительная проверка реализации: повторное удаление отклоняется.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/models/Card.java:168.
    @Test
    void softDeletesCardAndRejectsRepeatedDeletion() {
        Card deleted = card.deleted();
        assertEquals(CardStatus.DELETED, deleted.status());
        assertEquals(card.id(), deleted.id());
        assertEquals(card.availableBalance(), deleted.availableBalance());
        assertEquals(CardStatus.ACTIVE, card.status());
        assertThrows(IllegalStateException.class, deleted::deleted);
    }
}
