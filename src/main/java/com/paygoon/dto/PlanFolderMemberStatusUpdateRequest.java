package com.paygoon.dto;

import com.paygoon.model.PlanFolderMember;
import jakarta.validation.constraints.NotNull;

public record PlanFolderMemberStatusUpdateRequest(
        @NotNull Long id,
        @NotNull PlanFolderMember.Status status
) {}
