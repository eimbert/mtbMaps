package com.paygoon.dto;

public record LoginResponse(
    String token,
    Long id,
    String name,
    String email,
    String nickname,
    String rol,
    boolean premium,
    boolean verified
) {}
