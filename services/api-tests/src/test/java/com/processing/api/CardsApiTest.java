package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.TestData;
import com.processing.api.support.Pairwise;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Source: CardController, CardGeneratorController, CardServiceImpl; tz/05-card-management.md. */
class CardsApiTest extends ApiTestSupport {
    @Test
    void createGetPatchAndSoftDelete() {
        // given
        String pan = createCard();
        // when
        Response get = given().spec(Api.GATEWAY).get("/api/cards/{pan}", pan);
        Response patch = given().spec(Api.GATEWAY).body(Map.of("status", "BLOCKED", "availableBalance", 777,
                "dailyLimit", 5000, "monthlyLimit", 50000)).when().patch("/api/cards/{pan}", pan);
        // then
        get.then().statusCode(200).body("pan", equalTo(pan), "bin", equalTo("400000"),
                "cardholderName", equalTo("RAUL API TEST"), "currencyCode", equalTo("643"),
                "dailyLimit", equalTo(10000), "monthlyLimit", equalTo(100000), "issuerId", equalTo("ISS001"),
                "id", matchesPattern("[0-9a-f-]{36}"), "expiryDate", matchesPattern("[0-9]{4}-[0-9]{2}"));
        patch.then().statusCode(200).body("status", equalTo("BLOCKED"), "availableBalance", equalTo(777));
        given().spec(Api.GATEWAY).delete("/api/cards/{pan}", pan).then().statusCode(204).body(isEmptyString());
        assertError(given().spec(Api.CARDS).get("/api/cards/{pan}", pan), 404, "card-management",
                pan.substring(0, 4) + "********" + pan.substring(12));
        assertError(given().spec(Api.GATEWAY).delete("/api/cards/{pan}", pan), 404,
                "card-management", pan.substring(0, 4) + "********" + pan.substring(12));
    }

    @Test
    void emptyPatchKeepsAllKnownValues() {
        // given
        String pan = createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of()).when().patch("/api/cards/{pan}", pan);
        // then
        response.then().statusCode(200).body("status", equalTo("ACTIVE"), "dailyLimit", equalTo(10000),
                "monthlyLimit", equalTo(100000), "availableBalance", equalTo(100000));
    }

    @ParameterizedTest
    @CsvSource({"GET,404", "PATCH,404", "DELETE,404", "RESERVE,404", "ROLLBACK,404"})
    void missingCard(String method, int status) {
        // given
        String pan = TestData.missingPan("400000");
        var request = given().spec(Api.GATEWAY);
        if (method.equals("PATCH")) {
            request.body(Map.of("status", "ACTIVE"));
        } else if (method.equals("RESERVE") || method.equals("ROLLBACK")) {
            request.body(TestData.rollback(pan, TestData.rrn(), 1));
        }
        // when
        Response response = switch (method) {
            case "RESERVE" -> request.post("/api/cards/{pan}/reserve", pan);
            case "ROLLBACK" -> request.post("/api/cards/{pan}/rollback", pan);
            default -> request.request(method, "/api/cards/{pan}", pan);
        };
        // then
        assertError(response, status, "card-management", pan.substring(0, 4) + "********" + pan.substring(12));
        response.then().body("error", equalTo("CardNotFoundException"));
    }

    @Test
    void cardReadCacheMissHitAndInvalidation() {
        // given
        String pan = createCard();
        // when
        Response miss = given().spec(Api.GATEWAY).get("/api/cards/{pan}", pan);
        Response hit = given().spec(Api.GATEWAY).get("/api/cards/{pan}", pan);
        patch(pan, Map.of("availableBalance", 123));
        Response afterPatch = given().spec(Api.GATEWAY).when().get("/api/cards/{pan}", pan);
        // then
        miss.then().statusCode(200).header("X-Cache", equalTo("MISS")).body("pan", equalTo(pan));
        hit.then().statusCode(200).header("X-Cache", equalTo("HIT")).body("pan", equalTo(pan));
        afterPatch.then().statusCode(200).header("X-Cache", equalTo("MISS")).body("availableBalance", equalTo(123));
    }

    @Test
    void reserveDuplicateDifferentAmountAndRollback() {
        // given
        String pan = createCard();
        String rrn = TestData.rrn();
        // when
        Response reserve = given().spec(Api.GATEWAY).body(Map.of("rrn", rrn, "amount", 100))
                .when().post("/api/cards/{pan}/reserve", pan);
        // then
        reserve.then().statusCode(200).body("pan", equalTo(pan), "availableBalance", equalTo(99900));
        for (int amount : new int[]{100, 101}) {
            assertError(given().spec(Api.GATEWAY).body(Map.of("rrn", rrn, "amount", amount))
                    .post("/api/cards/{pan}/reserve", pan), 409, "card-management", rrn);
        }
        given().spec(Api.GATEWAY).body(TestData.rollback(pan, rrn, 100))
                .post("/api/cards/{pan}/rollback", pan).then().statusCode(200).body("availableBalance", equalTo(100000));
        assertError(given().spec(Api.GATEWAY).body(TestData.rollback(pan, rrn, 100))
                .post("/api/cards/{pan}/rollback", pan), 409, "card-management", rrn);
        card(pan).body("availableBalance", equalTo(100000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BLOCKED", "INACTIVE", "EXPIRED"})
    void cannotReserveOnNonActiveCard(String status) {
        // given
        String pan = createCard();
        patch(pan, Map.of("status", status));
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of("rrn", TestData.rrn(), "amount", 1))
                .when().post("/api/cards/{pan}/reserve", pan);
        // then
        assertError(response, 409, "card-management", status);
        card(pan).body("availableBalance", equalTo(100000));
    }

    @Test
    void reserveInsufficientBalance() {
        // given
        String pan = createCard();
        // when
        Response response = given().spec(Api.GATEWAY).body(Map.of("rrn", TestData.rrn(), "amount", 100001))
                .when().post("/api/cards/{pan}/reserve", pan);
        // then
        assertError(response, 402, "card-management", "funds");
        card(pan).body("availableBalance", equalTo(100000));
    }

    @Test
    void unknownReservationHasNoBalanceEffect() {
        // given
        String pan = createCard();
        String rrn = TestData.rrn();
        // when
        Response response = given().spec(Api.GATEWAY).body(TestData.rollback(pan, rrn, 1))
                .when().post("/api/cards/{pan}/rollback", pan);
        // then
        assertError(response, 404, "card-management", rrn);
        card(pan).body("availableBalance", equalTo(100000));
    }

    @Test
    void generationAndBulkStatusUpdateAffectOnlyOwnCards() {
        // given
        String bin = createBin();
        // when
        Response generated = given().spec(Api.GATEWAY).body(Map.of("count", 3, "bins", List.of(bin)))
                .when().post("/api/cards/generate");
        // then
        generated.then().statusCode(201).body("generated", equalTo(3), "cards", hasSize(3),
                "cards.bin", everyItem(equalTo(bin)), "cards.pan", everyItem(matchesPattern("[0-9]{16}")));
        List<String> pans = generated.path("cards.pan");
        ownedCards.addAll(pans);
        given().spec(Api.GATEWAY).body(Map.of("pans", pans, "status", "BLOCKED"))
                .post("/api/cards/bulk-update").then().statusCode(200).body("updated", equalTo(3));
        for (String pan : pans) {
            card(pan).body("status", equalTo("BLOCKED"));
        }
        given().spec(Api.GATEWAY).body(Map.of("bins", List.of(bin), "status", "ACTIVE"))
                .post("/api/cards/bulk-update").then().statusCode(200).body("updated", equalTo(3));
        for (String pan : pans) {
            card(pan).body("status", equalTo("ACTIVE"));
        }
    }

    @ParameterizedTest
    @CsvSource({"1,0,1", "2,0,2", "2,1,2", "3,3,0", "10000,0,3"})
    void cardPaginationBoundaries(int limit, int offset, int size) {
        // given — random BIN isolates the result set without relying on global counts.
        String bin = createBin();
        for (int i = 0; i < 3; i++) {
            createCard(TestData.card(bin));
        }
        // when
        Response response = given().spec(Api.GATEWAY).queryParams("bin", bin, "limit", limit, "offset", offset)
                .when().get("/api/cards");
        // then
        response.then().statusCode(200).body("total", equalTo(3), "cards", hasSize(size),
                "cards.bin", everyItem(equalTo(bin)));
    }

    @Test
    void cardFiltersCombineWithAndAndCanReturnEmpty() {
        // given
        String bin = createBin();
        String pan = createCard(TestData.card(bin));
        String issuer = card(pan).extract().path("issuerId");
        patch(pan, Map.of("status", "BLOCKED"));
        // when
        Response response = given().spec(Api.GATEWAY).queryParams("bin", bin, "status", "BLOCKED",
                        "issuerId", issuer, "startDate", Instant.now().minusSeconds(60).toString(),
                        "endDate", Instant.now().plusSeconds(60).toString())
                .when().get("/api/cards");
        // then
        response.then().statusCode(200).body("total", equalTo(1), "cards", hasSize(1), "cards[0].pan", equalTo(pan));
        given().spec(Api.GATEWAY).queryParams("bin", bin, "status", "ACTIVE").get("/api/cards")
                .then().statusCode(200).body("total", equalTo(0), "cards", hasSize(0));
        given().spec(Api.GATEWAY).queryParams("bin", bin, "offset", 1000000).get("/api/cards")
                .then().statusCode(200).body("total", equalTo(1), "cards", hasSize(0));
    }

    @Test
    void missingBinIsRejectedOnCardCreationAndGeneration() {
        // given — a fresh mapping is deleted to guarantee that this BIN is absent.
        String bin = createBin();
        given().spec(Api.CARDS).delete("/api/bins/{bin}", bin).then().statusCode(204);
        // when
        Response create = given().spec(Api.GATEWAY).body(TestData.card(bin)).when().post("/api/cards");
        Response generate = given().spec(Api.GATEWAY).body(Map.of("count", 1, "bins", List.of(bin)))
                .when().post("/api/cards/generate");
        // then
        assertError(create, 404, "card-management", bin);
        assertError(generate, 404, "card-management", bin);
        // Recreate the owned mapping for the standard cleanup path.
        given().spec(Api.CARDS).body(Map.of("bin", bin, "issuerId", "API001")).post("/api/bins").then().statusCode(201);
    }

    static IntStream cardPairwiseRows() {
        return Pairwise.rows(6).stream().mapToInt(Integer::intValue);
    }

    @ParameterizedTest(name = "card query pairwise mask {0}")
    @org.junit.jupiter.params.provider.MethodSource("cardPairwiseRows")
    void cardFiltersPairwise(int mask) {
        // given — BIN anchors the owned set; every pair of the other six parameter presences is covered.
        String bin = createBin();
        List<String> pans = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            pans.add(createCard(TestData.card(bin)));
        }
        patch(pans.get(1), Map.of("status", "BLOCKED"));
        String issuer = card(pans.getFirst()).extract().path("issuerId");
        String[] keys = {"status", "issuerId", "startDate", "endDate", "limit", "offset"};
        Object[] values = {"BLOCKED", issuer, Instant.now().minusSeconds(60).toString(),
                Instant.now().plusSeconds(60).toString(), 1, 1};
        Map<String, Object> query = new java.util.LinkedHashMap<>();
        query.put("bin", bin);
        for (int i = 0; i < keys.length; i++) {
            if ((mask & (1 << i)) != 0) {
                query.put(keys[i], values[i]);
            }
        }
        List<String> expected = query.containsKey("status") ? List.of(pans.get(1)) : pans.reversed();
        List<String> eligible = List.copyOf(expected);
        int total = expected.size();
        expected = expected.stream().skip(query.containsKey("offset") ? 1 : 0)
                .limit(query.containsKey("limit") ? 1 : 10).toList();
        // when
        Response response = given().spec(Api.GATEWAY).queryParams(query).when().get("/api/cards");
        // then
        response.then().statusCode(200).body("total", equalTo(total), "cards", hasSize(expected.size()),
                "cards.pan", everyItem(isIn(eligible)));
        // CMS does not define ORDER BY: assert membership/cardinality, rather than inventing page order.
        List<String> actual = response.jsonPath().getList("cards.pan", String.class);
        org.junit.jupiter.api.Assertions.assertEquals(actual.size(), new java.util.HashSet<>(actual).size());
    }
}
