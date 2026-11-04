package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Sources: common DTO annotations + CardController; D05 records loss of field names in CMS errors. */
class CardValidationTest extends ApiTestSupport {
    static Stream<Arguments> invalidCreateFields() {
        List<Arguments> cases = new ArrayList<>();
        for (String field : List.of("bin", "cardholderName", "currencyCode")) {
            cases.add(Arguments.of(field, null, "must not be blank"));
            cases.add(Arguments.of(field, " ", ""));
        }
        for (String bin : List.of("40000", "4000000", "40000A")) {
            cases.add(Arguments.of("bin", bin, "exactly 6 digits"));
        }
        for (String name : List.of("Raul", "РАУЛЬ", "RAUL1")) {
            cases.add(Arguments.of("cardholderName", name, "uppercase letters"));
        }
        for (String currency : List.of("64", "6430")) {
            cases.add(Arguments.of("currencyCode", currency, "exactly 3"));
        }
        cases.add(Arguments.of("currencyCode", "RUB", "digits only"));
        cases.add(Arguments.of("dailyLimit", -1, "negative"));
        cases.add(Arguments.of("monthlyLimit", -1, "negative"));
        return cases.stream();
    }

    @ParameterizedTest(name = "create {0} = [{1}]")
    @MethodSource("invalidCreateFields")
    void invalidCardCreation(String field, Object value, String message) {
        // given
        Map<String, Object> body = TestData.card("400000");
        body.put(field, value);
        // when
        Response response = given().spec(Api.GATEWAY).body(body).when().post("/api/cards");
        // then — documented CMS limitation: validation message may omit field name (D05).
        assertError(response, 400, "card-management", message);
        response.then().body("error", equalTo("MethodArgumentNotValidException"), "message", not(isEmptyOrNullString()));
    }

    @ParameterizedTest
    @CsvSource({"dailyLimit,-1,negative", "monthlyLimit,-1,negative",
            "dailyLimit,100001,Daily limit can not be larger"})
    void invalidPatchLeavesCardUnchanged(String field, int value, String message) {
        // given
        String pan = createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of(field, value))
                .when().patch("/api/cards/{pan}", pan);
        // then
        assertError(response, 400, "card-management", message);
        card(pan).body("dailyLimit", equalTo(10000), "monthlyLimit", equalTo(100000), "availableBalance", equalTo(100000));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void nonNegativeLimitsHaveValidLowerBoundary(int value) {
        // given
        String pan = createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of("dailyLimit", value, "monthlyLimit", value))
                .when().patch("/api/cards/{pan}", pan);
        // then
        response.then().statusCode(200).body("dailyLimit", equalTo(value), "monthlyLimit", equalTo(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"400000123456789", "40000012345678901", "400000123456789A"})
    void invalidPathPanForAllCardOperations(String pan) {
        // given — request data besides the path are valid.
        Map<String, Object> reserve = Map.of("rrn", TestData.rrn(), "amount", 1);
        // CardController applies the same @Pan constraint to all five paths.
        for (String method : List.of("GET", "PATCH", "DELETE", "RESERVE", "ROLLBACK")) {
            var request = given().spec(Api.GATEWAY);
            // when
            Response response = switch (method) {
                case "PATCH" -> request.body(Map.of()).patch("/api/cards/{pan}", pan);
                case "RESERVE" -> request.body(reserve).post("/api/cards/{pan}/reserve", pan);
                case "ROLLBACK" -> request.body(TestData.rollback(TestData.missingPan("400000"),
                        reserve.get("rrn").toString(), 1)).post("/api/cards/{pan}/rollback", pan);
                default -> request.request(method, "/api/cards/{pan}", pan);
            };
            // then
            assertError(response, 400, "card-management", "pan");
            response.then().body("message", containsString("exactly 16 digits"));
        }
    }

    @ParameterizedTest
    @CsvSource({"reserve,0", "reserve,-1", "rollback,0", "rollback,-1"})
    void invalidReservationAmount(String operation, int amount) {
        // given
        String pan = createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(TestData.rollback(pan, TestData.rrn(), amount))
                .when().post("/api/cards/{pan}/{operation}", pan, operation);
        // then
        assertError(response, 400, "card-management", "greater than 0");
        card(pan).body("availableBalance", equalTo(100000));
    }

    @ParameterizedTest
    @CsvSource({"reserve,12345678901", "reserve,1234567890123", "reserve,12345678901A",
            "rollback,12345678901", "rollback,1234567890123", "rollback,12345678901A"})
    void invalidReservationRrn(String operation, String rrn) {
        // given
        String pan = createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(TestData.rollback(pan, rrn, 1))
                .when().post("/api/cards/{pan}/{operation}", pan, operation);
        // then
        assertError(response, 400, "card-management", "exactly 12 digits");
    }

    @ParameterizedTest
    @CsvSource({"limit,0,400,limit", "limit,-1,400,limit", "limit,10001,413,10000",
            "offset,-1,400,offset", "bin,40000,400,bin", "bin,4000000,400,bin", "bin,40000A,400,bin",
            "issuerId,lower,400,issuerId", "issuerId,ABCDEFGHIJK,400,issuerId"})
    void invalidCardQuery(String key, String value, int status, String message) {
        // given
        var httpRequest = given().spec(Api.GATEWAY).queryParam(key, value);
        // when
        Response response = httpRequest.when().get("/api/cards");
        // then
        assertError(response, status, "card-management", message);
    }

    @ParameterizedTest
    @CsvSource({"0,400", "-1,400", "10001,400"})
    void generationCountBoundaries(int count, int status) {
        // given
        var httpRequest = given().spec(Api.GATEWAY).body(Map.of("count", count, "bins", List.of("400000")));
        // when
        Response response = httpRequest.when().post("/api/cards/generate");
        // then
        assertError(response, status, "card-management", count > 10000 ? "10000" : "greater than or equal to 1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPTY", "NULL", "BAD_BIN"})
    void invalidGenerationBins(String kind) {
        // given
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("count", 1);
        request.put("bins", kind.equals("NULL") ? null : kind.equals("EMPTY") ? List.of() : List.of("40000A"));
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/cards/generate");
        // then
        assertError(response, 400, "card-management", kind.equals("BAD_BIN") ? "6 digits" : "must not be empty");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NEITHER", "BOTH", "NULL_STATUS", "BAD_PAN", "BAD_BIN"})
    void invalidBulkUpdate(String kind) {
        // given
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("status", "ACTIVE");
        switch (kind) {
            case "BOTH" -> {
                request.put("bins", List.of("400000"));
                request.put("pans", List.of(TestData.missingPan("400000")));
            }
            case "NULL_STATUS" -> {
                request.put("pans", List.of(TestData.missingPan("400000")));
                request.put("status", null);
            }
            case "BAD_PAN" -> request.put("pans", List.of("bad"));
            case "BAD_BIN" -> request.put("bins", List.of("bad"));
            default -> { }
        }
        // when
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/cards/bulk-update");
        // then
        assertError(response, 400, "card-management", "");
        response.then().body("message", not(isEmptyOrNullString()));
    }
}
