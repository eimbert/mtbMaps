package com.paygoon.dto;

public record TrackGpxResponse(
        Long id,
        String fileName,
        String routeXml
) {}
