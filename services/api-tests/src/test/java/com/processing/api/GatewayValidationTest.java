package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/** Source: TransactionRequestValidator and TransactionValidationFilter (report D03/D04 for omitted constraints). */
class GatewayValidationTest extends ApiTestSupport {
    static Stream<Arguments> requiredFields() {
        List<Arguments> cases = new ArrayList<>();
        for (String field : List.of("mti", "stan", "pan", "processingCode", "currencyCode", "terminalId",
                "merchantId", "mcc", "acquirerId")) {
            for (Object value : new Object[]{null, "", " "}) {
                cases.add(Arguments.of(field, value));
            }
        }
        cases.add(Arguments.of("transmissionDateTime", null));
        cases.add(Arguments.of("amount", null));
        return cases.stream();
    }

    @ParameterizedTest(name = "required {0} = [{1}]")
    @MethodSource("requiredFields")
    void missingFieldsAreGatewayErrors(String field, Object value) {
        // given — one invalid field per invocation.
        Map<String, Object> request = TestData.authorization(TestData.missingPan("400000"));
        request.put(field, value);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then
        assertError(response, 400, "gateway", "Field '" + field + "' is required");
        response.then().body("error", equalTo("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @CsvSource({"mti,010, must be '0100'", "mti,01000, must be '0100'", "mti,0200, must be '0100'",
            "pan,400000123456789,exactly 16 digits", "pan,40000012345678901,exactly 16 digits",
            "pan,400000123456789A,exactly 16 digits", "currencyCode,64,exactly 3 characters",
            "currencyCode,6430,exactly 3 characters", "mcc,541,exactly 4 digits",
            "mcc,54111,exactly 4 digits", "mcc,54A1,exactly 4 digits"})
    void formatAndLengthBoundaries(String field, String value, String message) {
        // given
        Map<String, Object> request = TestData.authorization(TestData.missingPan("400000"));
        request.put(field, value);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then
        assertError(response, 400, "gateway", message.trim());
        response.then().body("message", containsString(field), "error", equalTo("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 0})
    void amountMustBePositive(double amount) {
        // given
        Map<String, Object> request = TestData.authorization(TestData.missingPan("400000"));
        request.put("amount", amount);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then
        assertError(response, 400, "gateway", "Field 'amount' must be > 0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "[]", "\"text\""})
    void unreadableJsonIsRejectedBeforeRouting(String body) {
        // given
        var httpRequest = given().spec(Api.GATEWAY).body(body);
        // when
        Response response = httpRequest.when().post("/api/transactions");
        // then
        assertError(response, 400, "gateway", "Request body must be valid JSON");
    }

    @Test
    void jsonNullBodyHasDedicatedError() {
        // given
        var httpRequest = given().spec(Api.GATEWAY).body("null");
        // when
        Response response = httpRequest.when().post("/api/transactions");
        // then
        assertError(response, 400, "gateway", "Request body is required");
    }

    @Test
    void malformedTimestampIsJsonMappingError() {
        // given
        Map<String, Object> request = TestData.authorization(TestData.missingPan("400000"));
        request.put("transmissionDateTime", "not-a-date");
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then
        assertError(response, 400, "gateway", "Request body must be valid JSON");
    }

    @Test
    void smallestPositiveAmountCanBeAuthorized() {
        // given
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        request.put("amount", 1);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then
        assertApproved(response, request.get("stan").toString());
        card(pan).body("availableBalance", equalTo(99999));
    }

    @Test
    void rejectedInputNeverBecomesSearchable() {
        // given
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        request.put("amount", 0);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then — continuous absence, not a single early empty read.
        assertError(response, 400, "gateway", "amount");
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(4))
                .pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
                    given().spec(Api.GATEWAY).queryParam("pan", pan).get("/api/transactions/search")
                            .then().statusCode(200).body("total", equalTo(0), "transactions", hasSize(0));
                    card(pan).body("availableBalance", equalTo(100000));
                });
    }

    @Test
    void rateLimitErrorIncludesRetryAfterAndRecovers() {
        // given — intentionally share one client bucket. Isolated Compose config: capacity 2, refill 1/sec.
        String client = "rate-" + TestData.token(12);
        AtomicReference<Response> limited = new AtomicReference<>();
        // when
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(10)).until(() -> {
            Response response = given().spec(Api.GATEWAY).header("X-Forwarded-For", client)
                    .body("{").post("/api/transactions");
            limited.set(response);
            return response.statusCode() == 429;
        });
        // then
        limited.get().then().statusCode(429).header("Retry-After", equalTo("1"))
                .body("error", equalTo("RATE_LIMIT_EXCEEDED"), "message", equalTo("Too many requests. Try again later."),
                        "retryAfterMs", equalTo(1000));
        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                given().spec(Api.GATEWAY).header("X-Forwarded-For", client).body("{").post("/api/transactions")
                        .then().statusCode(400).body("error", equalTo("VALIDATION_ERROR")));
    }
}
