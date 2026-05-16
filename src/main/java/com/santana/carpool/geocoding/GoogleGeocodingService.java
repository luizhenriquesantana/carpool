package com.santana.carpool.geocoding;

import com.santana.carpool.domain.GeoPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GoogleGeocodingService {
    private static final Pattern STATUS_PATTERN = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ERROR_MESSAGE_PATTERN = Pattern.compile("\"error_message\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "\"location\"\\s*:\\s*\\{\\s*\"lat\"\\s*:\\s*([-0-9.]+)\\s*,\\s*\"lng\"\\s*:\\s*([-0-9.]+)\\s*\\}"
    );

    private final HttpClient httpClient;
    private final String apiKey;
    private final String geocodingBaseUrl;
    private final long geocodeCacheTtlSeconds;
    private final Map<String, CacheEntry<GeoPoint>> geocodeCache = new ConcurrentHashMap<>();
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);

    public GoogleGeocodingService(
            @Value("${googleMaps.apiKey:}") String apiKey,
            @Value("${googleMaps.geocodingBaseUrl:https://maps.googleapis.com/maps/api/geocode/json}") String geocodingBaseUrl,
            @Value("${googleMaps.geocodeCacheTtlSeconds:86400}") long geocodeCacheTtlSeconds
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Google Maps API key is required.");
        }
        this.apiKey = apiKey;
        this.geocodingBaseUrl = geocodingBaseUrl;
        this.geocodeCacheTtlSeconds = geocodeCacheTtlSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public GeoPoint geocodeEircode(String eircode) {
        if (eircode == null || eircode.isBlank()) {
            throw new IllegalArgumentException("Eircode is required.");
        }

        // Normalize eircode so equivalent inputs map to the same cache entry.
        String cacheKey = normalizeEircode(eircode);
        GeoPoint cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        String encodedAddress = URLEncoder.encode(eircode, StandardCharsets.UTF_8);
        String requestUrl = geocodingBaseUrl
                + "?address=" + encodedAddress
                + "&components=country:IE"
                + "&key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Geocoding request failed with HTTP status " + response.statusCode());
            }

            String body = response.body();
            String status = extractStatus(body);
            if (!"OK".equals(status)) {
                String errorMessage = extractErrorMessage(body);
                if (errorMessage == null) {
                    throw new IllegalStateException("Geocoding API returned status " + status + " for eircode " + eircode);
                }
                throw new IllegalStateException(
                        "Geocoding API returned status " + status + " for eircode " + eircode + ": " + errorMessage
                );
            }

            GeoPoint resolved = extractLocation(body);
            // Persist resolved coordinates to avoid repeated external API calls.
            putInCache(cacheKey, resolved);
            return resolved;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to geocode eircode " + eircode, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to geocode eircode " + eircode, ex);
        }
    }

    public GeoPoint geocodePostalCode(String postalCode, String countryCode) {
        if (postalCode == null || postalCode.isBlank()) {
            throw new IllegalArgumentException("Postal code is required.");
        }
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("Country code is required.");
        }

        // Normalize country code and postal code for cache key
        String normalizedCountry = countryCode.toUpperCase(Locale.ROOT);
        String cacheKey = normalizePostalCode(postalCode) + "|" + normalizedCountry;
        GeoPoint cached = getFromCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        String encodedAddress = URLEncoder.encode(postalCode, StandardCharsets.UTF_8);
        String requestUrl = geocodingBaseUrl
                + "?address=" + encodedAddress
                + "&components=country:" + normalizedCountry
                + "&key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Geocoding request failed with HTTP status " + response.statusCode());
            }

            String body = response.body();
            String status = extractStatus(body);
            if (!"OK".equals(status)) {
                String errorMessage = extractErrorMessage(body);
                if (errorMessage == null) {
                    throw new IllegalStateException("Geocoding API returned status " + status + " for postal code " + postalCode + " in " + normalizedCountry);
                }
                throw new IllegalStateException(
                        "Geocoding API returned status " + status + " for postal code " + postalCode + " in " + normalizedCountry + ": " + errorMessage
                );
            }

            GeoPoint resolved = extractLocation(body);
            putInCache(cacheKey, resolved);
            return resolved;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to geocode postal code " + postalCode + " in " + normalizedCountry, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to geocode postal code " + postalCode + " in " + normalizedCountry, ex);
        }
    }

    private String extractStatus(String jsonBody) {
        Matcher matcher = STATUS_PATTERN.matcher(jsonBody);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not parse geocoding status from response.");
        }
        return matcher.group(1);
    }

    private GeoPoint extractLocation(String jsonBody) {
        Matcher matcher = LOCATION_PATTERN.matcher(jsonBody);
        if (!matcher.find()) {
            throw new IllegalStateException("Could not parse geocoding coordinates from response.");
        }

        double lat = Double.parseDouble(matcher.group(1));
        double lng = Double.parseDouble(matcher.group(2));
        return new GeoPoint(lat, lng);
    }

    private String extractErrorMessage(String jsonBody) {
        Matcher matcher = ERROR_MESSAGE_PATTERN.matcher(jsonBody);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private String normalizeEircode(String eircode) {
        return eircode.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String normalizePostalCode(String postalCode) {
        return postalCode.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private GeoPoint getFromCache(String key) {
        CacheEntry<GeoPoint> entry = geocodeCache.get(key);
        if (entry == null) {
            cacheMissCount.incrementAndGet();
            return null;
        }

        if (entry.expiresAtEpochMillis < System.currentTimeMillis()) {
            geocodeCache.remove(key);
            cacheMissCount.incrementAndGet();
            return null;
        }

        cacheHitCount.incrementAndGet();
        return entry.value;
    }

    private void putInCache(String key, GeoPoint value) {
        long expiresAt = System.currentTimeMillis() + (geocodeCacheTtlSeconds * 1000L);
        geocodeCache.put(key, new CacheEntry<>(value, expiresAt));
    }

    private record CacheEntry<T>(T value, long expiresAtEpochMillis) {
    }

    public Map<String, Long> cacheStats() {
        return Map.of(
                "hits", cacheHitCount.get(),
                "misses", cacheMissCount.get(),
                "size", (long) geocodeCache.size()
        );
    }
}
