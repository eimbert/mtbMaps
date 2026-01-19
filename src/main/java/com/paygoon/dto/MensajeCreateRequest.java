package com.paygoon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MensajeCreateRequest(
        @NotNull Long userId,
        Long userMsgId,
        @NotBlank String mensaje,
        @NotNull Integer tipoMsg,
        Integer estado
) {}
