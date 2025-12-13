package com.paygoon.dto;

public record LoginResponse(
    String token,
    Long id,
    String name,
    String email,
    boolean premium,
    boolean verified
) {}
