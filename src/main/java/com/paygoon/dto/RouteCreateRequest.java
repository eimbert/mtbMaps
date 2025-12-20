package com.paygoon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RouteCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 120) String population,
        @Size(max = 120) String autonomousCommunity,
        @NotNull @Positive Integer year,
        String logoBlob,
        String gpxMaster,
        @Size(max = 64) String logoMime
) {}
