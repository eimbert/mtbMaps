package com.paygoon.components;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Component
public class OpenRouteServiceDirectionsClient {

    private final WebClient webClient;
    private final Duration timeout;
    private final int retryCount;
    private final String apiKey;

    public OpenRouteServiceDirectionsClient(
            WebClient.Builder builder,
            @Value("${routing.ors.base-url:https://api.openrouteservice.org}") String baseUrl,
            @Value("${routing.ors.timeout-ms:4000}") long timeoutMs,
            @Value("${routing.ors.retry-count:1}") int retryCount,
            @Value("${routing.ors.api-key:}") String apiKey) {

        this.webClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
        this.timeout = Duration.ofMillis(Math.max(1000, timeoutMs));
        this.retryCount = Math.max(0, retryCount);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
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
        body.put("coordinates", coordinates);
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
            try {
                Map<String, Object> response = webClient.post()
                        .uri("/v2/directions/{profile}/json", profile)
                        .header("Authorization", apiKey)
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

                List<List<Double>> routeCoordinates = geometry == null ? null : (List<List<Double>>) geometry.get("coordinates");
                Double distance = toDouble(summary == null ? null : summary.get("distance"));
                Double duration = toDouble(summary == null ? null : summary.get("duration"));
                Double ascent = toDouble(summary == null ? null : summary.get("ascent"));
                return new DirectionsResult(routeCoordinates, distance, duration, ascent);
            } catch (RuntimeException ex) {
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

    public record DirectionsResult(
            List<List<Double>> coordinates,
            Double distanceMeters,
            Double durationSeconds,
            Double ascentMeters) {
    }
}
