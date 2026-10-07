package com.processing.cardmanagement.services;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ссылки на файлы ниже указаны относительно корня репозитория.
 * Unit-тесты Card Management: PAN тестовых карт должен иметь правильную контрольную цифру.
 * Покрываем известные примеры Луна и свойства генерации для каждого BIN учебной системы.
 */
class LuhnValidatorTest {
    private final LuhnValidator validator = new LuhnValidator();

    // Card Management: эталонные номера и номера с изменённой контрольной цифрой проверяют обе ветви.
    // Требование: tz/05-card-management.md:108 — раздел 4, алгоритм Луна.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-01.
    @ParameterizedTest
    @CsvSource({
            "4000001234567899, true", "4000001234567898, false",
            "4532015112830366, true", "4532015112830367, false",
            "5555555555554444, true", "5555555555554445, false"
    })
    void checksKnownPanExamples(String pan, boolean valid) {
        assertEquals(valid, validator.isValid(pan));
    }

    // Card Management: случайность влияет на цифры, но не на длину, BIN или независимый checksum.
    // Требование: tz/05-card-management.md:32 — раздел 2, создание карты: BIN и PAN из 16 цифр.
    // Требование: tz/05-card-management.md:108 — раздел 4, контрольная цифра PAN.
    // Связанные тест-кейсы: docs/practice-2/test-design.md, раздел 6.2: CMS-01.
    @ParameterizedTest
    @ValueSource(strings = {"400000", "400001", "400002", "400003", "400004"})
    void generatesSixteenDigitPanWithRequestedBinAndValidChecksum(String bin) {
        String pan = validator.generatePan(bin);
        assertTrue(pan.matches("[0-9]{16}"), pan);
        assertTrue(pan.startsWith(bin), pan);
        // Независимый расчёт: проверка генератора не должна полагаться на его же isValid().
        int sum = 0;
        for (int i = 0; i < pan.length(); i++) {
            int digit = Character.digit(pan.charAt(i), 10);
            int weighted = i % 2 == 0 ? digit * 2 : digit;
            sum += weighted / 10 + weighted % 10;
        }
        assertEquals(0, sum % 10, pan);
    }
}
