package com.paygoon.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlanTrackUpdateRequest(
        @JsonProperty("id") @NotNull Long id,
        @JsonProperty("route_xml") @NotBlank String routeXml
) {}
