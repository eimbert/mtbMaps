package com.paygoon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record PlanTrackVoteCreateRequest(
        @JsonProperty("idFolder") @NotNull Long idFolder,
        @JsonProperty("idTrack") @NotNull Long idTrack
) {}
