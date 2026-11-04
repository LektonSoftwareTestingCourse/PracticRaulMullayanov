package com.processing.api.support;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.UUID;

/** Shared HTTP configuration. No global mutable RestAssured configuration or automatic POST retries. */
public final class Api {
    public static final RequestSpecification GATEWAY = specification("gateway", 28080);
    public static final RequestSpecification CARDS = specification("cards", 28081);
    public static final RequestSpecification SWITCH = specification("switch", 28082);
    public static final RequestSpecification AUTH = specification("auth", 28083);
    public static final RequestSpecification MERCHANT = specification("merchant", 28084);
    public static final RequestSpecification TERMINAL = specification("terminal", 28085);
    public static final RequestSpecification LOGGER = specification("logger", 28088);
    public static final RequestSpecification BIN_LOOKUP = specification("binLookup", 28096);
    public static final RequestSpecification NOTIFICATIONS = specification("notifications", 28097);

    public static final RequestSpecification CARD_PROXY = specification("cardProxy", 28099);

    private Api() { }

    private static RequestSpecification specification(String service, int port) {
        return new RequestSpecBuilder()
                .setBaseUri(System.getProperty("api." + service + ".url", "http://localhost:" + port))
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", 5000)
                        .setParam("http.socket.timeout", 30000)))
                .addFilter((request, response, context) -> {
                    // Each ordinary request has its own rate-limit bucket. Rate-limit tests override this header.
                    if (!request.getHeaders().hasHeaderWithName("X-Forwarded-For")) {
                        request.header("X-Forwarded-For", "api-" + UUID.randomUUID());
                    }
                    return context.next(request, response);
                })
                .addFilter(new EvidenceFilter())
                .build();
    }
}
