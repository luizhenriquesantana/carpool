package com.santana.carpool.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.santana.carpool.cache.TtlCache;
import com.santana.carpool.domain.GeoPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Service
public class GoogleRoutesService {
    private static final Logger log = LoggerFactory.getLogger(GoogleRoutesService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String apiKey;
    private final String routesBaseUrl;
    private final TtlCache<String, RouteLegMetrics> routesCache;

    public GoogleRoutesService(
            @Value("${googleMaps.apiKey:}") String apiKey,
            @Value("${googleMaps.routesBaseUrl:https://routes.googleapis.com/directions/v2:computeRoutes}") String routesBaseUrl,
            @Value("${googleMaps.routesCacheTtlSeconds:1800}") long routesCacheTtlSeconds
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Google Maps API key is required.");
        }

        this.apiKey = apiKey;
        this.routesBaseUrl = routesBaseUrl;
        this.routesCache = new TtlCache<>(routesCacheTtlSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("GoogleRoutesService initialized (cacheTtl={}s)", routesCacheTtlSeconds);
    }

    public RouteLegMetrics computeDrivingLeg(GeoPoint origin, GeoPoint destination) {
        // Route metrics are cached per origin/destination pair to cut API latency and cost.
        String cacheKey = buildCacheKey(origin, destination);
        RouteLegMetrics cached = routesCache.get(cacheKey);
        if (cached != null) {
            log.debug("Routes cache hit for {}", cacheKey);
            return cached;
        }
        log.debug("Routes cache miss for {}, calling API", cacheKey);

        String requestBody = buildRequestBody(origin, destination);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(routesBaseUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "routes.distanceMeters,routes.duration")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Routes request failed with HTTP status " + response.statusCode());
            }

            String body = response.body();
            JsonNode root = MAPPER.readTree(body);
            long distanceMeters = extractDistanceMeters(root);
            long durationSeconds = extractDurationSeconds(root);
            RouteLegMetrics metrics = new RouteLegMetrics(distanceMeters / 1000.0, durationSeconds);
            routesCache.put(cacheKey, metrics);
            log.debug("Routes API returned {}km, {}s for {}", metrics.distanceKm(), durationSeconds, cacheKey);
            return metrics;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Routes request interrupted for {}", cacheKey, ex);
            throw new IllegalStateException("Routes request interrupted.", ex);
        } catch (IOException ex) {
            log.error("Routes request I/O failure for {}", cacheKey, ex);
            throw new IllegalStateException("Routes request failed.", ex);
        }
    }

    private String buildRequestBody(GeoPoint origin, GeoPoint destination) {
        return String.format(Locale.US,
                "{"
                        + "\"origin\":{\"location\":{\"latLng\":{\"latitude\":%.8f,\"longitude\":%.8f}}},"
                        + "\"destination\":{\"location\":{\"latLng\":{\"latitude\":%.8f,\"longitude\":%.8f}}},"
                        + "\"travelMode\":\"DRIVE\","
                        + "\"routingPreference\":\"TRAFFIC_UNAWARE\","
                        + "\"computeAlternativeRoutes\":false"
                        + "}",
                origin.latitude(),
                origin.longitude(),
                destination.latitude(),
                destination.longitude());
    }

    private long extractDistanceMeters(JsonNode root) {
        JsonNode distanceNode = root.path("routes").path(0).path("distanceMeters");
        if (distanceNode.isMissingNode() || !distanceNode.isNumber()) {
            String error = extractErrorMessage(root);
            if (error == null) {
                throw new IllegalStateException("Could not parse routes distance from response.");
            }
            throw new IllegalStateException("Routes API error: " + error);
        }
        return distanceNode.asLong();
    }

    private long extractDurationSeconds(JsonNode root) {
        JsonNode durationNode = root.path("routes").path(0).path("duration");
        if (durationNode.isMissingNode() || !durationNode.isTextual()) {
            String error = extractErrorMessage(root);
            if (error == null) {
                throw new IllegalStateException("Could not parse routes duration from response.");
            }
            throw new IllegalStateException("Routes API error: " + error);
        }
        String durationStr = durationNode.asText();
        return Long.parseLong(durationStr.replace("s", ""));
    }

    private String extractErrorMessage(JsonNode root) {
        JsonNode errorNode = root.path("error_message");
        if (errorNode.isMissingNode() || !errorNode.isTextual()) {
            return null;
        }
        return errorNode.asText();
    }

    private String buildCacheKey(GeoPoint origin, GeoPoint destination) {
        return String.format(
                Locale.US,
                "%.6f,%.6f->%.6f,%.6f",
                origin.latitude(),
                origin.longitude(),
                destination.latitude(),
                destination.longitude()
        );
    }

    public Map<String, Long> cacheStats() {
        return routesCache.stats();
    }
}
