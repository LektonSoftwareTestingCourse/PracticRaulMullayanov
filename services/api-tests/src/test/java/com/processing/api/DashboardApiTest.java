package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import com.processing.api.support.TestData;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Sources: DashboardController, ChartsFilter, TransactionService and tz/08-transaction-logger.md. */
class DashboardApiTest extends ApiTestSupport {
    @Test
    void statsHaveConsistentCountersAndRate() {
        // given
        LoggerApiTest.store(TestData.transaction());
        // when
        Response response = given().spec(Api.GATEWAY).when().get("/api/dashboard/stats");
        // then — global totals are never assumed to equal this test's own fixture count.
        response.then().statusCode(200).body("totalTransactions", greaterThanOrEqualTo(1),
                "approvedCount", greaterThanOrEqualTo(1), "declinedCount", greaterThanOrEqualTo(0),
                "approvalRate", allOf(greaterThanOrEqualTo(0f), lessThanOrEqualTo(1f)),
                "totalAmount", notNullValue(), "averageAmount", notNullValue(),
                "avgProcessingTimeMs", notNullValue(), "transactionsPerMinute", greaterThanOrEqualTo(0));
        org.junit.jupiter.api.Assertions.assertTrue(response.jsonPath().getDouble("totalAmount") >= 100);
        int total = response.path("totalTransactions");
        int approved = response.path("approvedCount");
        int declined = response.path("declinedCount");
        assertEquals(total, approved + declined);
        assertEquals((double) approved / total, response.jsonPath().getDouble("approvalRate"), 0.00001);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 500})
    void recentHonorsLimitAndDescendingOrder(int limit) {
        // given
        Map<String, Object> record = TestData.transaction();
        LoggerApiTest.store(record);
        // when
        Response response = given().spec(Api.GATEWAY).queryParam("limit", limit).when().get("/api/dashboard/recent");
        // then
        response.then().statusCode(200).body("size()", allOf(greaterThanOrEqualTo(1), lessThanOrEqualTo(limit)));
        var dates = response.jsonPath().getList("createdAt", String.class).stream().map(Instant::parse).toList();
        assertEquals(dates.stream().sorted(java.util.Comparator.reverseOrder()).toList(), dates);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 501})
    void invalidRecentLimit(int limit) {
        // given
        var httpRequest = given().spec(Api.GATEWAY).queryParam("limit", limit);
        // when
        Response response = httpRequest.when().get("/api/dashboard/recent");
        // then
        assertError(response, 400, "transaction-logger", "limit");
    }

    @ParameterizedTest
    @ValueSource(strings = {"hour", "day"})
    void chartsAggregateWithinHalfOpenRange(String granularity) {
        // given — an isolated future bucket avoids other tests' aggregate data, even across repeated runs.
        Instant base = Instant.parse("2100-01-01T00:00:00Z")
                .plus(ThreadLocalRandom.current().nextInt(200000), ChronoUnit.DAYS);
        Map<String, Object> first = TestData.transaction();
        first.put("createdAt", base.toString());
        Map<String, Object> excluded = TestData.transaction();
        excluded.put("createdAt", base.plusSeconds(3600).toString());
        LoggerApiTest.store(first);
        LoggerApiTest.store(excluded);
        // when
        Response response = given().spec(Api.GATEWAY).queryParams("granularity", granularity,
                "from", base.toString(), "to", base.plusSeconds(3600).toString()).when().get("/api/dashboard/charts");
        // then — lower bound inclusive, upper bound exclusive.
        response.then().statusCode(200).body("", hasSize(1), "[0].timestamp", equalTo(base.toString()),
                "[0].total", equalTo(1), "[0].approved", equalTo(1), "[0].declined", equalTo(0), "[0].amount", equalTo(100));
    }

    @ParameterizedTest
    @CsvSource({"granularity,minute", "from,not-a-date", "to,not-a-date"})
    void invalidChartsParameters(String key, String value) {
        // given
        var httpRequest = given().spec(Api.GATEWAY).queryParam(key, value);
        // when
        Response response = httpRequest.when().get("/api/dashboard/charts");
        // then
        assertError(response, 400, "transaction-logger", key);
    }

    @Test
    void chartsEmptyHalfOpenInterval() {
        // given
        String now = Instant.now().toString();
        // when
        Response response = given().spec(Api.GATEWAY).queryParams("from", now, "to", now)
                .when().get("/api/dashboard/charts");
        // then
        response.then().statusCode(200).body("", hasSize(0));
    }
}
