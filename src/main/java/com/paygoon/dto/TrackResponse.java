package com.paygoon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Column;

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
        BigDecimal difficultyScore,
        Short difficultyLevel,
        String fileName,
        Integer year,
        String autonomousCommunity,
        String province,
        String comarca,
        String population,
        String startLocationUrl,
        LocalDateTime uploadedAt,
        Long createdBy,
        String title,
        Boolean shared,
        BigDecimal startLat,
        BigDecimal startLon
) {}
