package com.paygoon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

public record PlanTrackVoteDeleteRequest(
        @JsonProperty("idFolder") @NotNull Long idFolder,
        @JsonProperty("idTrack") @NotNull Long idTrack
) {}
