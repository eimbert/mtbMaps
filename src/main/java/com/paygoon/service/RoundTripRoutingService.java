package com.paygoon.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.paygoon.components.OpenRouteServiceDirectionsClient;
import com.paygoon.dto.RoundTripRouteRequest;
import com.paygoon.dto.RoundTripRouteResponse;

@Service
public class RoundTripRoutingService {

    private static final Set<String> ALLOWED_PROFILES = Set.of(
            "cycling-regular", "cycling-road", "cycling-mountain", "cycling-electric");
    private static final Set<String> ALLOWED_COMPLEXITIES = Set.of("simple", "medium", "technical");
    private static final Set<String> ALLOWED_AVOID_FEATURES = Set.of("ferries", "steps", "tollways");
    private static final int AVOID_ASPHALT_SEED_ATTEMPTS = 8;
    private static final Set<Integer> ASPHALT_SURFACE_CODES = Set.of(1, 3, 4, 18);
    private static final Set<Integer> TRACK_PATH_WAYTYPE_CODES = Set.of(4, 5);

    private final OpenRouteServiceDirectionsClient directionsClient;

    public RoundTripRoutingService(OpenRouteServiceDirectionsClient directionsClient) {
        this.directionsClient = directionsClient;
    }

    public RoundTripRouteResponse generateRoundTrip(RoundTripRouteRequest request) {
        validateRequest(request);

        ComplexityParams complexityParams = mapComplexity(request.complexity());
        ResolvedPreferences resolved = resolvePreferences(request, complexityParams);

        RuntimeException lastError = null;
        List<String> warnings = new ArrayList<>();

        for (int fallbackLevel = 0; fallbackLevel <= 2; fallbackLevel++) {
            try {
                AttemptOutcome outcome = resolved.multiSeedAvoidAsphalt()
                        ? executeAvoidAsphaltAttempts(request, complexityParams, resolved, fallbackLevel)
                        : executeSingleAttempt(request, complexityParams, resolved, fallbackLevel);

                if (outcome.fallbackLevel() > 0) {
                    warnings.add(0, "Route generated after relaxing constraints to fallback level " + fallbackLevel);
                }
                warnings.addAll(outcome.warnings());

                return new RoundTripRouteResponse(
                        new RoundTripRouteResponse.Geometry(outcome.result().coordinates()),
                        outcome.result().distanceMeters(),
                        outcome.result().durationSeconds(),
                        outcome.result().ascentMeters(),
                        outcome.appliedOptions(),
                        outcome.fallbackLevel(),
                        warnings.isEmpty() ? null : warnings);
            } catch (ResponseStatusException ex) {
                warnings.add("Fallback " + fallbackLevel + ": ORS upstream error");
                lastError = ex;
            } catch (RuntimeException ex) {
                warnings.add("Fallback " + fallbackLevel + ": unable to generate route");
                lastError = ex;
            }
        }

        if (lastError != null) {
            throw new RoundTripRoutingException(HttpStatus.BAD_GATEWAY, "ORS_UPSTREAM_ERROR",
                    "Unable to generate round trip route with current preferences");
        }

        throw new RoundTripRoutingException(HttpStatus.BAD_GATEWAY, "ORS_INVALID_RESPONSE",
                "ORS returned route without usable geometry");
    }

    private AttemptOutcome executeSingleAttempt(
            RoundTripRouteRequest request,
            ComplexityParams complexityParams,
            ResolvedPreferences preferences,
            int fallbackLevel) {
        Map<String, Object> options = buildOrsOptions(request, complexityParams, preferences, fallbackLevel, complexityParams.seed());
        OpenRouteServiceDirectionsClient.DirectionsResult result = directionsClient.fetchRoundTrip(
                request.profile(),
                List.of(request.start().lon(), request.start().lat()),
                options,
                preferences.extraInfo());

        if (!hasUsableGeometry(result.coordinates())) {
            throw new IllegalStateException("ORS returned route without usable geometry");
        }

        return new AttemptOutcome(result, options, fallbackLevel, List.of());
    }

    private AttemptOutcome executeAvoidAsphaltAttempts(
            RoundTripRouteRequest request,
            ComplexityParams complexityParams,
            ResolvedPreferences preferences,
            int fallbackLevel) {

        ScoredAttempt best = null;
        RuntimeException lastError = null;
        int successfulAttempts = 0;
        List<String> warnings = new ArrayList<>();

        for (int i = 0; i < AVOID_ASPHALT_SEED_ATTEMPTS; i++) {
            int seed = complexityParams.seed() + i;
            Map<String, Object> options = buildOrsOptions(request, complexityParams, preferences, fallbackLevel, seed);
            try {
                OpenRouteServiceDirectionsClient.DirectionsResult result = directionsClient.fetchRoundTrip(
                        request.profile(),
                        List.of(request.start().lon(), request.start().lat()),
                        options,
                        preferences.extraInfo());

                if (!hasUsableGeometry(result.coordinates())) {
                    warnings.add("Seed " + seed + " returned invalid geometry");
                    continue;
                }

                successfulAttempts++;
                RouteScore score = scoreRoute(result.extras(), result.distanceMeters());
                ScoredAttempt candidate = new ScoredAttempt(result, options, seed, score);
                if (best == null || candidate.score().isBetterThan(best.score())) {
                    best = candidate;
                }
            } catch (RuntimeException ex) {
                lastError = ex;
            }
        }

        if (best == null) {
            if (lastError != null) {
                throw lastError;
            }
            throw new IllegalStateException("All avoid-asphalt attempts failed to produce usable geometry");
        }

        Map<String, Object> appliedOptions = new LinkedHashMap<>(best.options());
        appliedOptions.put("traceability", buildTraceability(successfulAttempts, best));

        if (best.score().surfaceCoverage() < 0.5) {
            warnings.add("Low surface data quality in this area; scoring used waytype/distance fallback");
        }

        return new AttemptOutcome(best.result(), appliedOptions, fallbackLevel, warnings);
    }

    private Map<String, Object> buildTraceability(int successfulAttempts, ScoredAttempt best) {
        Map<String, Object> traceability = new LinkedHashMap<>();
        traceability.put("mode", "avoid-asphalt");
        traceability.put("seedAttempts", AVOID_ASPHALT_SEED_ATTEMPTS);
        traceability.put("successfulAttempts", successfulAttempts);
        traceability.put("selectedSeed", best.seed());
        traceability.put("asphaltPercent", toPercent(best.score().asphaltRatio()));
        traceability.put("trackPathPercent", toPercent(best.score().trackPathRatio()));
        traceability.put("surfaceCoveragePercent", toPercent(best.score().surfaceCoverage()));
        traceability.put("distanceMeters", best.score().distanceMeters());
        return traceability;
    }

    private Double toPercent(double ratio) {
        return Math.round(ratio * 10_000.0) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private RouteScore scoreRoute(Map<String, Object> extras, Double distanceMeters) {
        SurfaceStats surfaceStats = readSurfaceStats(extras == null ? null : (Map<String, Object>) extras.get("surface"));
        WaytypeStats waytypeStats = readWaytypeStats(extras == null ? null : (Map<String, Object>) extras.get("waytype"));

        double effectiveDistance = distanceMeters == null || distanceMeters <= 0 ? 1.0 : distanceMeters;
        double surfaceCoverage = Math.min(1.0, surfaceStats.totalWeight() / effectiveDistance);

        return new RouteScore(
                surfaceStats.asphaltRatio(),
                waytypeStats.trackPathRatio(),
                surfaceCoverage,
                effectiveDistance);
    }

    @SuppressWarnings("unchecked")
    private SurfaceStats readSurfaceStats(Map<String, Object> surfaceExtra) {
        if (surfaceExtra == null) {
            return new SurfaceStats(0.0, 0.0);
        }

        List<List<Number>> values = (List<List<Number>>) surfaceExtra.get("values");
        if (values == null || values.isEmpty()) {
            return new SurfaceStats(0.0, 0.0);
        }

        double total = 0.0;
        double asphalt = 0.0;
        for (List<Number> valueRow : values) {
            if (valueRow == null || valueRow.size() < 3) {
                continue;
            }
            int code = valueRow.get(2).intValue();
            double weight = segmentWeight(valueRow);
            total += weight;
            if (ASPHALT_SURFACE_CODES.contains(code)) {
                asphalt += weight;
            }
        }

        double asphaltRatio = total > 0 ? asphalt / total : 1.0;
        return new SurfaceStats(total, asphaltRatio);
    }

    @SuppressWarnings("unchecked")
    private WaytypeStats readWaytypeStats(Map<String, Object> waytypeExtra) {
        if (waytypeExtra == null) {
            return new WaytypeStats(0.0);
        }

        List<List<Number>> values = (List<List<Number>>) waytypeExtra.get("values");
        if (values == null || values.isEmpty()) {
            return new WaytypeStats(0.0);
        }

        double total = 0.0;
        double trackPath = 0.0;
        for (List<Number> valueRow : values) {
            if (valueRow == null || valueRow.size() < 3) {
                continue;
            }
            int code = valueRow.get(2).intValue();
            double weight = segmentWeight(valueRow);
            total += weight;
            if (TRACK_PATH_WAYTYPE_CODES.contains(code)) {
                trackPath += weight;
            }
        }

        return new WaytypeStats(total > 0 ? trackPath / total : 0.0);
    }

    private double segmentWeight(List<Number> valueRow) {
        if (valueRow.size() > 3 && valueRow.get(3) != null && valueRow.get(3).doubleValue() > 0) {
            return valueRow.get(3).doubleValue();
        }
        if (valueRow.get(0) != null && valueRow.get(1) != null) {
            return Math.max(1.0, valueRow.get(1).doubleValue() - valueRow.get(0).doubleValue());
        }
        return 1.0;
    }

    private ComplexityParams mapComplexity(String complexity) {
        return switch (complexity) {
            case "simple" -> new ComplexityParams(2, 11, 0.35, 0.35);
            case "technical" -> new ComplexityParams(5, 37, 0.8, 0.7);
            case "medium" -> new ComplexityParams(3, 23, 0.6, 0.6);
            default -> throw new RoundTripRoutingException(HttpStatus.BAD_REQUEST, "INVALID_COMPLEXITY",
                    "Unsupported complexity value");
        };
    }

    private ResolvedPreferences resolvePreferences(RoundTripRouteRequest request, ComplexityParams params) {
        RoundTripRouteRequest.Preferences preferences = request.preferences();

        String mode = preferences == null || preferences.mode() == null ? "balanced" : preferences.mode();
        double strictness = preferences == null || preferences.strictness() == null ? 1.0 : preferences.strictness();

        double green = params.baseGreen();
        double quiet = params.baseQuiet();
        Double steepnessDifficulty = null;

        if ("trails".equals(mode)) {
            green = Math.min(1.0, green + 0.15);
            quiet = Math.min(1.0, quiet + 0.05);
        } else if ("anti-asphalt".equals(mode) || "avoid-asphalt".equals(mode)) {
            green = 0.9;
            quiet = Math.max(quiet, 0.7);
        }

        if (preferences != null && preferences.weightings() != null) {
            if (preferences.weightings().green() != null) {
                green = preferences.weightings().green();
            }
            if (preferences.weightings().quiet() != null) {
                quiet = preferences.weightings().quiet();
            }
            steepnessDifficulty = preferences.weightings().steepness_difficulty();
        }

        Set<String> avoidFeatures = new LinkedHashSet<>();
        if (preferences != null && preferences.avoidFeatures() != null) {
            avoidFeatures.addAll(preferences.avoidFeatures());
        }

        Set<String> extraInfo = new LinkedHashSet<>();
        if (preferences != null && preferences.extraInfo() != null) {
            extraInfo.addAll(preferences.extraInfo());
        }
        if ("avoid-asphalt".equals(mode)) {
            extraInfo.add("surface");
            extraInfo.add("waytype");
        }

        return new ResolvedPreferences(List.copyOf(avoidFeatures), List.copyOf(extraInfo), green, quiet,
                steepnessDifficulty, strictness, mode);
    }

    private Map<String, Object> buildOrsOptions(
            RoundTripRouteRequest request,
            ComplexityParams complexityParams,
            ResolvedPreferences preferences,
            int fallbackLevel,
            int seed) {

        double factor = switch (fallbackLevel) {
            case 0 -> 1.0;
            case 1 -> Math.max(0.2, 1.0 - (0.35 * preferences.strictness()));
            default -> Math.max(0.2, 1.0 - (0.6 * preferences.strictness()));
        };

        Map<String, Object> options = new LinkedHashMap<>();
        Map<String, Object> roundTrip = new LinkedHashMap<>();
        roundTrip.put("length", request.lengthKm() * 1_000d);
        roundTrip.put("points", complexityParams.points());
        roundTrip.put("seed", seed);
        options.put("round_trip", roundTrip);

        Map<String, Object> profileParams = new LinkedHashMap<>();
        Map<String, Object> weightings = new LinkedHashMap<>();
        weightings.put("green", clamp01(preferences.green() * factor));
        weightings.put("quiet", clamp01(preferences.quiet() * factor));
        if (preferences.steepnessDifficulty() != null) {
            weightings.put("steepness_difficulty", clampSteepness(preferences.steepnessDifficulty()));
        }
        profileParams.put("weightings", weightings);
        options.put("profile_params", profileParams);

        if (!preferences.avoidFeatures().isEmpty()) {
            options.put("avoid_features", preferences.avoidFeatures());
        }

        return options;
    }

    private boolean hasUsableGeometry(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            return false;
        }
        return coordinates.stream().allMatch(this::isValidCoordinate);
    }

    private boolean isValidCoordinate(List<Double> coordinate) {
        if (coordinate == null || coordinate.size() < 2) {
            return false;
        }
        Double lon = coordinate.get(0);
        Double lat = coordinate.get(1);
        return lon != null && lat != null && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private void validateRequest(RoundTripRouteRequest request) {
        if (request == null || request.start() == null) {
            throw new RoundTripRoutingException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is required");
        }

        if (!ALLOWED_PROFILES.contains(request.profile())) {
            throw new RoundTripRoutingException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE", "Unsupported profile value");
        }

        if (!ALLOWED_COMPLEXITIES.contains(request.complexity())) {
            throw new RoundTripRoutingException(HttpStatus.BAD_REQUEST, "INVALID_COMPLEXITY", "Unsupported complexity value");
        }

        if (request.lengthKm() == null || request.lengthKm() < 5 || request.lengthKm() > 200) {
            throw new RoundTripRoutingException(HttpStatus.BAD_REQUEST, "INVALID_LENGTH", "lengthKm must be between 5 and 200");
        }

        Double lat = request.start().lat();
        Double lon = request.start().lon();
        if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            throw new RoundTripRoutingException(HttpStatus.BAD_REQUEST, "INVALID_START", "start lat/lon are invalid");
        }

        RoundTripRouteRequest.Preferences preferences = request.preferences();
        if (preferences != null && preferences.avoidFeatures() != null) {
            for (String feature : preferences.avoidFeatures()) {
                if (!ALLOWED_AVOID_FEATURES.contains(feature)) {
                    throw new RoundTripRoutingException(HttpStatus.BAD_REQUEST, "INVALID_AVOID_FEATURE",
                            "Unsupported avoid feature: " + feature);
                }
            }
        }
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private int clampSteepness(double value) {
        return (int) Math.max(0, Math.min(6, Math.round(value)));
    }

    private record ComplexityParams(int points, int seed, double baseGreen, double baseQuiet) {
    }

    private record ResolvedPreferences(
            List<String> avoidFeatures,
            List<String> extraInfo,
            double green,
            double quiet,
            Double steepnessDifficulty,
            double strictness,
            String mode) {

        private boolean multiSeedAvoidAsphalt() {
            return "avoid-asphalt".equals(mode);
        }
    }

    private record AttemptOutcome(
            OpenRouteServiceDirectionsClient.DirectionsResult result,
            Map<String, Object> appliedOptions,
            int fallbackLevel,
            List<String> warnings) {
    }

    private record SurfaceStats(double totalWeight, double asphaltRatio) {
    }

    private record WaytypeStats(double trackPathRatio) {
    }

    private record RouteScore(double asphaltRatio, double trackPathRatio, double surfaceCoverage, double distanceMeters) {

        private boolean isBetterThan(RouteScore currentBest) {
            if (surfaceCoverage >= 0.2 && currentBest.surfaceCoverage >= 0.2) {
                int asphaltComparison = Double.compare(asphaltRatio, currentBest.asphaltRatio);
                if (asphaltComparison != 0) {
                    return asphaltComparison < 0;
                }
            }

            int trackComparison = Double.compare(trackPathRatio, currentBest.trackPathRatio);
            if (trackComparison != 0) {
                return trackComparison > 0;
            }

            return Double.compare(distanceMeters, currentBest.distanceMeters) < 0;
        }
    }

    private record ScoredAttempt(
            OpenRouteServiceDirectionsClient.DirectionsResult result,
            Map<String, Object> options,
            int seed,
            RouteScore score) {
    }
}
