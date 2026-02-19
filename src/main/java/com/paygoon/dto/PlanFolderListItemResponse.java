package com.paygoon.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.paygoon.model.PlanFolderMember;

public record PlanFolderListItemResponse(
        Long id,
        Long ownerId,
        String name,
        LocalDate plannedDate,
        String observations,
        String eventUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        PlanFolderMember.Role role
) {
}
