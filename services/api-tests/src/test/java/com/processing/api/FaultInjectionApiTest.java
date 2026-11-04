package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.ComposeFault;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

/** Observable HTTP failure contracts. Infrastructure is only stopped/restored; assertions remain API-only. */
@Tag("fault")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FaultInjectionApiTest extends ApiTestSupport {
    private void unavailable(Response response, String service) {
        response.then().statusCode(503).body("error", equalTo("SERVICE_UNAVAILABLE"), "serviceName", equalTo(service),
                "message", containsString("temporarily unavailable"));
    }

    @Test
    @Order(2)
    void gatewayIdentifiesUnavailableLogger() {
        // given
        List<String> services = List.of("transaction-logger");
        // when
        ComposeFault.withStopped(services, () -> {
            // then
            for (String path : List.of("/api/transactions/search", "/api/transactions/export", "/api/dashboard/stats",
                    "/api/dashboard/recent", "/api/dashboard/charts")) {
                unavailable(given().spec(Api.GATEWAY).get(path), "logger");
            }
        });
    }

    @Test
    @Order(3)
    void missingCardsServiceIsIdentifiedAcrossConsumers() {
        // given
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        String rrn = TestData.rrn();
        // when
        ComposeFault.withStopped(List.of("card-management"), () -> {
            // then — Gateway and Authorization expose different, well-defined failure envelopes.
            unavailable(given().spec(Api.GATEWAY).queryParam("isolated", TestData.token(8)).get("/api/cards"), "cardManagement");
            unavailable(given().spec(Api.GATEWAY).get("/api/cards/{pan}", pan), "cardManagement");
            unavailable(given().spec(Api.GATEWAY).body(TestData.card("400000")).post("/api/cards"), "cardManagement");
            unavailable(given().spec(Api.GATEWAY).body(Map.of()).patch("/api/cards/{pan}", pan), "cardManagement");
            unavailable(given().spec(Api.GATEWAY).delete("/api/cards/{pan}", pan), "cardManagement");
            assertDecline(given().spec(Api.AUTH).body(request).post("/api/internal/authorize"), 503,
                    request.get("stan").toString(), "96", "SERVICE_UNAVAILABLE");
            given().spec(Api.AUTH).body(TestData.rollback(pan, rrn, 1)).post("/api/internal/rollback")
                    .then().statusCode(503).body("rrn", equalTo(rrn), "status", equalTo("DECLINED"),
                            "responseCode", equalTo("96"), "declineReason", equalTo("SERVICE_UNAVAILABLE"));
            Response merchant = given().spec(Api.MERCHANT).body(Map.of("count", 1, "scenario", "grocery"))
                    .post("/api/simulator/merchant/run");
            merchant.then().statusCode(502).body("error", equalTo("External service error"),
                    "serviceName", equalTo("cardManagement"), "message", not(isEmptyOrNullString()));
        });
        card(pan).body("availableBalance", equalTo(100000));
    }

    @Test
    @Order(4)
    void gatewayIdentifiesUnavailableSwitch() {
        // given
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        // when
        ComposeFault.withStopped(List.of("switch"), () -> {
            // then
            unavailable(given().spec(Api.GATEWAY).body(request).post("/api/transactions"), "switch");
        });
        card(pan).body("availableBalance", equalTo(100000));
    }

    @Test
    @Order(5)
    void gatewayIdentifiesUnavailableSimulators() {
        // given
        List<String> services = List.of("terminal-simulator", "merchant-acquirer");
        // when
        ComposeFault.withStopped(services, () -> {
            // then
            unavailable(given().spec(Api.GATEWAY).body(Map.of("count", 1, "scenario", "normal", "tps", 1))
                    .post("/api/simulator/terminal/run"), "terminalSimulator");
            unavailable(given().spec(Api.GATEWAY).body(Map.of("count", 1, "scenario", "grocery"))
                    .post("/api/simulator/merchant/run"), "merchantSimulator");
        });
    }

    @Test
    @Order(1)
    void unavailableAuthorizationIsADistinctSwitchOutcome() {
        // given
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        // when
        ComposeFault.withStopped(List.of("authorization"), () -> {
            // then — this string originates in Switch, unlike Authorization's SERVICE_UNAVAILABLE/96.
            assertDecline(given().spec(Api.SWITCH).body(request).post("/api/internal/route"), 200,
                    request.get("stan").toString(), "05", "Authorization service unavailable");
        });
        card(pan).body("availableBalance", equalTo(100000));
        awaitTransaction(pan, request.get("stan").toString());
    }

    @Test
    @Order(6)
    void failedPublishRollsBackAndNeverAppearsInSearchEvenAfterRecovery() {
        // given — RouteService explicitly compensates an approved authorization after publish failure.
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        // when
        ComposeFault.withStopped(List.of("rabbitmq"), () -> {
            Response response = given().spec(Api.GATEWAY).body(request).post("/api/transactions");
            // then
            assertDecline(response, 200, request.get("stan").toString(), "96", "System error");
            card(pan).body("availableBalance", equalTo(100000));
            assertAbsentDuringWindow(pan);
        });
        // Observation continues after recovery so a delayed delivery cannot produce a false pass.
        assertAbsentDuringWindow(pan);
        card(pan).body("availableBalance", equalTo(100000));
    }

    private void assertAbsentDuringWindow(String pan) {
        await().alias("no stored transaction after rollback")
                .during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> given().spec(Api.GATEWAY).queryParam("pan", pan).get("/api/transactions/search")
                        .then().statusCode(200).body("total", equalTo(0), "transactions", hasSize(0)));
    }
}
