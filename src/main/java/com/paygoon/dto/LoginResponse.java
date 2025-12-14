package com.paygoon.dto;

public record LoginResponse(
    int exitCode,
    String token,
    Long id,
    String name,
    String email,
    String nickname,
    String rol,
    boolean premium,
    boolean verified
) {}
