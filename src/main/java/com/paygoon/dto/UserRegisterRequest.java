package com.paygoon.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    String password,
    @NotBlank String name,
    String nickname,
    String rol
) {}

