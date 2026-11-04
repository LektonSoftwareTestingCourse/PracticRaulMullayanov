package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.YearMonth;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.*;

/** Sources: tz/04-authorization.md, AuthControllerImpl and DeclineOutcome; deviations D01/D02 in the report. */
class AuthorizationApiTest extends ApiTestSupport {
    @ParameterizedTest(name = "{0}: {1}/{2}, HTTP {3}")
    @CsvSource({"BLOCKED,05,CARD_BLOCKED,403", "INACTIVE,05,CARD_INACTIVE,403",
            "EXPIRED,54,CARD_EXPIRED,403", "DELETED,14,CARD_NOT_FOUND,404"})
    void cardStatusesHaveDistinctReasons(String status, String code, String reason, int httpStatus) {
        // given — only the card status changes; balance and limits remain explicit.
        String pan = createCard();
        if (status.equals("DELETED")) {
            given().spec(Api.GATEWAY).delete("/api/cards/{pan}", pan).then().statusCode(204);
        } else {
            patch(pan, Map.of("status", status));
        }
        Map<String, Object> request = TestData.authorization(pan);
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then
        assertDecline(response, httpStatus, request.get("stan").toString(), code, reason);
        if (!status.equals("DELETED")) {
            card(pan).body("availableBalance", equalTo(100000));
        }
    }

    @ParameterizedTest
    @CsvSource({"BLOCKED,05,CARD_BLOCKED", "INACTIVE,05,CARD_INACTIVE", "EXPIRED,54,CARD_EXPIRED"})
    void gatewayPreservesBusinessDecline(String status, String code, String reason) {
        // given
        String pan = createCard();
        patch(pan, Map.of("status", status));
        Map<String, Object> request = TestData.authorization(pan);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then — Switch wraps the authorization result in HTTP 200.
        assertDecline(response, 200, request.get("stan").toString(), code, reason);
        awaitTransaction(pan, request.get("stan").toString());
        card(pan).body("availableBalance", equalTo(100000));
    }

    @ParameterizedTest
    @CsvSource({"999999,false", "400000,true"})
    void cardNotFoundThroughRoutingAndThroughCardState(String bin, boolean direct) {
        // given — unknown routing BIN vs known routing BIN with absent PAN.
        Map<String, Object> request = TestData.authorization(TestData.missingPan(bin));
        // when
        Response response = given().spec(direct ? Api.AUTH : Api.GATEWAY).body(request).when()
                .post(direct ? "/api/internal/authorize" : "/api/transactions");
        // then
        assertDecline(response, direct ? 404 : 200, request.get("stan").toString(), "14", "CARD_NOT_FOUND");
    }

    @Test
    void expiredByTransactionDateIsDifferentFromExpiredStatus() {
        // given — ACTIVE card, date just after the card's actual validity period.
        String pan = createCard();
        String expiry = card(pan).extract().path("expiryDate");
        Map<String, Object> request = TestData.authorization(pan);
        request.put("transmissionDateTime", YearMonth.parse(expiry).plusMonths(1).atDay(1) + "T00:00:00Z");
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then
        assertDecline(response, 403, request.get("stan").toString(), "54", "CARD_EXPIRED");
        card(pan).body("status", equalTo("ACTIVE"), "availableBalance", equalTo(100000));
    }

    @ParameterizedTest
    @CsvSource({"99999,200,00,APPROVED", "100000,200,00,APPROVED", "100001,422,51,DECLINED"})
    void balanceBoundary(int amount, int httpStatus, String code, String status) {
        // given — limits are above all balance boundary values.
        String pan = createCard(TestData.card("400000"));
        patch(pan, Map.of("dailyLimit", 200000, "monthlyLimit", 300000));
        Map<String, Object> request = TestData.authorization(pan);
        request.put("amount", amount);
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then
        if (status.equals("APPROVED")) {
            assertApproved(response, request.get("stan").toString());
            card(pan).body("availableBalance", equalTo(100000 - amount));
        } else {
            assertDecline(response, httpStatus, request.get("stan").toString(), code, "INSUFFICIENT_FUNDS");
            card(pan).body("availableBalance", equalTo(100000));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {9999, 10000, 10001})
    void dailyLimitBoundary(int amount) {
        // given
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        request.put("amount", amount);
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then
        if (amount <= 10000) {
            assertApproved(response, request.get("stan").toString());
            card(pan).body("availableBalance", equalTo(100000 - amount));
        } else {
            assertDecline(response, 400, request.get("stan").toString(), "61", "EXCEEDS_AMOUNT_LIMIT");
            card(pan).body("availableBalance", equalTo(100000));
        }
    }

    @Test
    void accumulatedDailyLimitRejectsNextPurchase() {
        // given
        String pan = createCard();
        Map<String, Object> first = TestData.authorization(pan);
        first.put("amount", 10000);
        assertApproved(given().spec(Api.AUTH).body(first).post("/api/internal/authorize"), first.get("stan").toString());
        Map<String, Object> next = TestData.authorization(pan);
        next.put("amount", 1);
        // when
        Response response = given().spec(Api.AUTH).body(next).when().post("/api/internal/authorize");
        // then
        assertDecline(response, 400, next.get("stan").toString(), "61", "EXCEEDS_AMOUNT_LIMIT");
        card(pan).body("availableBalance", equalTo(90000));
    }

    @Test
    void cumulativeMonthlyLimitAcrossDifferentDays() {
        // given — UTC dates in a future month still inside the generated card's validity.
        String pan = createCard();
        patch(pan, Map.of("dailyLimit", 1000, "monthlyLimit", 1000));
        YearMonth month = YearMonth.now().plusMonths(1);
        Map<String, Object> first = TestData.authorization(pan);
        first.put("amount", 1000);
        first.put("transmissionDateTime", month.atDay(1) + "T12:00:00Z");
        assertApproved(given().spec(Api.AUTH).body(first).post("/api/internal/authorize"), first.get("stan").toString());
        Map<String, Object> second = TestData.authorization(pan);
        second.put("amount", 1);
        second.put("transmissionDateTime", month.atDay(2) + "T12:00:00Z");
        // when
        Response response = given().spec(Api.AUTH).body(second).when().post("/api/internal/authorize");
        // then
        assertDecline(response, 400, second.get("stan").toString(), "61", "EXCEEDS_AMOUNT_LIMIT");
        card(pan).body("availableBalance", equalTo(99000));
    }

    @Test
    void purchaseTraversesGatewaySwitchAuthorizationAndLogger() {
        // given
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        request.remove("issuerId"); // Switch must fill this optional public field.
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then — mti deviation D01 is tested separately against the strict contract.
        assertApproved(response, request.get("stan").toString());
        response.then().body(matchesJsonSchemaInClasspath("schemas/authorization-response.json"));
        card(pan).body("availableBalance", equalTo(99900));
        awaitTransaction(pan, request.get("stan").toString());
        given().spec(Api.GATEWAY).queryParam("pan", pan).get("/api/transactions/search")
                .then().statusCode(200).body("total", equalTo(1), "transactions", hasSize(1),
                        "transactions[0].issuerId", equalTo("ISS001"), "transactions[0].amount", equalTo(100),
                        "transactions[0].rrn", equalTo(response.path("rrn")),
                        "transactions[0].authCode", equalTo(response.path("authCode")),
                        "transactions[0].status", equalTo("APPROVED"));
    }

    @Test
    void internalSwitchRouteIsReachableWithoutGateway() {
        // given
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        // when
        Response response = given().spec(Api.SWITCH).body(request).when().post("/api/internal/route");
        // then
        assertApproved(response, request.get("stan").toString());
        awaitTransaction(pan, request.get("stan").toString());
    }

    @Test
    void authorizationRollbackIsNotAppliedTwice() {
        // given
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        Response approved = given().spec(Api.AUTH).body(request).post("/api/internal/authorize");
        assertApproved(approved, request.get("stan").toString());
        Map<String, Object> rollback = TestData.rollback(pan, approved.path("rrn"), 100);
        // when
        Response first = given().spec(Api.AUTH).body(rollback).when().post("/api/internal/rollback");
        Response second = given().spec(Api.AUTH).body(rollback).when().post("/api/internal/rollback");
        // then
        first.then().statusCode(200).body("rrn", equalTo(approved.path("rrn")), "responseCode", equalTo("00"),
                "status", equalTo("APPROVED"), "declineReason", nullValue(), "processingTimeMs", greaterThanOrEqualTo(0));
        second.then().statusCode(409).body("rrn", equalTo(approved.path("rrn")), "responseCode", equalTo("05"),
                "status", equalTo("DECLINED"), "declineReason", equalTo("ALREADY_ROLLED_BACK"));
        card(pan).body("availableBalance", equalTo(100000));
    }

    @Test
    void rollbackForUnknownReservation() {
        // given
        String pan = createCard();
        String rrn = TestData.rrn();
        // when
        Response response = given().spec(Api.AUTH).body(TestData.rollback(pan, rrn, 1))
                .when().post("/api/internal/rollback");
        // then
        response.then().statusCode(404).body("rrn", equalTo(rrn), "status", equalTo("DECLINED"),
                "responseCode", equalTo("14"), "declineReason", equalTo("TRANSACTION_NOT_FOUND"));
        card(pan).body("availableBalance", equalTo(100000));
    }
}
