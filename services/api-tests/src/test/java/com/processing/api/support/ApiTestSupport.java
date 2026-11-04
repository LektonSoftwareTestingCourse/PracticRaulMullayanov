package com.processing.api.support;

import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.Tag;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;

@Tag("api")
public abstract class ApiTestSupport {
    protected final List<String> ownedCards = new ArrayList<>();
    private final List<String> ownedBins = new ArrayList<>();

    @BeforeEach
    void labelHttpEvidence(TestInfo info) {
        EvidenceFilter.label(info.getTestClass().orElseThrow().getSimpleName() + "#"
                + info.getTestMethod().orElseThrow().getName() + " / " + info.getDisplayName());
    }

    protected String createCard() {
        return createCard(TestData.card("400000"));
    }

    protected String createCard(Map<String, Object> request) {
        Response response = given().spec(Api.GATEWAY).body(request).when().post("/api/cards");
        response.then().statusCode(201).body("pan", matchesPattern("[0-9]{16}"))
                .body("status", equalTo("ACTIVE"))
                .body("availableBalance", equalTo(((Number) request.get("initialBalance")).intValue()));
        String pan = response.path("pan");
        ownedCards.add(pan);
        return pan;
    }

    protected String createBin() {
        // Reserve a random test BIN using its uniqueness constraint; never overwrite an existing mapping.
        for (int attempt = 0; attempt < 10; attempt++) {
            String bin = "8" + TestData.rrn().substring(7);
            Response response = given().spec(Api.CARDS).body(Map.of("bin", bin, "issuerId", "IT" + TestData.token(8)))
                    .when().post("/api/bins");
            if (response.statusCode() == 201) {
                ownedBins.add(bin);
                return bin;
            }
            response.then().statusCode(409).body("error", equalTo("BinAlreadyExistException"));
        }
        throw new AssertionError("Could not allocate a unique test BIN");
    }

    protected void patch(String pan, Map<String, Object> patch) {
        given().spec(Api.GATEWAY).body(patch).when().patch("/api/cards/{pan}", pan)
                .then().statusCode(200).body("pan", equalTo(pan));
    }

    protected ValidatableResponse card(String pan) {
        // Read directly to avoid confusing the Gateway's 30-second read cache with committed card state.
        return given().spec(Api.CARDS).when().get("/api/cards/{pan}", pan).then().statusCode(200);
    }

    protected static void assertDecline(Response response, int httpStatus, String stan, String code, String reason) {
        response.then().statusCode(httpStatus).contentType("application/json")
                .body("stan", equalTo(stan), "status", equalTo("DECLINED"),
                        "responseCode", equalTo(code), "declineReason", equalTo(reason),
                        "rrn", nullValue(), "authCode", nullValue(), "processingTimeMs", greaterThanOrEqualTo(0));
    }

    protected static void assertApproved(Response response, String stan) {
        response.then().statusCode(200).contentType("application/json")
                .body("stan", equalTo(stan), "status", equalTo("APPROVED"), "responseCode", equalTo("00"),
                        "declineReason", nullValue(), "rrn", matchesPattern("[0-9]{12}"),
                        "authCode", matchesPattern("[A-Z0-9]{6}"), "processingTimeMs", greaterThanOrEqualTo(0));
    }

    protected static void assertError(Response response, int status, String service, String messagePart) {
        response.then().statusCode(status).contentType("application/json")
                .body("error", not(isEmptyOrNullString()), "message", containsString(messagePart),
                        "timestamp", notNullValue(), "serviceName", equalTo(service));
    }

    protected static void awaitTransaction(String pan, String stan) {
        await().alias("Gateway transaction becomes searchable via HTTP")
                .atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                        given().spec(Api.GATEWAY).queryParam("pan", pan).when().get("/api/transactions/search")
                                .then().statusCode(200)
                                .body("transactions.findAll { it.stan == '" + stan + "' }", hasSize(1)));
    }

    @AfterEach
    void cleanupOwnedData() {
        for (String pan : ownedCards) {
            given().spec(Api.CARDS).when().delete("/api/cards/{pan}", pan)
                    .then().statusCode(oneOf(204, 404));
        }
        for (String bin : ownedBins) {
            given().spec(Api.CARDS).when().delete("/api/bins/{bin}", bin).then().statusCode(204);
        }
    }
}
