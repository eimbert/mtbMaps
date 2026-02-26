package com.paygoon.service;

import com.paygoon.model.AppUser;
import com.paygoon.model.PasswordResetToken;
import com.paygoon.repository.PasswordResetTokenRepository;
import com.paygoon.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_EXPIRATION = Duration.ofMinutes(30);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                NotificationService notificationService,
                                PasswordEncoder passwordEncoder) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.passwordEncoder = passwordEncoder;
    }

    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            try {
                enforceRateLimit(user);
                revokePendingTokens(user);

                String rawToken = generateRawToken();
                PasswordResetToken token = new PasswordResetToken();
                token.setTokenHash(hashToken(rawToken));
                token.setUser(user);
                token.setExpiresAt(LocalDateTime.now().plus(TOKEN_EXPIRATION));
                tokenRepository.save(token);

                user.setLastPasswordResetEmailSentAt(LocalDateTime.now());
                userRepository.save(user);

                notificationService.sendPasswordResetEmail(user, rawToken, TOKEN_EXPIRATION);
            } catch (ResponseStatusException ex) {
                log.info("Saltando envío de reset por rate limit para {}", user.getEmail());
            }
        });
    }

    public void resetPassword(String rawToken, String newPassword) {
        String tokenHash = hashToken(rawToken);
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token inválido"));

        if (token.isUsed() || token.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El token ya fue utilizado o revocado");
        }

        if (token.isExpired()) {
            token.setRevokedAt(LocalDateTime.now());
            tokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.GONE, "El token ha expirado");
        }

        AppUser user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        revokePendingTokens(user);
    }

    public void changePassword(AppUser user, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La contraseña actual no es correcta");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        revokePendingTokens(user);
    }

    private void enforceRateLimit(AppUser user) {
        if (user.getLastPasswordResetEmailSentAt() == null) {
            return;
        }

        Duration elapsed = Duration.between(user.getLastPasswordResetEmailSentAt(), LocalDateTime.now());
        if (elapsed.compareTo(RESEND_COOLDOWN) < 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Espera antes de volver a intentarlo");
        }
    }

    private void revokePendingTokens(AppUser user) {
        tokenRepository.findAllByUserAndUsedAtIsNullAndRevokedAtIsNull(user)
                .forEach(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    tokenRepository.save(token);
                });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
