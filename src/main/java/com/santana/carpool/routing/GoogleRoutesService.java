package com.santana.carpool.routing;

import com.santana.carpool.cache.TtlCache;
import com.santana.carpool.domain.GeoPoint;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleRoutesService {
    private static final Pattern DISTANCE_PATTERN = Pattern.compile("\\\"distanceMeters\\\"\\s*:\\s*(\\d+)");
    private static final Pattern DURATION_PATTERN = Pattern.compile("\\\"duration\\\"\\s*:\\s*\\\"(\\d+)s\\\"");
    private static final Pattern ERROR_MESSAGE_PATTERN = Pattern.compile("\\\"error_message\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

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
    }

    public RouteLegMetrics computeDrivingLeg(GeoPoint origin, GeoPoint destination) {
        // Route metrics are cached per origin/destination pair to cut API latency and cost.
        String cacheKey = buildCacheKey(origin, destination);
        RouteLegMetrics cached = routesCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

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
            long distanceMeters = extractDistanceMeters(body);
            long durationSeconds = extractDurationSeconds(body);
            RouteLegMetrics metrics = new RouteLegMetrics(distanceMeters / 1000.0, durationSeconds);
            // Save successful response for subsequent route-planning requests.
            routesCache.put(cacheKey, metrics);
            return metrics;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Routes request interrupted.", ex);
        } catch (IOException ex) {
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

    private long extractDistanceMeters(String body) {
        Matcher matcher = DISTANCE_PATTERN.matcher(body);
        if (!matcher.find()) {
            String error = extractErrorMessage(body);
            if (error == null) {
                throw new IllegalStateException("Could not parse routes distance from response.");
            }
            throw new IllegalStateException("Routes API error: " + error);
        }
        return Long.parseLong(matcher.group(1));
    }

    private long extractDurationSeconds(String body) {
        Matcher matcher = DURATION_PATTERN.matcher(body);
        if (!matcher.find()) {
            String error = extractErrorMessage(body);
            if (error == null) {
                throw new IllegalStateException("Could not parse routes duration from response.");
            }
            throw new IllegalStateException("Routes API error: " + error);
        }
        return Long.parseLong(matcher.group(1));
    }

    private String extractErrorMessage(String body) {
        Matcher matcher = ERROR_MESSAGE_PATTERN.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
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
