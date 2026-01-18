package com.paygoon.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.paygoon.model.PlanTrack;

public record PlanTrackListItemResponse(
        Long id,
        Long folderId,
        Long createdByUserId,
        String name,
        BigDecimal startLat,
        BigDecimal startLon,
        String startPopulation,
        BigDecimal distanceKm,
        Integer movingTimeSec,
        Integer totalTimeSec,
        Long desnivel,
        String howToGetUrl,
        PlanTrack.SourceType sourceType,
        String gpxStoragePath,
        String routeXml,
        Integer sortOrder,
        LocalDateTime createdAt
) {
}
