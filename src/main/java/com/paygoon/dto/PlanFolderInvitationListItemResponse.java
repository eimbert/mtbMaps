package com.paygoon.dto;

import com.paygoon.model.PlanFolderMember;

public record PlanFolderInvitationListItemResponse(
        Long id,
        Long folderId,
        Long userId,
        String name,
        String email,
        String nickname,
        PlanFolderMember.Role role,
        PlanFolderMember.Status status
) {
}
