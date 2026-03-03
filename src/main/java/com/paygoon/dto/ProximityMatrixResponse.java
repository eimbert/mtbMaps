package com.paygoon.dto;

import java.util.List;

public record ProximityMatrixResponse(
        String strategyUsed,
        List<OrderedItem> ordered
) {
    public record OrderedItem(
            Long trackId,
            int rank,
            Integer roadDistanceM,
            Integer roadDurationS
    ) {}
}
