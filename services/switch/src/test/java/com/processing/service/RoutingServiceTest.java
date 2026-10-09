package com.processing.service;

import com.processing.config.SwitchProperties;
import com.processing.exception.UnknownBinException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Практика 4: unit-тестирование СМП.
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Switch: BIN определяет эмитента до вызова Authorization.
 * Проверяем таблицу учебных BIN и отказ для неизвестного, отсутствующего или короткого PAN.
 */
class RoutingServiceTest {
    private final RoutingService routing = new RoutingService(new SwitchProperties("test",
            Map.of("400000", "BANK001", "400001", "BANK002", "400002", "BANK003",
                    "400003", "BANK004", "400004", "BANK005"), null, null, null, null, null, null));

    // Switch: каждый разрешённый BIN направляется к своему банку, оставшиеся цифры PAN не влияют на маршрут.
    // Требование: tz/03-switch.md:34 — раздел 2, шаги 1–2: BIN определяет issuerId.
    @ParameterizedTest
    @CsvSource({"400000, BANK001", "400001, BANK002", "400002, BANK003", "400003, BANK004", "400004, BANK005"})
    void resolvesIssuerFromFirstSixDigits(String bin, String issuer) {
        assertEquals(issuer, routing.getIssuerIdByPan(bin + "1234567890"));
    }

    // Switch: без шести цифр BIN невозможно выбрать маршрут, поэтому не возвращаем случайный issuerId.
    // Требование: tz/03-switch.md:34 — раздел 2, шаги 1–2: извлечение и поиск BIN.
    // Требование: tz/03-switch.md:110 — раздел 4, отказ при неизвестном BIN.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"4", "40000", "9999991234567890"})
    void rejectsPanWithoutKnownBin(String pan) {
        assertThrows(UnknownBinException.class, () -> routing.getIssuerIdByPan(pan));
    }

    // Switch: пустая таблица маршрутов не должна пропускать транзакцию к произвольному эмитенту.
    // Требование: tz/03-switch.md:110 — раздел 4, отказ при неизвестном BIN.
    @Test
    void rejectsWhenRoutingTableIsEmpty() {
        RoutingService empty = new RoutingService(
                new SwitchProperties("test", Map.of(), null, null, null, null, null, null));
        assertThrows(UnknownBinException.class, () -> empty.getIssuerIdByPan("4000001234567899"));
    }
}
