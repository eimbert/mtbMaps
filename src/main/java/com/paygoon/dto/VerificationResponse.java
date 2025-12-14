package com.paygoon.dto;

import java.time.LocalDateTime;

public record VerificationResponse(String message, LocalDateTime expiresAt) {}
