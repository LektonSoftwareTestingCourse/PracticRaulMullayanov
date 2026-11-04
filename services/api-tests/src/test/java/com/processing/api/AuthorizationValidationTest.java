package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Source: AuthorizationRequest/RollbackRequest DTOs; default Spring errors omit field details (report D04). */
class AuthorizationValidationTest extends ApiTestSupport {
    static Stream<Arguments> invalidAuthorizationFields() {
        List<Arguments> cases = new ArrayList<>();
        for (String field : List.of("mti", "stan", "pan", "processingCode", "currencyCode", "terminalId",
                "merchantId", "mcc", "acquirerId", "issuerId")) {
            for (Object value : new Object[]{null, "", " "}) {
                cases.add(Arguments.of(field, value));
            }
        }
        for (Map.Entry<String, Integer> field : Map.of("mti", 4, "stan", 6, "pan", 16, "processingCode", 6,
                "currencyCode", 3, "terminalId", 8, "merchantId", 15, "mcc", 4).entrySet()) {
            cases.add(Arguments.of(field.getKey(), "1".repeat(field.getValue() - 1)));
            cases.add(Arguments.of(field.getKey(), "1".repeat(field.getValue() + 1)));
        }
        cases.add(Arguments.of("amount", null));
        cases.add(Arguments.of("amount", -0.01));
        cases.add(Arguments.of("transmissionDateTime", null));
        cases.add(Arguments.of("transmissionDateTime", "bad-date"));
        return cases.stream();
    }

    @ParameterizedTest(name = "internal authorize {0} = [{1}]")
    @MethodSource("invalidAuthorizationFields")
    void invalidAuthorizePayload(String field, Object value) {
        // given
        Map<String, Object> request = TestData.authorization(TestData.missingPan("400000"));
        request.put(field, value);
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then — this service currently returns Spring's default envelope rather than ErrorResponse.
        response.then().statusCode(400).body("status", equalTo(400), "error", equalTo("Bad Request"),
                "path", equalTo("/api/internal/authorize"), "timestamp", notNullValue());
    }

    static Stream<Arguments> invalidRollbackFields() {
        List<Arguments> cases = new ArrayList<>();
        for (String field : List.of("rrn", "pan", "amount")) {
            cases.add(Arguments.of(field, null));
        }
        for (String rrn : List.of("", " ", "1".repeat(11), "1".repeat(13), "A".repeat(12))) {
            cases.add(Arguments.of("rrn", rrn));
        }
        for (String pan : List.of("", " ", "1".repeat(15), "1".repeat(17), "A".repeat(16))) {
            cases.add(Arguments.of("pan", pan));
        }
        cases.add(Arguments.of("amount", 0));
        cases.add(Arguments.of("amount", -1));
        return cases.stream();
    }

    @ParameterizedTest(name = "internal rollback {0} = [{1}]")
    @MethodSource("invalidRollbackFields")
    void invalidRollbackPayload(String field, Object value) {
        // given
        Map<String, Object> request = new java.util.LinkedHashMap<>(
                TestData.rollback(TestData.missingPan("400000"), TestData.rrn(), 1));
        request.put(field, value);
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/rollback");
        // then
        response.then().statusCode(400).body("status", equalTo(400), "error", equalTo("Bad Request"),
                "path", equalTo("/api/internal/rollback"), "timestamp", notNullValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null"})
    void invalidSwitchBody(String body) {
        // given
        var httpRequest = given().spec(Api.SWITCH).body(body);
        // when
        Response response = httpRequest.when().post("/api/internal/route");
        // then
        response.then().statusCode(400).body("status", equalTo(400), "error", equalTo("Bad Request"),
                "path", equalTo("/api/internal/route"));
    }
}
