package com.paygoon.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RouteAnalysisResponse(
        Long id,
        Long trackId,
        Long userId,
        String source,
        String fileName,
        String gpxHash,
        String analysisVersion,
        String model,
        String fallbackReason,
        String userInstructions,
        String elevationSource,
        RouteAnalysisStats routeStats,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        RouteAnalysisReport report,
        boolean usageCharged,
        boolean reusedExisting
) {
    public record RouteAnalysisStats(
            double distanceKm,
            double elevationGainM,
            double elevationLossM,
            double minEleM,
            double maxEleM,
            List<RouteAnalysisElevationPoint> elevationProfile
    ) {}

    public record RouteAnalysisElevationPoint(double distanceKm, double elevationM) {}

    public record RouteAnalysisReport(
            String summary,
            String routeType,
            String difficultyExplanation,
            List<RouteAnalysisHighlight> highlights,
            List<String> warnings,
            List<String> recommendations,
            List<RouteAnalysisSector> sectors,
            RouteAnalysisApproaches approaches
    ) {}

    public record RouteAnalysisHighlight(
            String title,
            String description,
            Double kmStart,
            Double kmEnd,
            Double lat,
            Double lon,
            String type,
            String severity
    ) {}

    public record RouteAnalysisSector(
            String name,
            Double kmStart,
            Double kmEnd,
            String focus,
            String effort
    ) {}

    public record RouteAnalysisApproaches(
            List<String> recreational,
            List<String> training,
            List<String> competition
    ) {}
}


