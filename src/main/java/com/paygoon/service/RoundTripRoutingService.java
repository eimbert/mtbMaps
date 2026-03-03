package com.paygoon.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.paygoon.components.OpenRouteServiceDirectionsClient;
import com.paygoon.dto.RoundTripRouteRequest;
import com.paygoon.dto.RoundTripRouteResponse;

@Service
public class RoundTripRoutingService {

    private final OpenRouteServiceDirectionsClient directionsClient;

    public RoundTripRoutingService(OpenRouteServiceDirectionsClient directionsClient) {
        this.directionsClient = directionsClient;
    }

    public RoundTripRouteResponse generateRoundTrip(RoundTripRouteRequest request) {
        validateRequest(request);

        ComplexityParams complexityParams = mapComplexity(request.complexity());

        OpenRouteServiceDirectionsClient.DirectionsResult result;
        try {
            result = directionsClient.fetchRoundTrip(
                    request.profile(),
                    List.of(request.start().lon(), request.start().lat()),
                    request.lengthKm() * 1_000d,
                    complexityParams.points(),
                    complexityParams.seed());
        } catch (ResponseStatusException ex) {
            throw new RoundTripRoutingException(HttpStatus.BAD_GATEWAY, "ORS_UPSTREAM_ERROR",
                    "Error from ORS round trip endpoint");
        } catch (RuntimeException ex) {
            throw new RoundTripRoutingException(HttpStatus.BAD_GATEWAY, "ORS_UPSTREAM_ERROR",
                    "Unable to generate round trip route");
        }

        if (!hasUsableGeometry(result.coordinates())) {
            throw new RoundTripRoutingException(HttpStatus.BAD_GATEWAY, "ORS_INVALID_RESPONSE",
                    "ORS returned route without usable geometry");
        }

        return new RoundTripRouteResponse(
                new RoundTripRouteResponse.Geometry(result.coordinates()),
                result.distanceMeters(),
                result.durationSeconds(),
                result.ascentMeters());
    }

    private ComplexityParams mapComplexity(String complexity) {
        return switch (complexity) {
            case "simple" -> new ComplexityParams(2, 11);
            case "technical" -> new ComplexityParams(5, 37);
            case "medium" -> new ComplexityParams(3, 23);
            default -> throw new RoundTripRoutingException(HttpStatus.BAD_REQUEST, "INVALID_COMPLEXITY",
                    "Unsupported complexity value");
        };
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

        if (!List.of("cycling-regular", "cycling-road", "cycling-mountain", "cycling-electric").contains(request.profile())) {
            throw new RoundTripRoutingException(HttpStatus.BAD_REQUEST, "INVALID_PROFILE", "Unsupported profile value");
        }

        if (!List.of("simple", "medium", "technical").contains(request.complexity())) {
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
    }

    private record ComplexityParams(int points, int seed) {
    }
}
