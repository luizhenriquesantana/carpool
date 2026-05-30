package com.santana.carpool.geocoding;

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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Service
public class GoogleGeocodingService {
    private static final Logger log = LoggerFactory.getLogger(GoogleGeocodingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String apiKey;
    private final String geocodingBaseUrl;
    private final TtlCache<String, GeoPoint> geocodeCache;

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
        this.geocodeCache = new TtlCache<>(geocodeCacheTtlSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("GoogleGeocodingService initialized (cacheTtl={}s)", geocodeCacheTtlSeconds);
    }

    public GeoPoint geocodeEircode(String eircode) {
        if (eircode == null || eircode.isBlank()) {
            throw new IllegalArgumentException("Eircode is required.");
        }

        // Normalize eircode so equivalent inputs map to the same cache entry.
        String cacheKey = normalizeEircode(eircode);
        GeoPoint cached = geocodeCache.get(cacheKey);
        if (cached != null) {
            log.debug("Geocode cache hit for eircode={}", cacheKey);
            return cached;
        }
        log.debug("Geocode cache miss for eircode={}, calling API", cacheKey);

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
            JsonNode root = MAPPER.readTree(body);
            String status = extractStatus(root);
            if (!"OK".equals(status)) {
                String errorMessage = extractErrorMessage(root);
                if (errorMessage == null) {
                    throw new IllegalStateException("Geocoding API returned status " + status + " for eircode " + eircode);
                }
                throw new IllegalStateException(
                        "Geocoding API returned status " + status + " for eircode " + eircode + ": " + errorMessage
                );
            }

            GeoPoint resolved = extractLocation(root);
            geocodeCache.put(cacheKey, resolved);
            log.debug("Geocoded eircode={} -> ({}, {})", cacheKey, resolved.latitude(), resolved.longitude());
            return resolved;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Geocoding interrupted for eircode={}", eircode, ex);
            throw new IllegalStateException("Failed to geocode eircode " + eircode, ex);
        } catch (IOException ex) {
            log.error("Geocoding I/O failure for eircode={}", eircode, ex);
            throw new IllegalStateException("Failed to geocode eircode " + eircode, ex);
        }
    }

    public GeoPoint geocodePostalCode(String postalCode, String countryCode) {
        return geocodePostalCode(postalCode, countryCode, null, null);
    }

    public GeoPoint geocodePostalCode(String postalCode, String countryCode, String street, String houseNumber) {
        if (postalCode == null || postalCode.isBlank()) {
            throw new IllegalArgumentException("Postal code is required.");
        }
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("Country code is required.");
        }

        String normalizedCountry = countryCode.toUpperCase(Locale.ROOT);
        String normalizedPostalCode = normalizePostalCode(postalCode);
        String cacheKey = buildCacheKey(normalizedPostalCode, normalizedCountry, street, houseNumber);
        GeoPoint cached = geocodeCache.get(cacheKey);
        if (cached != null) {
            log.debug("Geocode cache hit for address={}", cacheKey);
            return cached;
        }
        log.debug("Geocode cache miss for address={}, calling API", cacheKey);

        String addressQuery = buildAddressQuery(normalizedPostalCode, street, houseNumber);
        String encodedAddress = URLEncoder.encode(addressQuery, StandardCharsets.UTF_8);
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
            JsonNode root = MAPPER.readTree(body);
            String status = extractStatus(root);
            if (!"OK".equals(status)) {
                String errorMessage = extractErrorMessage(root);
                if (errorMessage == null) {
                    throw new IllegalStateException("Geocoding API returned status " + status + " for address " + addressQuery + " in " + normalizedCountry);
                }
                throw new IllegalStateException(
                        "Geocoding API returned status " + status + " for address " + addressQuery + " in " + normalizedCountry + ": " + errorMessage
                );
            }

            GeoPoint resolved = extractLocation(root);
            geocodeCache.put(cacheKey, resolved);
            log.debug("Geocoded address={} -> ({}, {})", cacheKey, resolved.latitude(), resolved.longitude());
            return resolved;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Geocoding interrupted for postalCode={} in {}", postalCode, normalizedCountry, ex);
            throw new IllegalStateException("Failed to geocode postal code " + postalCode + " in " + normalizedCountry, ex);
        } catch (IOException ex) {
            log.error("Geocoding I/O failure for postalCode={} in {}", postalCode, normalizedCountry, ex);
            throw new IllegalStateException("Failed to geocode postal code " + postalCode + " in " + normalizedCountry, ex);
        }
    }

    private String buildAddressQuery(String postalCode, String street, String houseNumber) {
        StringBuilder query = new StringBuilder();
        if (street != null && !street.isBlank()) {
            query.append(street.trim());
            if (houseNumber != null && !houseNumber.isBlank()) {
                query.append(" ").append(houseNumber.trim());
            }
            query.append(", ");
        }
        query.append(postalCode);
        return query.toString();
    }

    private String buildCacheKey(String postalCode, String countryCode, String street, String houseNumber) {
        if (street == null || street.isBlank()) {
            return postalCode + "|" + countryCode;
        }
        StringBuilder key = new StringBuilder();
        key.append(street.trim().toUpperCase(Locale.ROOT));
        if (houseNumber != null && !houseNumber.isBlank()) {
            key.append("|").append(houseNumber.trim().toUpperCase(Locale.ROOT));
        }
        key.append("|").append(postalCode).append("|").append(countryCode);
        return key.toString();
    }

    private String extractStatus(JsonNode root) {
        JsonNode statusNode = root.path("status");
        if (statusNode.isMissingNode() || !statusNode.isTextual()) {
            throw new IllegalStateException("Could not parse geocoding status from response.");
        }
        return statusNode.asText();
    }

    private GeoPoint extractLocation(JsonNode root) {
        JsonNode location = root.path("results").path(0).path("geometry").path("location");
        JsonNode latNode = location.path("lat");
        JsonNode lngNode = location.path("lng");
        if (latNode.isMissingNode() || lngNode.isMissingNode()) {
            throw new IllegalStateException("Could not parse geocoding coordinates from response.");
        }
        return new GeoPoint(latNode.asDouble(), lngNode.asDouble());
    }

    private String extractErrorMessage(JsonNode root) {
        JsonNode errorNode = root.path("error_message");
        if (errorNode.isMissingNode() || !errorNode.isTextual()) {
            return null;
        }
        return errorNode.asText();
    }

    private String normalizeEircode(String eircode) {
        return eircode.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String normalizePostalCode(String postalCode) {
        return postalCode.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public Map<String, Long> cacheStats() {
        return geocodeCache.stats();
    }
}
