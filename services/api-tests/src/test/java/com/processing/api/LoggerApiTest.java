package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Sources: InternalTransactionController, TransactionRequest, TransactionMapper.matches. */
class LoggerApiTest extends ApiTestSupport {
    static void store(Map<String, Object> request) {
        given().spec(Api.LOGGER).body(request).post("/api/internal/log").then().statusCode(201)
                .body("id", equalTo(request.get("id")), "status", equalTo("stored"));
    }

    @Test
    void sameIdAndSamePayloadAreIdempotent() {
        // given
        Map<String, Object> request = TestData.transaction();
        store(request);
        // when
        Response duplicate = given().spec(Api.LOGGER).body(request).when().post("/api/internal/log");
        // then — both status and persisted cardinality detect a disabled deduplication guard.
        duplicate.then().statusCode(200).body("id", equalTo(request.get("id")), "pan", equalTo(request.get("pan")),
                "stan", equalTo(request.get("stan")), "status", equalTo("APPROVED"), "amount", equalTo(100));
        given().spec(Api.GATEWAY).queryParam("pan", request.get("pan")).get("/api/transactions/search")
                .then().statusCode(200).body("total", equalTo(1), "transactions", hasSize(1),
                        "transactions[0].id", equalTo(request.get("id")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mti", "stan", "rrn", "pan", "processingCode", "amount", "currencyCode", "terminalId",
            "terminalType", "merchantId", "mcc", "acquirerId", "issuerId", "acquiringFee", "status",
            "declineReason", "authCode", "processingTimeMs", "transmissionDateTime"})
    void sameIdAndDifferentBusinessFieldAreConflict(String field) {
        // given — vary exactly one field used by TransactionMapper.matches.
        Map<String, Object> request = TestData.transaction();
        store(request);
        Map<String, Object> changed = new LinkedHashMap<>(request);
        Object value = switch (field) {
            case "mti" -> "0110";
            case "stan" -> "999999".equals(request.get("stan")) ? "999998" : "999999";
            case "rrn" -> TestData.rrn();
            case "pan" -> TestData.missingPan("400001");
            case "processingCode" -> "200000";
            case "amount" -> 101;
            case "currencyCode" -> "840";
            case "terminalId" -> "DIFFTERM";
            case "terminalType" -> "ECOM";
            case "merchantId" -> "DIFFMERCHANT001";
            case "mcc" -> "5812";
            case "acquirerId" -> "ACQ002";
            case "issuerId" -> "ISS002";
            case "acquiringFee" -> 2;
            case "status" -> "DECLINED";
            case "declineReason" -> "CARD_BLOCKED";
            case "authCode" -> "OTHER1";
            case "processingTimeMs" -> 2;
            default -> Instant.parse(request.get(field).toString()).plusSeconds(1).toString();
        };
        changed.put(field, value);
        // when
        Response response = given().spec(Api.LOGGER).body(changed).when().post("/api/internal/log");
        // then
        assertError(response, 409, "transaction-logger", request.get("id").toString());
        response.then().body("error", equalTo("Transaction conflict"), "retryAfterMs", equalTo("0"));
        given().spec(Api.GATEWAY).queryParam("pan", request.get("pan")).get("/api/transactions/search")
                .then().statusCode(200).body("total", equalTo(1), "transactions[0].amount", equalTo(100));
    }

    @Test
    void differentDecimalScaleAndCreatedAtDoNotCreateConflict() {
        // given — TransactionMapper intentionally ignores createdAt and compares decimal values numerically.
        Map<String, Object> request = TestData.transaction();
        store(request);
        Map<String, Object> replay = new LinkedHashMap<>(request);
        replay.put("amount", new BigDecimal("100.000"));
        replay.put("createdAt", Instant.now().plusSeconds(1).toString());
        // when
        Response response = given().spec(Api.LOGGER).body(replay).when().post("/api/internal/log");
        // then
        response.then().statusCode(200).body("id", equalTo(request.get("id")), "amount", equalTo(100));
    }

    @Test
    void optionalFieldsCanBeExplicitlyNull() {
        // given
        Map<String, Object> request = TestData.transaction();
        for (String key : List.of("rrn", "terminalType", "issuerId", "acquiringFee", "declineReason", "authCode",
                "processingTimeMs")) {
            request.put(key, null);
        }
        // when
        store(request);
        Response response = given().spec(Api.GATEWAY).queryParam("pan", request.get("pan"))
                .when().get("/api/transactions/search");
        // then
        response.then().statusCode(200).body("transactions", hasSize(1), "transactions[0].rrn", nullValue(),
                "transactions[0].authCode", nullValue(), "transactions[0].declineReason", nullValue(),
                "transactions[0].acquiringFee", nullValue());
    }

    static Stream<Arguments> invalidFields() {
        List<Arguments> cases = new ArrayList<>();
        for (String key : List.of("id", "mti", "stan", "pan", "processingCode", "amount", "currencyCode",
                "terminalId", "merchantId", "mcc", "acquirerId", "status", "transmissionDateTime", "createdAt")) {
            cases.add(Arguments.of(key, null));
        }
        for (String key : List.of("mti", "stan", "pan", "processingCode", "currencyCode", "terminalId",
                "merchantId", "mcc", "acquirerId")) {
            cases.add(Arguments.of(key, " "));
        }
        for (Map.Entry<String, Integer> entry : Map.of("mti", 4, "rrn", 12, "terminalId", 8, "terminalType", 10,
                "merchantId", 15, "acquirerId", 10, "issuerId", 10, "declineReason", 100, "authCode", 6).entrySet()) {
            cases.add(Arguments.of(entry.getKey(), "A".repeat(entry.getValue() + 1)));
        }
        for (Map.Entry<String, Integer> entry : Map.of("stan", 6, "pan", 16, "processingCode", 6,
                "currencyCode", 3, "mcc", 4).entrySet()) {
            int length = entry.getValue();
            cases.add(Arguments.of(entry.getKey(), "1".repeat(length - 1)));
            cases.add(Arguments.of(entry.getKey(), "1".repeat(length + 1)));
            cases.add(Arguments.of(entry.getKey(), "A".repeat(length)));
        }
        cases.add(Arguments.of("amount", 0));
        cases.add(Arguments.of("amount", -0.01));
        cases.add(Arguments.of("acquiringFee", -0.01));
        cases.add(Arguments.of("processingTimeMs", -1));
        return cases.stream();
    }

    @ParameterizedTest(name = "log {0} = [{1}]")
    @MethodSource("invalidFields")
    void invalidLogPayloadIdentifiesItsField(String field, Object value) {
        // given
        Map<String, Object> request = TestData.transaction();
        request.put(field, value);
        // when
        Response response = given().spec(Api.LOGGER).body(request).when().post("/api/internal/log");
        // then
        assertError(response, 400, "transaction-logger", field);
        response.then().body("error", equalTo("Validation error"), "retryAfterMs", equalTo("0"));
        given().spec(Api.GATEWAY).queryParam("pan", request.get("pan") == null ? "absent" : request.get("pan"))
                .get("/api/transactions/search").then().statusCode(200).body("transactions", hasSize(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "{\"id\":\"not-a-uuid\"}", "{\"status\":\"UNKNOWN\"}"})
    void malformedLogPayload(String body) {
        // given
        var httpRequest = given().spec(Api.LOGGER).body(body);
        // when
        Response response = httpRequest.when().post("/api/internal/log");
        // then
        assertError(response, 400, "transaction-logger", "malformed or contains unsupported values");
        response.then().body("error", equalTo("Invalid request body"));
    }

    @Test
    void unsupportedLogMethodHasStructuredError() {
        // given
        var httpRequest = given().spec(Api.LOGGER);
        // when
        Response response = httpRequest.when().get("/api/internal/log");
        // then
        assertError(response, 405, "transaction-logger", "not supported");
        response.then().body("error", equalTo("Method not allowed"));
    }
}
