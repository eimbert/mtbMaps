package com.paygoon.dto;

import jakarta.validation.constraints.NotNull;

public record MensajeEstadoUpdateRequest(
        @NotNull Integer estado
) {}
