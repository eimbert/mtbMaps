package com.paygoon.dto;

import java.math.BigDecimal;

public record TrackRouteSummaryResponse(
        Long id,
        String nickname,
        String category,
        String bikeType,
        BigDecimal distanceKm,
        Integer tiempoReal
) {}
