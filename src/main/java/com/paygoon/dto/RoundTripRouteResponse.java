package com.paygoon.dto;

import java.util.List;
import java.util.Map;

public record RoundTripRouteResponse(
        Geometry geometry,
        Double distanceMeters,
        Double durationSeconds,
        Double ascentMeters,
        Map<String, Object> appliedOptions,
        Integer fallbackLevel,
        List<String> warnings) {

    public record Geometry(List<List<Double>> coordinates) {
    }
}
