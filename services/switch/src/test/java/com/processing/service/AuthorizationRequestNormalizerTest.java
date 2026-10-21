package com.processing.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthorizationRequestNormalizerTest {
    @ParameterizedTest
    @MethodSource("terminalIds")
    void shouldNormalizeTerminalIdWhenLengthVaries(String input, String expected) {
        // Arrange: идентификатор и ожидание заданы параметрами.

        // Act
        String actual = AuthorizationRequestNormalizer.normalizeTerminalId(input);

        // Assert
        assertEquals(expected, actual);
    }

    @ParameterizedTest
    @MethodSource("merchantIds")
    void shouldNormalizeMerchantIdWhenFormatVaries(String input, String expected) {
        // Arrange: идентификатор и ожидание заданы параметрами.

        // Act
        String actual = AuthorizationRequestNormalizer.normalizeMerchantId(input);

        // Assert
        assertEquals(expected, actual);
    }

    static Stream<Arguments> terminalIds() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", ""),
                Arguments.of(" ", " "),
                Arguments.of("TERM", "TERM0000"),
                Arguments.of("TERM0001", "TERM0001"),
                Arguments.of("TERM000123", "TERM0001")
        );
    }

    static Stream<Arguments> merchantIds() {
        return Stream.of(
                Arguments.of(null, null),
                Arguments.of("", ""),
                Arguments.of(" ", " "),
                Arguments.of("SHOP", "SHOP00000000000"),
                Arguments.of("MERCH0000000001", "MERCH0000000001"),
                Arguments.of("MERCH00000000001", "MERCH0000000001"),
                Arguments.of("SHOP123456789012", "SHOP12345678901"),
                Arguments.of("MERCHX1234567890", "MERCHX123456789"),
                Arguments.of("MERCH1234567890123", "MERCH1234567890")
        );
    }
}
