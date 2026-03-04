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
            Map<String, Object> options = buildOrsOptions(request, complexityParams, resolved, fallbackLevel);

            try {
                OpenRouteServiceDirectionsClient.DirectionsResult result = directionsClient.fetchRoundTrip(
                        request.profile(),
                        List.of(request.start().lon(), request.start().lat()),
                        options);

                if (!hasUsableGeometry(result.coordinates())) {
                    warnings.add("Fallback " + fallbackLevel + ": ORS returned route without usable geometry");
                    continue;
                }

                if (fallbackLevel > 0) {
                    warnings.add(0, "Route generated after relaxing constraints to fallback level " + fallbackLevel);
                }

                return new RoundTripRouteResponse(
                        new RoundTripRouteResponse.Geometry(result.coordinates()),
                        result.distanceMeters(),
                        result.durationSeconds(),
                        result.ascentMeters(),
                        options,
                        fallbackLevel,
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
        }

        Set<String> avoidFeatures = new LinkedHashSet<>();
        if (preferences != null && preferences.avoidFeatures() != null) {
            avoidFeatures.addAll(preferences.avoidFeatures());
        }

        return new ResolvedPreferences(List.copyOf(avoidFeatures), green, quiet, strictness, mode);
    }

    private Map<String, Object> buildOrsOptions(
            RoundTripRouteRequest request,
            ComplexityParams complexityParams,
            ResolvedPreferences preferences,
            int fallbackLevel) {

        double factor = switch (fallbackLevel) {
            case 0 -> 1.0;
            case 1 -> Math.max(0.2, 1.0 - (0.35 * preferences.strictness()));
            default -> Math.max(0.2, 1.0 - (0.6 * preferences.strictness()));
        };

        Map<String, Object> options = new LinkedHashMap<>();
        Map<String, Object> roundTrip = new LinkedHashMap<>();
        roundTrip.put("length", request.lengthKm() * 1_000d);
        roundTrip.put("points", complexityParams.points());
        roundTrip.put("seed", complexityParams.seed());
        options.put("round_trip", roundTrip);

        Map<String, Object> profileParams = new LinkedHashMap<>();
        Map<String, Object> weightings = new LinkedHashMap<>();
        weightings.put("green", clamp01(preferences.green() * factor));
        weightings.put("quiet", clamp01(preferences.quiet() * factor));
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

    private record ComplexityParams(int points, int seed, double baseGreen, double baseQuiet) {
    }

    private record ResolvedPreferences(
            List<String> avoidFeatures,
            double green,
            double quiet,
            double strictness,
            String mode) {
    }
}
