package com.paygoon.dto;

import java.math.BigDecimal;

public record TrackRouteSummaryResponse(
        Long id,
        String nickname,
        String category,
        String bikeType,
        BigDecimal distanceKm,
        Integer tiempoReal,
        String tiempoRealFormatted
) {

    public TrackRouteSummaryResponse(
            Long id,
            String nickname,
            String category,
            String bikeType,
            BigDecimal distanceKm,
            Integer tiempoReal
    ) {
        this(id, nickname, category, bikeType, distanceKm, tiempoReal, formatSeconds(tiempoReal));
    }

    private static String formatSeconds(Integer tiempoReal) {
        if (tiempoReal == null || tiempoReal < 0) {
            return null;
        }
        int hours = tiempoReal / 3600;
        int minutes = (tiempoReal % 3600) / 60;
        int seconds = tiempoReal % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
