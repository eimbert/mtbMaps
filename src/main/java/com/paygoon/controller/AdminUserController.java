package com.paygoon.controller;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.paygoon.dto.AdminPlanRequest;
import com.paygoon.dto.EntitlementsResponse;
import com.paygoon.model.AppUser;
import com.paygoon.model.AppUser.AccountPlan;
import com.paygoon.repository.UserRepository;
import com.paygoon.service.EntitlementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserRepository userRepository;
    private final EntitlementService entitlementService;

    @PutMapping("/plan")
    public ResponseEntity<EntitlementsResponse> changePlan(Authentication authentication,
            @RequestParam String email, @RequestBody AdminPlanRequest request) {
        AppUser actor = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (entitlementService.effectivePlan(actor) != AccountPlan.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un administrador puede cambiar planes");
        }
        AppUser target = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        AccountPlan plan;
        try {
            plan = AccountPlan.valueOf(request.plan().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan no valido");
        }
        target.setAccountPlan(plan);
        target.setLifetimePremium(plan == AccountPlan.PREMIUM && request.lifetimePremium());
        target.setPremium(plan == AccountPlan.PREMIUM);
        if (plan != AccountPlan.PREMIUM) target.setPremiumUntil(null);
        userRepository.save(target);
        return ResponseEntity.ok(entitlementService.describe(target));
    }
}
