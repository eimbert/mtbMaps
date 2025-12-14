package com.paygoon.dto;

public record UserProfileResponse(
    Long userId,
    String nom,
    String nickname,
    String rol
) {}
