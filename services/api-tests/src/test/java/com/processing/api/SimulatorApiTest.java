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
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Sources: simulator controllers and request DTOs; report D07 records the undocumented required terminal tps. */
class SimulatorApiTest extends ApiTestSupport {
    @ParameterizedTest
    @ValueSource(strings = {"normal", "mixed", "high_value", "night_time", "declines_test"})
    void terminalScenarios(String scenario) {
        // given — fixtures include both status groups required by mixed/declines_test.
        createCard();
        String blocked = createCard();
        patch(blocked, Map.of("status", "BLOCKED"));
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of("count", 1, "scenario", scenario, "tps", 1))
                .when().post("/api/simulator/terminal/run");
        // then
        assertSimulation(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"grocery", "electronics", "restaurant", "travel"})
    void merchantScenarios(String scenario) {
        // given
        createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of("count", 1, "scenario", scenario, "tps", 1))
                .when().post("/api/simulator/merchant/run");
        // then
        assertSimulation(response);
    }

    private void assertSimulation(Response response) {
        response.then().statusCode(200).body("totalSubmitted", equalTo(1), "transactions", hasSize(1),
                "transactions[0].status", oneOf("APPROVED", "DECLINED"),
                "transactions[0].stan", matchesPattern("[0-9]{6}"), "elapsedMs", greaterThanOrEqualTo(0));
        assertEquals(1, response.jsonPath().getInt("approved") + response.jsonPath().getInt("declined"));
    }

    static Stream<Arguments> invalidRequests() {
        List<Arguments> cases = new ArrayList<>();
        for (String simulator : List.of("terminal", "merchant")) {
            for (int value : new int[]{0, -1}) {
                cases.add(Arguments.of(simulator, "count", value));
                cases.add(Arguments.of(simulator, "tps", value));
            }
            cases.add(Arguments.of(simulator, "scenario", null));
            cases.add(Arguments.of(simulator, "scenario", "unknown"));
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} {1} = [{2}]")
    @MethodSource("invalidRequests")
    void simulatorInputValidation(String simulator, String field, Object value) {
        // given
        Map<String, Object> request = new java.util.LinkedHashMap<>(Map.of("count", 1, "tps", 1,
                "scenario", simulator.equals("terminal") ? "normal" : "grocery"));
        request.put(field, value);
        // when
        Response response = given().spec(Api.GATEWAY).body(request)
                .when().post("/api/simulator/{simulator}/run", simulator);
        // then
        String expectedMessage = "unknown".equals(value)
                ? (simulator.equals("terminal") ? "TerminalScenario" : "ScenarioType") : field;
        assertError(response, 400, simulator.equals("terminal") ? "terminal-simulator" : "Merchant acquirer simulator",
                expectedMessage);
    }

    @Test
    void continuousStartConflictAndStop() {
        // given
        createCard();
        Map<String, Object> request = Map.of("tps", 1, "transactionType", "NORMAL");
        // when
        try {
            Response first = given().spec(Api.GATEWAY).body(request).when().post("/api/simulator/terminal/start-continuous");
            Response second = given().spec(Api.GATEWAY).body(request).when().post("/api/simulator/terminal/start-continuous");
            // then — these control endpoints intentionally have no body.
            first.then().statusCode(200).body(isEmptyString());
            assertError(second, 422, "terminal-simulator", "already running");
        } finally {
            given().spec(Api.GATEWAY).post("/api/simulator/terminal/stop").then().statusCode(200).body(isEmptyString());
        }
    }

    @ParameterizedTest
    @CsvSource({"0,NORMAL,tps", "-1,NORMAL,tps", "1,UNKNOWN,TransactionType"})
    void invalidContinuousStart(int tps, String type, String message) {
        // given
        var httpRequest = given().spec(Api.GATEWAY).body(Map.of("tps", tps, "transactionType", type));
        // when
        Response response = httpRequest.when().post("/api/simulator/terminal/start-continuous");
        // then
        assertError(response, 400, "terminal-simulator", message);
    }

    @Test
    void merchantListAndUnknownFee() {
        // given
        Map<String, Object> request = TestData.authorization(TestData.missingPan("400000"));
        // when
        Response list = given().spec(Api.MERCHANT).get("/api/simulator/merchants");
        Response fee = given().spec(Api.GATEWAY).body(request).when().post("/api/simulator/merchant/fee");
        // then — list is internal-only because of the missing Gateway route (D06).
        list.then().statusCode(200).body("size()", greaterThanOrEqualTo(1), "id", everyItem(matchesPattern("MERCH[0-9]{10}")),
                "mcc", everyItem(matchesPattern("[0-9]{4}")));
        assertError(fee, 404, "Merchant acquirer simulator", "fee");
    }

    @Test
    void merchantMccOverrideCanHaveNoMatches() {
        // given
        createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of("count", 1, "scenario", "grocery",
                "mccCodes", List.of("0000"))).when().post("/api/simulator/merchant/run");
        // then
        assertError(response, 404, "Merchant acquirer simulator", "mcc");
    }

    @Test
    void generatedMerchantTransactionHasFee() {
        // given
        String pan = createCard();
        Response run = given().spec(Api.GATEWAY).body(Map.of("count", 1, "scenario", "grocery", "tps", 1))
                .post("/api/simulator/merchant/run");
        assertSimulation(run);
        String stan = run.path("transactions[0].stan");
        String authCode = run.path("transactions[0].authCode");
        // when — locate the generated transaction through HTTP; no simulator database access.
        var record = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            Response search = given().spec(Api.GATEWAY).queryParams("limit", 500, "pan", pan).get("/api/transactions/search");
            search.then().statusCode(200).body("transactions.findAll { it.stan == '" + stan + "' }", hasSize(1));
            record.set(search.jsonPath().getMap("transactions.find { it.stan == '" + stan + "' }"));
        });
        Response fee = given().spec(Api.GATEWAY).body(record.get()).when().post("/api/simulator/merchant/fee");
        // then
        fee.then().statusCode(200).body("acquirerFee", notNullValue());
        assertEquals(authCode, record.get().get("authCode"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"transmissionDateTime", "pan", "stan", "terminalId"})
    void missingFeeLookupFields(String field) {
        // given
        Map<String, Object> request = TestData.authorization(TestData.missingPan("400000"));
        request.put(field, null);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/simulator/merchant/fee");
        // then
        assertError(response, 400, "Merchant acquirer simulator", field);
    }
}
