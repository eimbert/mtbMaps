package com.paygoon.dto;

import jakarta.validation.constraints.NotBlank;

public record VerificationRequest(@NotBlank String token) {}
