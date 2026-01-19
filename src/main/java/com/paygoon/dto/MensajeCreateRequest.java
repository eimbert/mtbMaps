package com.paygoon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MensajeCreateRequest(
        @NotNull Long userId,
        @NotBlank String mensaje,
        @NotNull Integer tipoMsg,
        Integer estado
) {}
