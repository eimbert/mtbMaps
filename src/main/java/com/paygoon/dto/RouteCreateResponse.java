package com.paygoon.dto;

public record RouteCreateResponse(
        Long id,
        String message,
        int exitCode
) {}
