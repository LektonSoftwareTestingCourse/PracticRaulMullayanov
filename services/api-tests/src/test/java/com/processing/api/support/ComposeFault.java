package com.processing.api.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/** TCP listeners are closed without changing Docker DNS; the broker stop is confined to smp-api-tests. */
public final class ComposeFault {
    private static final Path FILE = Path.of("../../docker-compose.api-tests.yaml").toAbsolutePath().normalize();

    private ComposeFault() { }

    public static void withStopped(List<String> services, Runnable assertions) {
        if (!Boolean.getBoolean("api.faults.enabled")) {
            throw new IllegalStateException("Explicit -Dapi.faults.enabled=true is required for isolated Compose faults");
        }
        if (!services.contains("rabbitmq")) {
            try {
                for (String service : services) {
                    availability(service, false);
                }
                assertions.run();
            } finally {
                for (String service : services) {
                    availability(service, true);
                }
                for (String service : services) {
                    if (service.equals("card-management") || service.equals("transaction-logger")) {
                        String path = service.equals("card-management") ? "/api/cards" : "/api/transactions/search";
                        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20))
                                .pollInterval(Duration.ofMillis(250)).untilAsserted(() ->
                                        given().spec(Api.GATEWAY).queryParam("probe", "recovery")
                                                .get(path).then().statusCode(200));
                    }
                }
            }
            return;
        }
        try {
            if (!Files.readString(FILE).contains("smp-api-tests")) {
                throw new IllegalStateException("Unexpected Compose file: " + FILE);
            }
            command(services, "stop", "--timeout", "5");
            assertions.run();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } finally {
            command(services, "up", "-d", "--no-deps", "--wait", "--wait-timeout", "180");
            // Application Rabbit connections must recover as well as the broker health-check.
            // Docker/JVM DNS may retain the previous broker address for 30 seconds after stop/start.
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofSeconds(1))
                    .untilAsserted(() -> given().spec(Api.SWITCH).get("/actuator/health")
                            .then().statusCode(200).body("status", equalTo("UP")));
        }
    }

    private static void availability(String service, boolean enabled) {
        given().spec(Api.CARD_PROXY).body(java.util.Map.of("enabled", enabled))
                .post("/__availability/{service}", service).then().statusCode(200)
                .body("service", equalTo(service), "enabled", equalTo(enabled));
    }

    private static void command(List<String> services, String... arguments) {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "-p", "smp-api-tests", "-f", FILE.toString()));
        command.addAll(List.of(arguments));
        command.addAll(services);
        try {
            Path log = Path.of("target", "compose-faults.log");
            Files.createDirectories(log.getParent());
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
            if (!process.waitFor(Duration.ofMinutes(4).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Compose command timed out: " + command);
            }
            if (process.exitValue() != 0) {
                throw new AssertionError("Compose failed: " + command + "; see " + log.toAbsolutePath());
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
