package com.paygoon.dto;

import java.time.LocalDateTime;

public record EntitlementsResponse(
        String plan,
        boolean premium,
        boolean administrator,
        boolean lifetimePremium,
        LocalDateTime premiumUntil,
        int maxOwnTracks,
        int maxThirdPartyTracks,
        int aiAnalysesPerSixHours,
        int aiAnalysesPerMonth,
        long ownTracksUsed,
        long aiAnalysesUsedLastSixHours,
        long aiAnalysesUsedThisMonth,
        LocalDateTime nextAiAvailability,
        int thirdPartyAccessesPer72Hours,
        long thirdPartyAccessesUsed,
        LocalDateTime nextThirdPartyAccessAvailability) {}
