package com.paygoon.dto;

import jakarta.validation.constraints.NotBlank;

public record RouteAnalysisRequest(
        Long trackId,
        String source,
        String fileName,
        @NotBlank String routeXml,
        String userInstructions,
        Boolean forceRefresh
) {}
