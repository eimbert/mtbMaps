package com.paygoon.dto;

import java.time.LocalDateTime;

public record PlanTrackVoteListItemResponse(
        Long id,
        Long folderId,
        Long userId,
        Long trackId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
