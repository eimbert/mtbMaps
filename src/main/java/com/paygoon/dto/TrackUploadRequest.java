package com.paygoon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TrackUploadRequest(
        Long routeId,
        @Size(max = 60) String category,
        @Size(max = 30) String bikeType,
        @PositiveOrZero Integer timeSeconds,
        @PositiveOrZero Integer tiempoReal,
        LocalTime duracionRecorrido,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal distanceKm,
        String routeXml,
        @Size(max = 255) String fileName,
        Integer year,
        @Size(max = 50) String autonomousCommunity,
        @Size(max = 50) String province,
        @Size(max = 70) String population,
        LocalDateTime uploadedAt,
        String title,
        boolean shared
) {}
