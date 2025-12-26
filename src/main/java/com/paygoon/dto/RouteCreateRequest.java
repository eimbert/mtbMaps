package com.paygoon.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
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
        String province,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal distanceKm,
        @Size(max = 64) String logoMime
) {}
