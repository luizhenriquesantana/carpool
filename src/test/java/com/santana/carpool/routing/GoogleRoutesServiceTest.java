package com.santana.carpool.routing;

import com.santana.carpool.domain.GeoPoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;

@DisplayName("GoogleRoutesService Tests")
class GoogleRoutesServiceTest {

    private GoogleRoutesService routesService;

    @BeforeEach
    void setUp() {
        routesService = new GoogleRoutesService(
                "test-api-key-123",
                "https://routes.googleapis.com/directions/v2:computeRoutes",
                1800L
        );
    }

    private HttpServer startServer(int statusCode, String responseBody, AtomicInteger hitCounter) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/routes", exchange -> {
            hitCounter.incrementAndGet();
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return server;
    }

    @Test
    @DisplayName("Should throw exception when API key is null")
    void testConstructorWithNullApiKey() {
        assertThatThrownBy(() -> new GoogleRoutesService(null, "https://example.com", 1800L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key is required");
    }

    @Test
    @DisplayName("Should throw exception when API key is blank")
    void testConstructorWithBlankApiKey() {
        assertThatThrownBy(() -> new GoogleRoutesService("  ", "https://example.com", 1800L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key is required");
    }

    @Test
    @DisplayName("Should initialize cache stats as zero")
    void testInitialCacheStats() {
        Map<String, Long> stats = routesService.cacheStats();

        assertThat(stats)
                .containsEntry("hits", 0L)
                .containsEntry("misses", 0L)
                .containsEntry("size", 0L);
    }

    @Test
    @DisplayName("Should parse distance and duration from successful response")
    void testComputeDrivingLegSuccess() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"routes\":[{\"distanceMeters\":220008,\"duration\":\"9000s\"}]}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/routes";
            routesService = new GoogleRoutesService("test-key", baseUrl, 1800L);

            GeoPoint dublin = new GeoPoint(53.3498, -6.2603);
            GeoPoint cork = new GeoPoint(51.8985, -8.4761);

            RouteLegMetrics metrics = routesService.computeDrivingLeg(dublin, cork);
            assertThat(metrics.distanceKm()).isCloseTo(220.008, within(0.001));
            assertThat(metrics.durationSeconds()).isEqualTo(9000L);

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should return cached route on repeated lookup")
    void testCacheHitOnRepeatedLeg() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"routes\":[{\"distanceMeters\":12345,\"duration\":\"901s\"}]}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/routes";
            routesService = new GoogleRoutesService("test-key", baseUrl, 1800L);

            GeoPoint a = new GeoPoint(53.3498, -6.2603);
            GeoPoint b = new GeoPoint(53.3520, -6.2700);

            RouteLegMetrics first = routesService.computeDrivingLeg(a, b);
            RouteLegMetrics second = routesService.computeDrivingLeg(a, b);

            assertThat(second).isEqualTo(first);
            assertThat(hits.get()).isEqualTo(1);
            assertThat(routesService.cacheStats()).containsEntry("hits", 1L);

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should maintain separate cache entries for different routes")
    void testMultipleRoutesCaching() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"routes\":[{\"distanceMeters\":12345,\"duration\":\"901s\"}]}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/routes";
            routesService = new GoogleRoutesService("test-key", baseUrl, 1800L);

            GeoPoint a = new GeoPoint(53.3498, -6.2603);
            GeoPoint b = new GeoPoint(53.3520, -6.2700);
            GeoPoint c = new GeoPoint(53.3000, -6.2000);

            routesService.computeDrivingLeg(a, b);
            routesService.computeDrivingLeg(a, c);

            assertThat(hits.get()).isEqualTo(2);
            assertThat(routesService.cacheStats()).containsEntry("size", 2L);

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should throw when API returns non-200")
    void testHttpError() {
        AtomicInteger hits = new AtomicInteger(0);
        GeoPoint origin = new GeoPoint(53.3498, -6.2603);
        GeoPoint destination = new GeoPoint(53.3520, -6.2700);

        try {
            HttpServer server = startServer(503, "{}", hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/routes";
            routesService = new GoogleRoutesService("test-key", baseUrl, 1800L);

                assertThatThrownBy(() -> routesService.computeDrivingLeg(origin, destination))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HTTP status 503");

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @ParameterizedTest(name = "{index} => {2}")
    @MethodSource("routesParseErrorCases")
    @DisplayName("Should handle distance/duration parse error branches")
    void testRoutesParseErrorBranches(String body, String expectedMessage, String caseName) {
        AtomicInteger hits = new AtomicInteger(0);
        GeoPoint origin = new GeoPoint(53.3498, -6.2603);
        GeoPoint destination = new GeoPoint(53.3520, -6.2700);

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/routes";
            routesService = new GoogleRoutesService("test-key", baseUrl, 1800L);

            assertThatThrownBy(() -> routesService.computeDrivingLeg(origin, destination))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(expectedMessage);

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> routesParseErrorCases() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "{\"routes\":[{\"duration\":\"901s\"}],\"error_message\":\"quota exceeded\"}",
                        "quota exceeded",
                        "distance missing with API error"
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "{\"routes\":[{\"distanceMeters\":12345}]}",
                        "Could not parse routes duration",
                        "duration missing without API error"
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "{\"routes\":[{\"duration\":\"61s\"}]}",
                        "Could not parse routes distance",
                        "distance missing without API error"
                ),
                org.junit.jupiter.params.provider.Arguments.of(
                        "{\"routes\":[{\"distanceMeters\":12345}],\"error_message\":\"duration unavailable\"}",
                        "duration unavailable",
                        "duration missing with API error"
                )
        );
    }

    @Test
    @DisplayName("Should expire cache when TTL is zero")
    void testCacheExpiryAtZeroTtl() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"routes\":[{\"distanceMeters\":1000,\"duration\":\"60s\"}]}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/routes";
            routesService = new GoogleRoutesService("test-key", baseUrl, -1L);

            GeoPoint origin = new GeoPoint(53.3498, -6.2603);
            GeoPoint destination = new GeoPoint(53.3520, -6.2700);

            routesService.computeDrivingLeg(origin, destination);
            routesService.computeDrivingLeg(origin, destination);

            assertThat(hits.get()).isEqualTo(2);

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }
}
