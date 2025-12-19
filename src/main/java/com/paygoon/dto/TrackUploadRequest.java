package com.paygoon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TrackUploadRequest(
        @NotNull Long routeId,
        @Size(max = 60) String category,
        @Size(max = 30) String bikeType,
        @PositiveOrZero Integer timeSeconds,
        @PositiveOrZero Integer tiempoReal,
        LocalTime duracionRecorrido,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal distanceKm,
        String routeXml,
        @Size(max = 255) String fileName,
        LocalDateTime uploadedAt
) {}
