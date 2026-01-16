package com.paygoon.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlanFolderCreateRequest(
        @NotBlank @Size(max = 120) String name,
        LocalDate plannedDate,
        @Size(max = 2000) String observations
) {}
