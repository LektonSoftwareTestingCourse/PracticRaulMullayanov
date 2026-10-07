package com.processing.cardmanagement.models;

import com.processing.cardmanagement.exceptions.RollbackAlreadySatisfiedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Card Management: жизненный цикл удержания защищает от повторного возврата денег.
 * Покрываем связь возврата с удержанием и переход RESERVED → ROLLED_BACK без внешних зависимостей.
 */
class ReservationTest {
    private final Reservation reservation = new Reservation("4000001234567899", new BigDecimal("40"), "627712000001");

    // Card Management: возврат ссылается на исходное удержание и переводит его в конечный статус.
    // Требование: tz/04-authorization.md:126 — дополнительные задания, Reversal: поиск по RRN и возврат.
    // Контракт CMS: services/card-management/README.md:244 — POST /api/cards/{pan}/rollback.
    // Дополнительная проверка реализации: переход состояния удержания RESERVED → ROLLED_BACK.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/models/Reservation.java:70.
    @Test
    void linksRollbackToReservationAndMarksItRolledBack() {
        ReservationRollback rollback = reservation.startRollback(new BigDecimal("40"));
        assertEquals(reservation.id(), rollback.reservationId());
        assertEquals(reservation.pan(), rollback.pan());
        assertEquals(reservation.rrn(), rollback.rrn());
        assertEquals(new BigDecimal("40"), rollback.rollbackAmount());
        Reservation updated = reservation.rolledBack(rollback);
        assertEquals(ReservationStatus.ROLLED_BACK, updated.status());
        assertEquals(reservation.id(), updated.id());
        assertEquals(ReservationStatus.RESERVED, reservation.status());
    }

    // Card Management: повторный возврат не должен увеличивать баланс второй раз.
    // Требование идемпотентности: docs/api/openapi.yaml:1192 — RollbackRequest.
    // Дополнительная проверка реализации: повторный возврат уже возвращённого удержания отклоняется.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/models/Reservation.java:49.
    @Test
    void rejectsSecondRollback() {
        Reservation updated = reservation.rolledBack(reservation.startRollback(new BigDecimal("40")));
        assertThrows(RollbackAlreadySatisfiedException.class, () -> updated.startRollback(new BigDecimal("40")));
    }

    // Card Management: возврат от другого удержания не может изменить состояние текущего.
    // Контракт операции возврата: docs/api/openapi.yaml:1192 — RollbackRequest.
    // Дополнительная проверка реализации: возврат должен ссылаться на текущее удержание.
    // Основание: services/card-management/src/main/java/com/processing/cardmanagement/models/Reservation.java:70.
    @Test
    void rejectsRollbackForAnotherReservation() {
        ReservationRollback foreign = new ReservationRollback(UUID.randomUUID(), UUID.randomUUID(),
                reservation.pan(), BigDecimal.ONE, reservation.rrn(), Instant.parse("2026-10-04T12:00:00Z"));
        assertThrows(IllegalArgumentException.class, () -> reservation.rolledBack(foreign));
    }
}
