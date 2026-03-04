package com.paygoon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;

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
        @NotNull @Valid Start start,
        @Valid Preferences preferences) {

    public record Start(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon) {
    }

    public record Preferences(
            @Size(max = 8) List<@Pattern(regexp = "^(ferries|steps|tollways)$") String> avoidFeatures,
            @Size(max = 8) List<@Pattern(regexp = "^(surface|waytype)$") String> extraInfo,
            @Valid Weightings weightings,
            @Pattern(regexp = "^(balanced|trails|anti-asphalt|avoid-asphalt)$") String mode,
            @DecimalMin("0.0") @DecimalMax("1.0") Double strictness) {
    }

    public record Weightings(
            @DecimalMin("0.0") @DecimalMax("2.0") Double green,
            @DecimalMin("0.0") @DecimalMax("2.0") Double quiet,
            @DecimalMin("0.0") @DecimalMax("6.0") Double steepness_difficulty) {
    }
}
