package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Executable bug reproductions. Run explicitly with -Papi-tests,strict-contract; failures are never called passes. */
@Tag("strict-contract")
class StrictContractTest extends ApiTestSupport {
    @Test
    void d01ResponseMtiMustBe0110() {
        // given — docs/api-spec.md, AuthorizationResponse.mti.
        String pan = createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(TestData.authorization(pan)).when().post("/api/transactions");
        // then
        response.then().statusCode(200).body("status", equalTo("APPROVED"), "mti", equalTo("0110"));
    }

    @Test
    void d02CardResponseMustContainTheDocumentedFields() {
        // given — docs/api-spec.md Card example and model table.
        String pan = createCard();
        // when
        Response response = given().spec(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        // then
        response.then().statusCode(200).body("expiryDate", matchesPattern("[0-9]{4}"), "createdAt", notNullValue());
    }

    @ParameterizedTest
    @CsvSource({"stan,12345", "stan,1234567", "processingCode,12345", "processingCode,1234567",
            "terminalId,1234567", "terminalId,123456789", "merchantId,12345678901234", "merchantId,1234567890123456"})
    void d03GatewayMustExplainInvalidFieldLength(String field, String value) {
        // given — docs/api-spec.md AuthorizationRequest and common DTO ExactSize constraints.
        Map<String, Object> request = TestData.authorization(TestData.missingPan("400000"));
        request.put(field, value);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then
        response.then().statusCode(400).body("serviceName", equalTo("gateway"), "message", containsString(field));
    }

    @Test
    void d04AuthorizationValidationMustExplainItsField() {
        // given — practice-5 completeness requirement 4, docs/api-spec.md unified ErrorResponse.
        Map<String, Object> request = TestData.authorization(TestData.missingPan("400000"));
        request.put("stan", "12345");
        // when
        Response response = given().spec(Api.AUTH).body(request).when().post("/api/internal/authorize");
        // then
        response.then().statusCode(400).body("serviceName", equalTo("authorization"), "message", containsString("stan"));
    }

    @Test
    void d05CardValidationMustIdentifyItsField() {
        // given — practice-5 completeness requirement 4: error identifies both field and violated rule.
        Map<String, Object> request = TestData.card("400000");
        request.put("dailyLimit", -1);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/cards");
        // then
        response.then().statusCode(400).body("message", containsString("dailyLimit"));
    }

    @Test
    void d07TerminalRequestFromSpecificationMustWorkWithoutTps() {
        // given — docs/api-spec.md terminal run example contains only count and scenario.
        createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of("count", 1, "scenario", "normal"))
                .when().post("/api/simulator/terminal/run");
        // then
        response.then().statusCode(200).body("totalSubmitted", equalTo(1), "transactions", hasSize(1));
    }

    @Test
    void d08ReserveRequiresAnAmount() {
        // given — ReserveRequest documents amount as the amount to reserve; a missing amount is invalid input.
        String pan = createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of("rrn", TestData.rrn()))
                .when().post("/api/cards/{pan}/reserve", pan);
        // then
        response.then().statusCode(400).body("message", containsString("amount"));
    }

    @Test
    void d09RollbackCannotCreditMoreThanWasReserved() {
        // given — tz/05-card-management.md: rollback releases the reserved funds.
        String pan = createCard();
        String rrn = TestData.rrn();
        given().spec(Api.CARDS).body(Map.of("rrn", rrn, "amount", 100)).post("/api/cards/{pan}/reserve", pan)
                .then().statusCode(200).body("availableBalance", equalTo(99900));
        // when
        Response response = given().spec(Api.GATEWAY).body(TestData.rollback(pan, rrn, 101))
                .when().post("/api/cards/{pan}/rollback", pan);
        // then
        response.then().statusCode(400).body("message", not(isEmptyOrNullString()));
        card(pan).body("availableBalance", equalTo(99900));
    }

    @Test
    void d10ReversedDateRangeExplainsTheRule() {
        // given — TransactionFilter @ValidDateRange says dateFrom must not be after dateTo.
        // when
        Response response = given().spec(Api.GATEWAY).queryParams("dateFrom", "2026-11-02", "dateTo", "2026-11-01")
                .when().get("/api/transactions/search");
        // then
        response.then().statusCode(400).body("message", containsString("dateFrom must not be after dateTo"));
    }

    @Test
    void d11BinIssuerIdConstraintMustBeActive() {
        // given — IssuerIdValidator documents [A-Z0-9]{6,10}, but @IssuerId lacks @Constraint/@Retention.
        String bin = createBin();
        given().spec(Api.CARDS).delete("/api/bins/{bin}", bin).then().statusCode(204);
        Map<String, Object> request = new LinkedHashMap<>(Map.of("bin", bin, "issuerId", "lower"));
        // when
        Response response = given().spec(Api.CARDS).body(request).when().post("/api/bins");
        // then — recreate the fixture only if the invalid input was correctly rejected.
        if (response.statusCode() == 400) {
            given().spec(Api.CARDS).body(Map.of("bin", bin, "issuerId", "API001"))
                    .post("/api/bins").then().statusCode(201);
        }
        response.then().statusCode(400).body("message", containsString("IssuerId"));
    }

    @Test
    void d12FractionalAmountMustHaveAnObservableBalanceEffect() {
        // given — AuthorizationRequest.amount is BigDecimal; no scale constraint is declared.
        String pan = createCard();
        Map<String, Object> request = TestData.authorization(pan);
        request.put("amount", 0.01);
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/transactions");
        // then — CardRestMapper currently rounds the returned balance to an integer (HALF_EVEN).
        assertApproved(response, request.get("stan").toString());
        card(pan).body("availableBalance", equalTo(99999.99f));
    }
}
