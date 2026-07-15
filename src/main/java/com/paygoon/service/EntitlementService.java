package com.paygoon.service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.paygoon.dto.EntitlementsResponse;
import com.paygoon.model.AiUsage;
import com.paygoon.model.AppUser;
import com.paygoon.model.AppUser.AccountPlan;
import com.paygoon.repository.AiUsageRepository;
import com.paygoon.repository.TrackRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EntitlementService {
    public static final int UNLIMITED = -1;
    private static final String ROUTE_ANALYSIS = "ROUTE_ANALYSIS";
    private static final String THIRD_PARTY_TRACK = "THIRD_PARTY_TRACK";

    private final AiUsageRepository aiUsageRepository;
    private final TrackRepository trackRepository;

    public AccountPlan effectivePlan(AppUser user) {
        if (user.getRol() != null && user.getRol().toUpperCase(Locale.ROOT).contains("ADMIN")) return AccountPlan.ADMIN;
        if (user.getAccountPlan() == AccountPlan.ADMIN) return AccountPlan.ADMIN;
        if (user.isLifetimePremium()) return AccountPlan.PREMIUM;
        if (user.getPremiumUntil() != null && user.getPremiumUntil().isAfter(LocalDateTime.now())) return AccountPlan.PREMIUM;
        if (user.getAccountPlan() == AccountPlan.PREMIUM || user.isPremium()) return AccountPlan.PREMIUM;
        return AccountPlan.FREE;
    }

    public boolean isPremium(AppUser user) {
        return effectivePlan(user) != AccountPlan.FREE;
    }

    public void assertCanUploadTrack(AppUser user) {
        int max = maxOwnTracks(user);
        if (max != UNLIMITED && trackRepository.countByCreatedById(user.getId()) >= max) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Has alcanzado el limite de " + max + " tracks de la cuenta gratuita. Hazte Premium para subir mas.");
        }
    }

    public void assertCanUseRouteAnalysis(AppUser user) {
        AccountPlan plan = effectivePlan(user);
        if (plan == AccountPlan.ADMIN) return;

        LocalDateTime now = LocalDateTime.now();
        int sixHourLimit = plan == AccountPlan.PREMIUM ? 20 : 2;
        int monthlyLimit = plan == AccountPlan.PREMIUM ? 100 : 8;
        long recent = aiUsageRepository.countByUserIdAndOperationAndUsedAtAfter(user.getId(), ROUTE_ANALYSIS, now.minusHours(6));
        long monthly = aiUsageRepository.countByUserIdAndOperationAndUsedAtAfter(user.getId(), ROUTE_ANALYSIS,
                YearMonth.from(now).atDay(1).atStartOfDay());
        if (recent >= sixHourLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Has alcanzado el limite de " + sixHourLimit + " analisis IA cada 6 horas de tu plan.");
        }
        if (monthly >= monthlyLimit) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Has alcanzado el limite mensual de " + monthlyLimit + " analisis IA de tu plan.");
        }
    }

    public void recordRouteAnalysis(AppUser user) {
        AiUsage usage = new AiUsage();
        usage.setUserId(user.getId());
        usage.setOperation(ROUTE_ANALYSIS);
        aiUsageRepository.save(usage);
    }

    public int maxOwnTracks(AppUser user) {
        return effectivePlan(user) == AccountPlan.FREE ? 25 : UNLIMITED;
    }

    public int maxThirdPartyTracks(AppUser user) {
        return effectivePlan(user) == AccountPlan.FREE ? 25 : UNLIMITED;
    }

    public void registerThirdPartyTrackAccess(AppUser user, Long trackId) {
        if (effectivePlan(user) != AccountPlan.FREE) return;
        LocalDateTime threshold = LocalDateTime.now().minusHours(72);
        if (aiUsageRepository.existsByUserIdAndOperationAndResourceIdAndUsedAtAfter(
                user.getId(), THIRD_PARTY_TRACK, trackId, threshold)) return;
        long used = aiUsageRepository.countByUserIdAndOperationAndUsedAtAfter(user.getId(), THIRD_PARTY_TRACK, threshold);
        if (used >= maxThirdPartyTracks(user)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Has alcanzado el limite de 25 rutas distintas de terceros cada 72 horas.");
        }
        AiUsage usage = new AiUsage();
        usage.setUserId(user.getId());
        usage.setOperation(THIRD_PARTY_TRACK);
        usage.setResourceId(trackId);
        aiUsageRepository.save(usage);
    }

    public EntitlementsResponse describe(AppUser user) {
        LocalDateTime now = LocalDateTime.now();
        AccountPlan plan = effectivePlan(user);
        long sixHourUsed = aiUsageRepository.countByUserIdAndOperationAndUsedAtAfter(
                user.getId(), ROUTE_ANALYSIS, now.minusHours(6));
        long monthlyUsed = aiUsageRepository.countByUserIdAndOperationAndUsedAtAfter(user.getId(), ROUTE_ANALYSIS,
                YearMonth.from(now).atDay(1).atStartOfDay());
        int sixHourLimit = plan == AccountPlan.ADMIN ? UNLIMITED : plan == AccountPlan.PREMIUM ? 20 : 2;
        int monthlyLimit = plan == AccountPlan.ADMIN ? UNLIMITED : plan == AccountPlan.PREMIUM ? 100 : 8;
        LocalDateTime nextAi = null;
        if (monthlyLimit != UNLIMITED && monthlyUsed >= monthlyLimit) {
            nextAi = YearMonth.from(now).plusMonths(1).atDay(1).atStartOfDay();
        } else if (sixHourLimit != UNLIMITED && sixHourUsed > 0) {
            nextAi = aiUsageRepository.findFirstByUserIdAndOperationAndUsedAtAfterOrderByUsedAtAsc(
                    user.getId(), ROUTE_ANALYSIS, now.minusHours(6)).map(value -> value.getUsedAt().plusHours(6)).orElse(null);
        }
        int thirdPartyLimit = maxThirdPartyTracks(user);
        long thirdPartyUsed = thirdPartyLimit == UNLIMITED ? 0
                : aiUsageRepository.countByUserIdAndOperationAndUsedAtAfter(user.getId(), THIRD_PARTY_TRACK, now.minusHours(72));
        LocalDateTime nextThirdParty = thirdPartyLimit != UNLIMITED && thirdPartyUsed > 0
                ? aiUsageRepository.findFirstByUserIdAndOperationAndUsedAtAfterOrderByUsedAtAsc(
                        user.getId(), THIRD_PARTY_TRACK, now.minusHours(72))
                        .map(value -> value.getUsedAt().plusHours(72)).orElse(null)
                : null;
        return new EntitlementsResponse(plan.name(), plan != AccountPlan.FREE, plan == AccountPlan.ADMIN,
                user.isLifetimePremium(), user.getPremiumUntil(), maxOwnTracks(user), maxThirdPartyTracks(user),
                sixHourLimit, monthlyLimit, trackRepository.countByCreatedById(user.getId()),
                sixHourUsed, monthlyUsed, nextAi, thirdPartyLimit, thirdPartyUsed, nextThirdParty);
    }
}
