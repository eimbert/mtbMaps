package com.paygoon.dto;

import jakarta.validation.constraints.NotNull;

public record PlanFolderMemberDeleteRequest(
        @NotNull Long folderId,
        @NotNull Long userId
) {}
