package com.paygoon.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record ProximityMatrixRequest(
        @NotBlank String profile,
        @NotNull @Valid Origin origin,
        @NotEmpty List<@Valid Candidate> candidates,
        @NotNull @Valid Options options
) {

    public record Origin(
            @NotNull Double lat,
            @NotNull Double lon
    ) {}

    public record Candidate(
            @NotNull Long trackId,
            @NotNull Double startLat,
            @NotNull Double startLon,
            @NotNull Double haversineKm
    ) {}

    public record Options(
            @NotEmpty List<String> metrics,
            @NotBlank String units,
            @NotBlank String resolveOrderBy,
            @NotBlank String fallback
    ) {}
}
