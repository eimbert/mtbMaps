package com.paygoon.components;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Component
public class OpenRouteServiceDirectionsClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceDirectionsClient.class);
    private static final String ROUND_TRIP_PATH = "/v2/directions/{profile}/geojson";

    private final WebClient webClient;
    private final Duration timeout;
    private final int retryCount;
    private final String apiKey;
    private final String baseUrl;

    public OpenRouteServiceDirectionsClient(
            WebClient.Builder builder,
            @Value("${routing.ors.base-url:https://api.openrouteservice.org}") String baseUrl,
            @Value("${routing.ors.timeout-ms:4000}") long timeoutMs,
            @Value("${routing.ors.retry-count:1}") int retryCount,
            @Value("${routing.ors.api-key:}") String apiKey) {

        this.webClient = builder
                .baseUrl(baseUrl)
                .build();
        this.timeout = Duration.ofMillis(Math.max(1000, timeoutMs));
        this.retryCount = Math.max(0, retryCount);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    @SuppressWarnings("unchecked")
    public DirectionsResult fetchRoundTrip(
            String profile,
            List<Double> coordinates,
            double lengthMeters,
            int points,
            int seed
    ) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "ORS API key not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("coordinates", List.of(coordinates));
        body.put("elevation", true);

        Map<String, Object> options = new LinkedHashMap<>();
        Map<String, Object> roundTrip = new LinkedHashMap<>();
        roundTrip.put("length", lengthMeters);
        roundTrip.put("points", points);
        roundTrip.put("seed", seed);
        options.put("round_trip", roundTrip);
        body.put("options", options);

        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            log.info("Calling ORS round-trip endpoint={}{} profile={} payload={} attempt={}/{}",
                    baseUrl, ROUND_TRIP_PATH.replace("{profile}", profile), profile, body, attempt + 1, retryCount + 1);
            try {
                Map<String, Object> response = webClient.post()
                        .uri(ROUND_TRIP_PATH, profile)
                        .header("Authorization", buildAuthorizationHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.valueOf("application/geo+json"))
                        .bodyValue(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError,
                                clientResponse -> clientResponse.bodyToMono(String.class)
                                        .flatMap(err -> Mono.error(new ResponseStatusException(
                                                clientResponse.statusCode(), err))))
                        .bodyToMono(Map.class)
                        .block(timeout);

                if (response == null) {
                    throw new IllegalStateException("Empty ORS response");
                }

                List<Map<String, Object>> features = (List<Map<String, Object>>) response.get("features");
                if (features == null || features.isEmpty()) {
                    throw new IllegalStateException("ORS response missing features");
                }

                Map<String, Object> firstFeature = features.get(0);
                Map<String, Object> geometry = (Map<String, Object>) firstFeature.get("geometry");
                Map<String, Object> properties = (Map<String, Object>) firstFeature.get("properties");
                Map<String, Object> summary = properties == null ? null : (Map<String, Object>) properties.get("summary");
                List<Map<String, Object>> segments = properties == null ? null : (List<Map<String, Object>>) properties.get("segments");
                Map<String, Object> firstSegment = segments == null || segments.isEmpty() ? null : segments.get(0);

                List<List<Double>> routeCoordinates = geometry == null ? null : (List<List<Double>>) geometry.get("coordinates");
                Double distance = firstNonNullNumber(
                        summary == null ? null : summary.get("distance"),
                        firstSegment == null ? null : firstSegment.get("distance"));
                Double duration = firstNonNullNumber(
                        summary == null ? null : summary.get("duration"),
                        firstSegment == null ? null : firstSegment.get("duration"));
                Double ascent = firstNonNullNumber(
                        summary == null ? null : summary.get("ascent"),
                        properties == null ? null : properties.get("ascent"));
                return new DirectionsResult(routeCoordinates, distance, duration, ascent);
            } catch (RuntimeException ex) {
                log.warn("ORS round-trip call failed profile={} payload={} attempt={}/{} message={}",
                        profile, body, attempt + 1, retryCount + 1, ex.getMessage());
                lastError = ex;
                if (attempt == retryCount) {
                    throw ex;
                }
            }
        }

        throw lastError == null ? new IllegalStateException("Unknown ORS directions error") : lastError;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private Double firstNonNullNumber(Object primary, Object fallback) {
        Double primaryNumber = toDouble(primary);
        return primaryNumber != null ? primaryNumber : toDouble(fallback);
    }

    private String buildAuthorizationHeader() {
        return apiKey.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())
                ? apiKey
                : "Bearer " + apiKey;
    }

    public record DirectionsResult(
            List<List<Double>> coordinates,
            Double distanceMeters,
            Double durationSeconds,
            Double ascentMeters) {
    }
}
