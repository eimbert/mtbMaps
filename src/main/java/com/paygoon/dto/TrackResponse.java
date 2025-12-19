package com.paygoon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record TrackResponse(
        Long id,
        Long routeId,
        String nickname,
        String category,
        String bikeType,
        Integer timeSeconds,
        Integer tiempoReal,
        LocalTime duracionRecorrido,
        BigDecimal distanceKm,
        String fileName,
        LocalDateTime uploadedAt,
        Long createdBy
) {}
