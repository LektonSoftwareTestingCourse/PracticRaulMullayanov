package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Real Authorization HTTP boundary with selectively injected upstream responses, without DB/AMQP access. */
@Tag("fault")
class DependencyFaultApiTest extends ApiTestSupport {
    private final List<String> rules = new ArrayList<>();

    private void inject(String method, String path, int status, Object body) {
        String identifier = TestData.token(16);
        Map<String, Object> rule = new java.util.LinkedHashMap<>(Map.of("method", method, "path", path,
                "status", status, "times", 1));
        rule.put("body", body);
        given().spec(Api.CARD_PROXY).body(rule).post("/__faults/{id}", identifier)
                .then().statusCode(201).body("id", equalTo(identifier));
        rules.add(identifier);
    }

    @AfterEach
    void removeFaults() {
        for (String rule : rules) {
            given().spec(Api.CARD_PROXY).delete("/__faults/{id}", rule).then().statusCode(204);
        }
    }

    @ParameterizedTest
    @CsvSource({"404,404,14,CARD_NOT_FOUND", "400,404,14,CARD_NOT_FOUND", "402,403,05,CARD_BLOCKED",
            "503,503,96,SERVICE_UNAVAILABLE", "500,503,96,SERVICE_UNAVAILABLE", "409,503,96,SERVICE_UNAVAILABLE"})
    void getCardFailurePaths(int upstreamStatus, int status, String code, String reason) {
        // given — the card is ACTIVE; the response code comes from the upstream HTTP error path.
        String pan = createCard();
        inject("GET", "/api/cards/" + pan, upstreamStatus, Map.of("error", "injected upstream error"));
        Map<String, Object> request = TestData.authorization(pan);
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then
        assertDecline(response, status, request.get("stan").toString(), code, reason);
        card(pan).body("status", equalTo("ACTIVE"), "availableBalance", equalTo(100000));
    }

    @ParameterizedTest
    @CsvSource({"404,404,14,CARD_NOT_FOUND", "400,404,14,CARD_NOT_FOUND", "402,422,51,INSUFFICIENT_FUNDS",
            "503,503,96,SERVICE_UNAVAILABLE", "500,503,96,SERVICE_UNAVAILABLE", "409,503,96,RESERVATION_FAILED"})
    void reserveFailurePaths(int upstreamStatus, int status, String code, String reason) {
        // given — GET card succeeds; inject only the later reserve call (e.g. concurrent depletion after GET).
        String pan = createCard();
        inject("POST", "/api/cards/" + pan + "/reserve", upstreamStatus, Map.of("error", "injected reserve error"));
        Map<String, Object> request = TestData.authorization(pan);
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then
        assertDecline(response, status, request.get("stan").toString(), code, reason);
        card(pan).body("availableBalance", equalTo(100000));
    }

    @ParameterizedTest
    @CsvSource({"404,404,14,TRANSACTION_NOT_FOUND", "400,404,14,TRANSACTION_NOT_FOUND",
            "409,409,05,ALREADY_ROLLED_BACK", "503,503,96,SERVICE_UNAVAILABLE", "500,503,96,SERVICE_UNAVAILABLE",
            "402,503,96,ROLLBACK_FAILED"})
    void rollbackFailurePaths(int upstreamStatus, int status, String code, String reason) {
        // given
        String pan = createCard();
        String rrn = TestData.rrn();
        inject("POST", "/api/cards/" + pan + "/rollback", upstreamStatus, Map.of("error", "injected rollback error"));
        // when
        Response response = given().spec(Api.AUTH).body(TestData.rollback(pan, rrn, 1))
                .when().post("/api/internal/rollback");
        // then
        response.then().statusCode(status).body("rrn", equalTo(rrn), "responseCode", equalTo(code),
                "status", equalTo("DECLINED"), "declineReason", equalTo(reason), "processingTimeMs", greaterThanOrEqualTo(0));
        card(pan).body("availableBalance", equalTo(100000));
    }

    @Test
    void unknownReasonFromMalformedUpstreamCardStatus() {
        // given — impossible through public card writes; inject the defensive null-status boundary explicitly.
        String pan = createCard();
        Map<String, Object> upstream = card(pan).extract().as(Map.class);
        upstream.put("status", null);
        inject("GET", "/api/cards/" + pan, 200, upstream);
        Map<String, Object> request = TestData.authorization(pan);
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then
        assertDecline(response, 400, request.get("stan").toString(), "05", "UNKNOWN_REASON");
        card(pan).body("status", equalTo("ACTIVE"), "availableBalance", equalTo(100000));
    }

    @Test
    void databaseFailureCurrentlyLosesDomainResponse() {
        // given — an invalid HTTP dependency response triggers the limit storage error without SQL/DB clients.
        String pan = createCard();
        Map<String, Object> upstream = card(pan).extract().as(Map.class);
        upstream.put("pan", "1".repeat(17));
        inject("GET", "/api/cards/" + pan, 200, upstream);
        Map<String, Object> request = TestData.authorization(pan);
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then — known defect D14: failed SQL marks the outer transaction rollback-only and loses the decline DTO.
        response.then().statusCode(500).body("error", equalTo("Internal Server Error"), "status", equalTo(500),
                "path", equalTo("/api/internal/authorize"), "timestamp", notNullValue());
        card(pan).body("availableBalance", equalTo(100000));
    }
}
