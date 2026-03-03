package com.paygoon.dto;

import java.util.List;

public record RoundTripRouteResponse(
        Geometry geometry,
        Double distanceMeters,
        Double durationSeconds,
        Double ascentMeters) {

    public record Geometry(List<List<Double>> coordinates) {
    }
}
