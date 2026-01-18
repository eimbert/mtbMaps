package com.paygoon.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlanTrackImportRequest(
        @JsonProperty("created_by_user_id") Long createdByUserId,
        @JsonProperty("desnivel") Long desnivel,
        @JsonProperty("distance_km") BigDecimal distanceKm,
        @JsonProperty("folder_id") @NotNull Long folderId,
        @JsonProperty("moving_time_sec") Integer movingTimeSec,
        @Size(max = 160) String name,
        @JsonProperty("route_xml") String routeXml,
        @JsonProperty("start_lat") @NotNull BigDecimal startLat,
        @JsonProperty("start_lon") @NotNull BigDecimal startLon,
        @JsonProperty("start_population") @Size(max = 140) String startPopulation,
        @JsonProperty("total_time_sec") Integer totalTimeSec
) {}
