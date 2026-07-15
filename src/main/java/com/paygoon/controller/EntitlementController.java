package com.paygoon.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paygoon.dto.EntitlementsResponse;
import com.paygoon.model.AppUser;
import com.paygoon.repository.UserRepository;
import com.paygoon.service.EntitlementService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class EntitlementController {
    private final UserRepository userRepository;
    private final EntitlementService entitlementService;

    @GetMapping("/entitlements")
    public ResponseEntity<EntitlementsResponse> entitlements(Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return ResponseEntity.ok(entitlementService.describe(user));
    }
}
