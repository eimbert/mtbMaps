package com.paygoon.service;

import com.paygoon.model.AppUser;
import com.paygoon.model.VerificationToken;
import com.paygoon.repository.UserRepository;
import com.paygoon.repository.VerificationTokenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private static final Duration TOKEN_EXPIRATION = Duration.ofHours(2);
    private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public EmailVerificationService(VerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    NotificationService notificationService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public VerificationToken createAndSendToken(AppUser user) {
        enforceRateLimit(user);
        revokePendingTokens(user);

        VerificationToken token = new VerificationToken();
        token.setToken(UUID.randomUUID().toString());
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plus(TOKEN_EXPIRATION));

        tokenRepository.save(token);

        LocalDateTime now = LocalDateTime.now();
        if (user.getVerificationRequestedAt() == null) {
            user.setVerificationRequestedAt(now);
        }
        user.setLastVerificationEmailSentAt(now);
        userRepository.save(user);

        notificationService.sendVerificationEmail(user, token.getToken(), TOKEN_EXPIRATION);

        return token;
    }

    private void enforceRateLimit(AppUser user) {
        if (user.getLastVerificationEmailSentAt() != null) {
            Duration elapsed = Duration.between(user.getLastVerificationEmailSentAt(), LocalDateTime.now());
            if (elapsed.compareTo(RESEND_COOLDOWN) < 0) {
                long secondsLeft = RESEND_COOLDOWN.minus(elapsed).toSeconds();
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Por favor espera " + secondsLeft + " segundos para reenviar el correo");
            }
        }
    }

    private void revokePendingTokens(AppUser user) {
        tokenRepository.findAllByUserAndUsedAtIsNullAndRevokedAtIsNull(user)
                .forEach(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    tokenRepository.save(token);
                });
    }

    public AppUser verifyToken(String rawToken) {
        VerificationToken token = tokenRepository.findByToken(rawToken)
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
        user.setVerified(true);
        user.setVerificationCompletedAt(LocalDateTime.now());
        userRepository.save(user);

        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        return user;
    }
}
