package com.paygoon.dto;

public record PlanTrackVoteSummaryResponse(
        Long trackId,
        long totalVotes,
        boolean votedByUser
) {
}
