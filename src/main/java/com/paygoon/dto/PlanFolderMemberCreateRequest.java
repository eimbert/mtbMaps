package com.paygoon.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlanFolderMemberCreateRequest(
        @NotNull Long folderId,
        Long userId,
        @Email @Size(max = 320) String email,
        @Size(max = 120) String nickname
) {}
