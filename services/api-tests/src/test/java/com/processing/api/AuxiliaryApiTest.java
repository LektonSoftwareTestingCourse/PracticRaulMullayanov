package com.processing.api;

import com.processing.api.support.Api;
import com.processing.api.support.ApiTestSupport;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Inventory sources: controller mappings, gateway/application.yml and docs/api-spec.md. */
class AuxiliaryApiTest extends ApiTestSupport {
    static Stream<Arguments> services() {
        return Stream.of(Arguments.of(Api.GATEWAY, "/health"), Arguments.of(Api.CARDS, "/health"),
                Arguments.of(Api.SWITCH, "/health"), Arguments.of(Api.AUTH, "/health"),
                Arguments.of(Api.TERMINAL, "/health"), Arguments.of(Api.MERCHANT, "/health"),
                Arguments.of(Api.LOGGER, "/health"), Arguments.of(Api.BIN_LOOKUP, "/actuator/health"),
                Arguments.of(Api.NOTIFICATIONS, "/actuator/health"));
    }

    @ParameterizedTest
    @MethodSource("services")
    void serviceHealth(RequestSpecification specification, String path) {
        // given
        var httpRequest = given().spec(specification);
        // when
        Response response = httpRequest.when().get(path);
        // then
        response.then().statusCode(200).contentType("application/json").body("status", oneOf("ok", "UP"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/switch-docs", "/logger-docs", "/terminal-docs", "/merchant-docs", "/cards-docs"})
    void gatewayPublishesDownstreamOpenApi(String path) {
        // given
        var httpRequest = given().spec(Api.GATEWAY);
        // when
        Response response = httpRequest.when().get(path);
        // then
        response.then().statusCode(200).body("openapi", startsWith("3."), "paths", notNullValue(), "servers", hasSize(1));
        Map<String, Object> paths = response.jsonPath().getMap("paths");
        assertFalse(paths.keySet().stream().anyMatch(key -> key.contains("/internal/")));
        if (path.equals("/switch-docs")) {
            response.then().body("paths.'/api/transactions'.post", notNullValue());
        }
    }

    @Test
    void binMappingLifecycleAndDuplicate() {
        // given
        String bin = createBin();
        String issuer = given().spec(Api.CARDS).get("/api/bins/{bin}", bin).path("issuerId");
        // when
        Response all = given().spec(Api.CARDS).get("/api/bins");
        Response duplicate = given().spec(Api.CARDS).body(Map.of("bin", bin, "issuerId", "API001"))
                .when().post("/api/bins");
        // then
        all.then().statusCode(200).body("findAll { it.bin == '" + bin + "' }", hasSize(1),
                "find { it.bin == '" + bin + "' }.issuerId", equalTo(issuer));
        assertError(duplicate, 409, "card-management", bin);
        given().spec(Api.CARDS).delete("/api/bins/{bin}", bin).then().statusCode(204).body(isEmptyString());
        assertError(given().spec(Api.CARDS).get("/api/bins/{bin}", bin), 404, "card-management", bin);
        assertError(given().spec(Api.CARDS).delete("/api/bins/{bin}", bin), 404, "card-management", bin);
        given().spec(Api.CARDS).body(Map.of("bin", bin, "issuerId", issuer)).post("/api/bins").then().statusCode(201);
    }

    @ParameterizedTest
    @ValueSource(strings = {"40000", "4000000", "40000A"})
    void invalidBinMapping(String bin) {
        // given
        var httpRequest = given().spec(Api.CARDS).body(Map.of("bin", bin, "issuerId", "API001"));
        // when
        Response response = httpRequest.when().post("/api/bins");
        // then
        assertError(response, 400, "card-management", "exactly 6 digits");
    }

    @ParameterizedTest
    @CsvSource({"400000,ISS001", "400001,ISS002", "400002,ISS003", "400003,ISS004", "400004,ISS005"})
    void binLookupKnownBins(String bin, String issuer) {
        // given
        var httpRequest = given().spec(Api.BIN_LOOKUP);
        // when
        Response response = httpRequest.when().get("/api/bin/{bin}", bin);
        // then
        response.then().statusCode(200).body("bin", equalTo(bin), "issuerId", equalTo(issuer),
                "issuerName", not(isEmptyOrNullString()));
    }

    @Test
    void binLookupUnknownAndInjectedFailureHaveEmptyBodies() {
        // given
        var httpRequest = given().spec(Api.BIN_LOOKUP);
        // when
        Response unknown = httpRequest.when().get("/api/bin/999999");
        Response failed = given().spec(Api.BIN_LOOKUP).queryParam("fail", true).when().get("/api/bin/400000");
        // then — intentionally empty bodies are explicit in BinLookupController.
        unknown.then().statusCode(404).body(isEmptyString());
        failed.then().statusCode(500).body(isEmptyString());
    }

    @ParameterizedTest
    @CsvSource({"delay,abc", "fail,not-a-boolean"})
    void invalidBinLookupParameters(String parameter, String value) {
        // given
        var httpRequest = given().spec(Api.BIN_LOOKUP).queryParam(parameter, value);
        // when
        Response response = httpRequest.when().get("/api/bin/400000");
        // then
        response.then().statusCode(400).body("status", equalTo(400), "error", equalTo("Bad Request"),
                "path", equalTo("/api/bin/400000"));
    }

    @ParameterizedTest
    @CsvSource({"0,1", "1,2", "1000000,50"})
    void notificationsArePaginated(int page, int size) {
        // given
        var httpRequest = given().spec(Api.NOTIFICATIONS).queryParams("page", page, "size", size);
        // when
        Response response = httpRequest.when().get("/api/notifications");
        // then
        response.then().statusCode(200).body("number", equalTo(page), "size", equalTo(size),
                "numberOfElements", lessThanOrEqualTo(size), "content", notNullValue(),
                "totalElements", greaterThanOrEqualTo(0));
        if (page == 1000000) {
            response.then().body("content", hasSize(0));
        }
    }

    @Test
    void missingPublicRoutesAreRecordedAsDiscrepancies() {
        // given — D06: controller exists, but Gateway has no matching route.
        List<String> paths = List.of("/api/bins", "/api/notifications", "/api/simulator/merchants");
        for (String path : paths) {
            // when
            Response response = given().spec(Api.GATEWAY).when().get(path);
            // then
            response.then().statusCode(404)
                    .body("status", equalTo(404), "error", equalTo("Not Found"), "path", equalTo(path));
        }
    }
}
