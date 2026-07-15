package com.paygoon.dto;

import java.time.LocalDateTime;

public record PublicRouteAnalysisResponse(
        Long id,
        String title,
        String fileName,
        String source,
        RouteAnalysisResponse.RouteAnalysisStats routeStats,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        RouteAnalysisResponse.RouteAnalysisReport report
) {}
