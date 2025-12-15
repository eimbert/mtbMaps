package com.paygoon.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "verification_tokens")
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String token;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", columnDefinition = "BIGINT UNSIGNED")
    private AppUser user;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    private LocalDateTime revokedAt;

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActive() {
        return !isExpired() && !isUsed() && !isRevoked();
    }
}
