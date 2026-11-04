package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.Pairwise;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Sources: TransactionFilter, TransactionSpecification, TransactionController (CSV ignores pagination). */
class TransactionSearchApiTest extends ApiTestSupport {
    private List<Map<String, Object>> seed() {
        List<Map<String, Object>> records = new ArrayList<>();
        String pan = TestData.missingPan("400000");
        String merchant = "M" + TestData.token(14);
        Instant base = Instant.now().minusSeconds(10).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        for (int index = 0; index < 3; index++) {
            Map<String, Object> record = TestData.transaction();
            record.put("pan", pan);
            record.put("merchantId", merchant);
            record.put("createdAt", base.plusSeconds(index).toString());
            record.put("transmissionDateTime", LocalDate.now(ZoneOffset.UTC).minusDays(index) + "T12:00:00Z");
            if (index == 1) {
                record.put("status", "DECLINED");
                record.put("declineReason", "CARD_BLOCKED");
                record.put("authCode", null);
                record.put("rrn", null);
            }
            if (index == 2) {
                record.put("mcc", "5812");
                record.put("issuerId", "ISS002");
            }
            LoggerApiTest.store(record);
            records.add(record);
        }
        return records;
    }

    static IntStream pairwiseRows() {
        return Pairwise.rows(9).stream().mapToInt(Integer::intValue);
    }

    @ParameterizedTest(name = "pairwise parameter mask {0}")
    @MethodSource("pairwiseRows")
    void searchParametersPairwise(int mask) {
        // given — PAN is always the isolation key; every pair of the other nine parameter presences is covered.
        List<Map<String, Object>> records = seed();
        String[] keys = {"status", "merchantId", "issuerId", "mcc", "declineReason", "dateFrom", "dateTo", "limit", "offset"};
        Object[] values = {"DECLINED", records.getFirst().get("merchantId"), "ISS001", "5411", "CARD_BLOCKED",
                LocalDate.now(ZoneOffset.UTC).minusDays(1).toString(), LocalDate.now(ZoneOffset.UTC).toString(), 1, 1};
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("pan", records.getFirst().get("pan"));
        for (int index = 0; index < keys.length; index++) {
            if ((mask & (1 << index)) != 0) {
                query.put(keys[index], values[index]);
            }
        }
        List<String> expected = records.reversed().stream().filter(record -> matches(record, query))
                .map(record -> record.get("id").toString()).toList();
        int total = expected.size();
        expected = expected.stream().skip(((Number) query.getOrDefault("offset", 0)).intValue())
                .limit(((Number) query.getOrDefault("limit", 50)).intValue()).toList();
        // when
        Response response = given().spec(Api.GATEWAY).queryParams(query).when().get("/api/transactions/search");
        // then — exact IDs and order, not only the total size.
        response.then().statusCode(200).body("total", equalTo(total), "transactions", hasSize(expected.size()));
        assertEquals(expected, response.jsonPath().getList("transactions.id", String.class));
    }

    private boolean matches(Map<String, Object> record, Map<String, Object> query) {
        for (String field : List.of("status", "merchantId", "issuerId", "mcc", "declineReason")) {
            if (query.containsKey(field) && !query.get(field).equals(record.get(field))) {
                return false;
            }
        }
        LocalDate date = Instant.parse(record.get("transmissionDateTime").toString()).atZone(ZoneOffset.UTC).toLocalDate();
        return (!query.containsKey("dateFrom") || !date.isBefore(LocalDate.parse(query.get("dateFrom").toString())))
                && (!query.containsKey("dateTo") || !date.isAfter(LocalDate.parse(query.get("dateTo").toString())));
    }

    @ParameterizedTest
    @CsvSource({"1,0,1", "2,0,2", "2,1,2", "3,3,0", "500,0,3", "50,1000000,0"})
    void paginationBoundaries(int limit, int offset, int size) {
        // given
        List<Map<String, Object>> records = seed();
        // when
        Response response = given().spec(Api.GATEWAY).queryParams("pan", records.getFirst().get("pan"),
                "limit", limit, "offset", offset).when().get("/api/transactions/search");
        // then
        response.then().statusCode(200).body("total", equalTo(3), "transactions", hasSize(size));
        if (size > 0) {
            response.then().body("transactions[0].id", equalTo(records.get(2 - offset).get("id")));
        }
    }

    @Test
    void utcDatesAreInclusiveAndBlankDeclineReasonIsIgnored() {
        // given
        List<Map<String, Object>> records = seed();
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        // when
        Response response = given().spec(Api.GATEWAY).queryParams("pan", records.getFirst().get("pan"),
                "dateFrom", today, "dateTo", today, "declineReason", " ").when().get("/api/transactions/search");
        // then
        response.then().statusCode(200).body("total", equalTo(1), "transactions", hasSize(1),
                "transactions[0].id", equalTo(records.getFirst().get("id")));
    }

    @ParameterizedTest
    @CsvSource({"limit,0", "limit,-1", "limit,501", "offset,-1", "status,UNKNOWN", "limit,abc",
            "dateFrom,not-a-date", "dateTo,2026-02-30"})
    void invalidSearchAndExportParameters(String key, String value) {
        // given — search and export share the same DTO validation.
        List<String> endpoints = List.of("/api/transactions/search", "/api/transactions/export");
        for (String endpoint : endpoints) {
            // when
            Response response = given().spec(Api.GATEWAY).queryParam(key, value).when().get(endpoint);
            // then
            assertError(response, 400, "transaction-logger", key);
            response.then().body("error", equalTo("Validation error"), "retryAfterMs", equalTo("0"));
        }
    }

    @Test
    void noMatchesReturnsAnEmptyArray() {
        // given
        var httpRequest = given().spec(Api.GATEWAY).queryParam("pan", TestData.missingPan("999999"));
        // when
        Response response = httpRequest.when().get("/api/transactions/search");
        // then
        response.then().statusCode(200).body("total", equalTo(0), "transactions", hasSize(0));
    }

    @Test
    void exportContainsOwnRowsAndIgnoresPagination() {
        // given
        List<Map<String, Object>> records = seed();
        // when
        Response response = given().spec(Api.GATEWAY).accept("text/csv")
                .queryParams("pan", records.getFirst().get("pan"), "limit", 1, "offset", 2)
                .when().get("/api/transactions/export");
        // then
        response.then().statusCode(200).contentType("text/csv")
                .header("Content-Disposition", equalTo("attachment; filename=\"transactions.csv\""))
                .body(startsWith("id,mti,stan,rrn,pan,processingCode,amount,"));
        assertEquals(4, response.asString().lines().count());
        for (Map<String, Object> record : records) {
            response.then().body(containsString(record.get("id").toString()));
        }
    }

    @Test
    void emptyCsvHasHeaderAndNoDataRows() {
        // given
        var httpRequest = given().spec(Api.GATEWAY).accept("text/csv")
                .queryParam("pan", TestData.missingPan("999999"));
        // when
        Response response = httpRequest.when().get("/api/transactions/export");
        // then
        response.then().statusCode(200).contentType("text/csv").body(startsWith("id,mti,stan,"));
        assertEquals(1, response.asString().lines().count());
    }
}
