package com.processing.api.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Raw HTTP evidence contains synthetic data only; failure to write evidence fails the run. */
public final class EvidenceFilter implements Filter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path DIRECTORY = Path.of("target", "api-evidence");
    private static volatile String currentTest = "fixture";

    public static void label(String test) {
        currentTest = test;
    }

    @Override
    public Response filter(FilterableRequestSpecification request,
                           FilterableResponseSpecification responseSpecification, FilterContext context) {
        Response response = context.next(request, responseSpecification);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", Instant.now().toString());
        entry.put("test", currentTest);
        entry.put("method", request.getMethod());
        entry.put("uri", request.getURI());
        entry.put("requestBody", request.getBody());
        entry.put("status", response.statusCode());
        entry.put("responseHeaders", response.headers().toString());
        entry.put("responseBody", response.asString());
        try {
            Files.createDirectories(DIRECTORY);
            JSON.writerWithDefaultPrettyPrinter().writeValue(
                    DIRECTORY.resolve(UUID.randomUUID() + ".json").toFile(), entry);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write HTTP evidence", exception);
        }
        return response;
    }
}
