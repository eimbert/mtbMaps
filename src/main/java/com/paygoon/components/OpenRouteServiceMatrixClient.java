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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@Component
public class OpenRouteServiceMatrixClient {

    private final WebClient webClient;
    private final Duration timeout;
    private final int retryCount;
    private final String apiKey;

    public OpenRouteServiceMatrixClient(
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
    public MatrixResult fetchMatrix(
            String profile,
            List<List<Double>> locations,
            List<Integer> sources,
            List<Integer> destinations,
            List<String> metrics,
            String units
    ) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "ORS API key not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("locations", locations);
        body.put("sources", sources);
        body.put("destinations", destinations);
        body.put("metrics", metrics);
        body.put("units", units);

        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= retryCount; attempt++) {
            try {
                Map<String, Object> response = webClient.post()
                        .uri("/v2/matrix/{profile}", profile)
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

                List<List<Double>> durations = (List<List<Double>>) response.get("durations");
                List<List<Double>> distances = (List<List<Double>>) response.get("distances");
                return new MatrixResult(durations, distances);
            } catch (RuntimeException ex) {
                lastError = ex;
                if (!isRetryable(ex) || attempt == retryCount) {
                    throw ex;
                }
            }
        }

        throw lastError == null ? new IllegalStateException("Unknown ORS matrix error") : lastError;
    }

    private boolean isRetryable(RuntimeException ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof WebClientResponseException wc) {
                int code = wc.getRawStatusCode();
                return code == 408 || code == 425 || code == 429 || code >= 500;
            }
            if (current instanceof ResponseStatusException rs) {
                int code = rs.getStatusCode().value();
                return code == 408 || code == 425 || code == 429 || code >= 500;
            }
            current = current.getCause();
        }
        return ex.getClass().getSimpleName().contains("Timeout");
    }

    public record MatrixResult(List<List<Double>> durations, List<List<Double>> distances) {}
}
