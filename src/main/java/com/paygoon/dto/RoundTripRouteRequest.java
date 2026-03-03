package com.paygoon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RoundTripRouteRequest(
        @NotNull
        @Pattern(regexp = "^(cycling-regular|cycling-road|cycling-mountain|cycling-electric)$")
        String profile,
        @NotNull
        @Pattern(regexp = "^(simple|medium|technical)$")
        String complexity,
        @NotNull
        @DecimalMin("5.0")
        @DecimalMax("200.0")
        Double lengthKm,
        @NotNull @Valid Start start) {

    public record Start(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon) {
    }
}
