package com.santana.carpool.geocoding;

import com.santana.carpool.domain.GeoPoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@DisplayName("GoogleGeocodingService Tests")
class GoogleGeocodingServiceTest {

    private GoogleGeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        geocodingService = new GoogleGeocodingService(
                "test-api-key-123",
                "https://maps.googleapis.com/maps/api/geocode/json",
                3600L
        );
    }

    private HttpServer startServer(int statusCode, String responseBody, AtomicInteger hitCounter) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/geocode", exchange -> {
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
        assertThatThrownBy(() -> new GoogleGeocodingService(null, "https://example.com", 3600L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key is required");
    }

    @Test
    @DisplayName("Should throw exception when API key is blank")
    void testConstructorWithBlankApiKey() {
        assertThatThrownBy(() -> new GoogleGeocodingService("  ", "https://example.com", 3600L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("API key is required");
    }

    @Test
    @DisplayName("Should throw exception when eircode is null")
    void testGeocodeEircodeWithNull() {
        assertThatThrownBy(() -> geocodingService.geocodeEircode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Eircode is required");
    }

    @Test
    @DisplayName("Should throw exception when eircode is blank")
    void testGeocodeEircodeWithBlank() {
        assertThatThrownBy(() -> geocodingService.geocodeEircode("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Eircode is required");
    }

    @Test
    @DisplayName("Should initialize cache stats as zero")
    void testInitialCacheStats() {
        Map<String, Long> stats = geocodingService.cacheStats();

        assertThat(stats)
                .containsEntry("hits", 0L)
                .containsEntry("misses", 0L)
                .containsEntry("size", 0L);
    }

    @Test
    @DisplayName("Should expose cache statistics")
    void testCacheStatsStructure() {
        Map<String, Long> stats = geocodingService.cacheStats();

        assertThat(stats)
                .containsKeys("hits", "misses", "size")
                .allSatisfy((key, value) -> assertThat(value).isGreaterThanOrEqualTo(0L));
    }

    @Test
    @DisplayName("Should create service with valid parameters")
    void testConstructorWithValidParameters() {
        GoogleGeocodingService service = new GoogleGeocodingService(
                "valid-key",
                "https://maps.googleapis.com/maps/api/geocode/json",
                3600L
        );
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("Should have configured cache TTL in seconds")
    void testTTLConfiguration() {
        GoogleGeocodingService shortTtl = new GoogleGeocodingService(
                "key1",
                "https://maps.googleapis.com/maps/api/geocode/json",
                60L
        );

        GoogleGeocodingService longTtl = new GoogleGeocodingService(
                "key2",
                "https://maps.googleapis.com/maps/api/geocode/json",
                86400L
        );

        assertThat(shortTtl).isNotNull();
        assertThat(longTtl).isNotNull();
    }

    @Test
    @DisplayName("Should normalize eircode (case-insensitive, trim whitespace)")
    void testEircodeNormalization() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"status\":\"OK\",\"results\":[{\"geometry\":{\"location\":{\"lat\":53.30251819999999,\"lng\":-8.9849391}}}]}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/geocode";
            geocodingService = new GoogleGeocodingService("test-key", baseUrl, 3600L);

            GeoPoint first = geocodingService.geocodeEircode("N37 XR90");
            GeoPoint second = geocodingService.geocodeEircode(" n37  xr90 ");

            assertThat(first.latitude()).isEqualTo(53.30251819999999);
            assertThat(first.longitude()).isEqualTo(-8.9849391);
            assertThat(second).isEqualTo(first);
            assertThat(hits.get()).isEqualTo(1);
            assertThat(geocodingService.cacheStats()).containsEntry("hits", 1L);

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should parse successful geocoding response")
    void testGeocodeSuccess() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"status\":\"OK\",\"results\":[{\"geometry\":{\"location\":{\"lat\":53.3025,\"lng\":-8.9849}}}]}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/geocode";
            geocodingService = new GoogleGeocodingService("test-key", baseUrl, 3600L);

            GeoPoint result = geocodingService.geocodeEircode("N37 XR90");
            assertThat(result.latitude()).isEqualTo(53.3025);
            assertThat(result.longitude()).isEqualTo(-8.9849);
            assertThat(hits.get()).isEqualTo(1);

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should throw when API returns non-200")
    void testGeocodeHttpError() {
        AtomicInteger hits = new AtomicInteger(0);

        try {
            HttpServer server = startServer(500, "{}", hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/geocode";
            geocodingService = new GoogleGeocodingService("test-key", baseUrl, 3600L);

            assertThatThrownBy(() -> geocodingService.geocodeEircode("N37 XR90"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("HTTP status 500");

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should include API error message when status is not OK")
    void testGeocodeApiStatusError() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"status\":\"ZERO_RESULTS\",\"error_message\":\"No matching eircode\"}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/geocode";
            geocodingService = new GoogleGeocodingService("test-key", baseUrl, 3600L);

            assertThatThrownBy(() -> geocodingService.geocodeEircode("INVALID"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ZERO_RESULTS")
                    .hasMessageContaining("No matching eircode");

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should throw when status field is missing")
    void testGeocodeMissingStatus() {
        AtomicInteger hits = new AtomicInteger(0);

        try {
            HttpServer server = startServer(200, "{\"results\":[]}", hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/geocode";
            geocodingService = new GoogleGeocodingService("test-key", baseUrl, 3600L);

            assertThatThrownBy(() -> geocodingService.geocodeEircode("N37 XR90"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Could not parse geocoding status");

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should throw status error when error_message is absent")
    void testGeocodeApiStatusWithoutErrorMessage() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"status\":\"ZERO_RESULTS\"}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/geocode";
            geocodingService = new GoogleGeocodingService("test-key", baseUrl, 3600L);

            assertThatThrownBy(() -> geocodingService.geocodeEircode("INVALID"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Geocoding API returned status ZERO_RESULTS");

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should throw when status is OK but location is missing")
    void testGeocodeMissingLocation() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"status\":\"OK\",\"results\":[{}]}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/geocode";
            geocodingService = new GoogleGeocodingService("test-key", baseUrl, 3600L);

            assertThatThrownBy(() -> geocodingService.geocodeEircode("N37 XR90"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Could not parse geocoding coordinates");

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }

    @Test
    @DisplayName("Should expire cache when TTL is zero")
    void testGeocodeCacheExpiry() {
        AtomicInteger hits = new AtomicInteger(0);
        String body = "{\"status\":\"OK\",\"results\":[{\"geometry\":{\"location\":{\"lat\":53.3025,\"lng\":-8.9849}}}]}";

        try {
            HttpServer server = startServer(200, body, hits);
            String baseUrl = "http://localhost:" + server.getAddress().getPort() + "/geocode";
            geocodingService = new GoogleGeocodingService("test-key", baseUrl, -1L);

            geocodingService.geocodeEircode("N37 XR90");
            geocodingService.geocodeEircode("N37XR90");

            assertThat(hits.get()).isEqualTo(2);

            server.stop(0);
        } catch (IOException ex) {
            fail("Unexpected test server error", ex);
        }
    }
}
