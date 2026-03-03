package com.paygoon.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.paygoon.components.OpenRouteServiceMatrixClient;
import com.paygoon.dto.ProximityMatrixRequest;
import com.paygoon.dto.ProximityMatrixResponse;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class RoutingMatrixService {

    private static final Logger log = LoggerFactory.getLogger(RoutingMatrixService.class);

    private final OpenRouteServiceMatrixClient orsClient;
    private final int maxCandidates;
    private final int requestsPerMinute;
    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();

    public RoutingMatrixService(
            OpenRouteServiceMatrixClient orsClient,
            @Value("${routing.matrix.max-candidates:10}") int maxCandidates,
            @Value("${routing.matrix.rate-limit.requests-per-minute:60}") int requestsPerMinute) {
        this.orsClient = orsClient;
        this.maxCandidates = maxCandidates;
        this.requestsPerMinute = requestsPerMinute;
    }

    public ProximityMatrixResponse rankByRoad(ProximityMatrixRequest request, Authentication authentication, HttpServletRequest httpRequest) {
        validateRequest(request);
        enforceRateLimit(authentication, httpRequest);

        long startNs = System.nanoTime();

        List<ProximityMatrixRequest.Candidate> candidates = request.candidates();
        List<List<Double>> locations = new ArrayList<>();
        locations.add(List.of(request.origin().lon(), request.origin().lat()));

        Map<Integer, ProximityMatrixRequest.Candidate> candidateByDestination = new HashMap<>();
        List<Integer> destinations = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            int destinationIndex = i + 1;
            ProximityMatrixRequest.Candidate candidate = candidates.get(i);
            locations.add(List.of(candidate.startLon(), candidate.startLat()));
            destinations.add(destinationIndex);
            candidateByDestination.put(destinationIndex, candidate);
        }

        try {
            OpenRouteServiceMatrixClient.MatrixResult matrix = orsClient.fetchMatrix(
                    request.profile(),
                    locations,
                    List.of(0),
                    destinations,
                    List.of("duration", "distance"),
                    "m");

            List<ProximityMatrixResponse.OrderedItem> ordered = buildMatrixRanking(matrix, candidates, candidateByDestination);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            log.info("matrix-rank strategyUsed=matrix candidates={} latencyMs={}", candidates.size(), latencyMs);
            return new ProximityMatrixResponse("matrix", ordered);
        } catch (RuntimeException ex) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            String reason = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName());
            log.warn("matrix-rank strategyUsed=haversine candidates={} latencyMs={} fallbackReason={}",
                    candidates.size(), latencyMs, reason);
            return new ProximityMatrixResponse("haversine", buildHaversineRanking(candidates));
        }
    }

    private List<ProximityMatrixResponse.OrderedItem> buildMatrixRanking(
            OpenRouteServiceMatrixClient.MatrixResult matrix,
            List<ProximityMatrixRequest.Candidate> candidates,
            Map<Integer, ProximityMatrixRequest.Candidate> candidateByDestination) {

        List<List<Double>> durations = matrix.durations();
        List<List<Double>> distances = matrix.distances();

        if (durations == null || durations.isEmpty() || durations.get(0) == null) {
            throw new IllegalStateException("ORS matrix missing durations");
        }

        List<Double> rowDurations = durations.get(0);
        List<Double> rowDistances = (distances != null && !distances.isEmpty()) ? distances.get(0) : null;

        List<SortableCandidate> sortable = new ArrayList<>();
        for (int destination = 1; destination <= candidates.size(); destination++) {
            ProximityMatrixRequest.Candidate candidate = candidateByDestination.get(destination);
            Double duration = destination <= rowDurations.size() ? rowDurations.get(destination - 1) : null;
            Double distance = rowDistances != null && destination <= rowDistances.size() ? rowDistances.get(destination - 1) : null;
            sortable.add(new SortableCandidate(candidate, duration, distance));
        }

        sortable.sort(Comparator
                .comparing(SortableCandidate::duration, Comparator.nullsLast(Double::compareTo))
                .thenComparing(sc -> sc.candidate().haversineKm())
                .thenComparing(sc -> sc.candidate().trackId()));

        List<ProximityMatrixResponse.OrderedItem> ordered = new ArrayList<>();
        AtomicInteger rank = new AtomicInteger(0);
        sortable.forEach(item -> ordered.add(new ProximityMatrixResponse.OrderedItem(
                item.candidate().trackId(),
                rank.getAndIncrement(),
                toIntegerMeters(item.distance()),
                toIntegerSeconds(item.duration()))));
        return ordered;
    }

    private List<ProximityMatrixResponse.OrderedItem> buildHaversineRanking(List<ProximityMatrixRequest.Candidate> candidates) {
        List<ProximityMatrixRequest.Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparing(ProximityMatrixRequest.Candidate::haversineKm)
                .thenComparing(ProximityMatrixRequest.Candidate::trackId));

        List<ProximityMatrixResponse.OrderedItem> ordered = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            ProximityMatrixRequest.Candidate candidate = sorted.get(i);
            ordered.add(new ProximityMatrixResponse.OrderedItem(candidate.trackId(), i, null, null));
        }
        return ordered;
    }

    private void validateRequest(ProximityMatrixRequest request) {
        if (!"driving-car".equals(request.profile())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "profile must be driving-car");
        }

        validateCoordinates(request.origin().lat(), request.origin().lon(), "origin");

        if (request.candidates() == null || request.candidates().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidates cannot be empty");
        }
        if (request.candidates().size() > maxCandidates) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidates max is " + maxCandidates);
        }

        for (ProximityMatrixRequest.Candidate candidate : request.candidates()) {
            validateCoordinates(candidate.startLat(), candidate.startLon(), "candidate " + candidate.trackId());
        }

        if (request.options() == null || request.options().metrics() == null
                || !request.options().metrics().contains("duration")
                || !request.options().metrics().contains("distance")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options.metrics must include duration and distance");
        }

        if (!"m".equals(request.options().units())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "options.units must be m");
        }
    }

    private void validateCoordinates(Double lat, Double lon, String field) {
        if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " has invalid coordinates");
        }
    }

    private void enforceRateLimit(Authentication authentication, HttpServletRequest request) {
        String key;
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            key = "user:" + authentication.getName();
        } else {
            key = "ip:" + request.getRemoteAddr();
        }

        RateWindow window = rateWindows.computeIfAbsent(key, ignored -> new RateWindow(System.currentTimeMillis(), 0));
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.windowStartMs >= 60_000) {
                window.windowStartMs = now;
                window.requests = 0;
            }
            window.requests++;
            if (window.requests > requestsPerMinute) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "matrix-rank rate limit exceeded");
            }
        }
    }

    private Integer toIntegerMeters(Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    private Integer toIntegerSeconds(Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    private record SortableCandidate(ProximityMatrixRequest.Candidate candidate, Double duration, Double distance) {}

    private static class RateWindow {
        private long windowStartMs;
        private int requests;

        private RateWindow(long windowStartMs, int requests) {
            this.windowStartMs = windowStartMs;
            this.requests = requests;
        }
    }
}
