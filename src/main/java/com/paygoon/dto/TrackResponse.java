package com.paygoon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TrackResponse(
        Long id,
        Long routeId,
        String nickname,
        String category,
        String bikeType,
        Integer timeSeconds,
        BigDecimal distanceKm,
        String fileName,
        LocalDateTime uploadedAt,
        Long createdBy
) {}
