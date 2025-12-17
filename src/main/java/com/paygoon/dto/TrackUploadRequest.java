package com.paygoon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TrackUploadRequest(
        @NotNull Long routeId,
        @NotBlank @Size(max = 120) String nickname,
        @Size(max = 60) String category,
        @Size(max = 30) String bikeType,
        @PositiveOrZero Integer timeSeconds,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal distanceKm,
        @PositiveOrZero Integer ascentM,
        @Size(max = 512) String gpxAssetUrl,
        String routeXml,
        @Size(max = 255) String fileName,
        LocalDateTime uploadedAt
) {}
