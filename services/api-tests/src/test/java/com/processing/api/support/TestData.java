package com.processing.api.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/** Independent, API-only fixtures. Amounts are in minor currency units, as in docs/api-spec.md. */
public final class TestData {
    private static final AtomicInteger STAN = new AtomicInteger(ThreadLocalRandom.current().nextInt(900000));

    private TestData() { }

    public static String token(int length) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, length).toUpperCase();
    }

    public static String rrn() {
        return String.format("%012d", ThreadLocalRandom.current().nextLong(1_000_000_000_000L));
    }

    public static String missingPan(String bin) {
        return bin + String.format("%010d", ThreadLocalRandom.current().nextLong(10_000_000_000L));
    }

    public static Map<String, Object> card(String bin) {
        return new LinkedHashMap<>(Map.of("bin", bin, "cardholderName", "RAUL API TEST",
                "currencyCode", "643", "dailyLimit", 10000, "monthlyLimit", 100000, "initialBalance", 100000));
    }

    public static Map<String, Object> authorization(String pan) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("mti", "0100");
        request.put("stan", String.format("%06d", STAN.getAndIncrement() % 1_000_000));
        request.put("pan", pan);
        request.put("processingCode", "000000");
        request.put("amount", 100);
        request.put("currencyCode", "643");
        request.put("transmissionDateTime", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString());
        request.put("terminalId", "T" + token(7));
        request.put("terminalType", "POS");
        request.put("merchantId", "M" + token(14));
        request.put("mcc", "5411");
        request.put("acquirerId", "ACQ001");
        request.put("issuerId", "ISS001");
        return request;
    }

    public static Map<String, Object> transaction() {
        Map<String, Object> request = authorization(missingPan("400000"));
        request.put("id", UUID.randomUUID().toString());
        request.put("rrn", rrn());
        request.put("status", "APPROVED");
        request.put("authCode", token(6));
        request.put("processingTimeMs", 1);
        request.put("acquiringFee", new BigDecimal("1.50"));
        request.put("declineReason", null);
        request.put("createdAt", Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString());
        return request;
    }

    public static Map<String, Object> rollback(String pan, String rrn, Object amount) {
        return Map.of("pan", pan, "rrn", rrn, "amount", amount);
    }
}
