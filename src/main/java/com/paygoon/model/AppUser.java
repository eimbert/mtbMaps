package com.paygoon.model;


import jakarta.persistence.Id;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    private String name;

    @Column(unique = true)
    private String email;

    private String passwordHash;

    private String nickname;

    private String rol;

    @Enumerated(EnumType.STRING)
    private LoginType loginType = LoginType.EMAIL;

    private boolean verified = false;

    private LocalDateTime verificationRequestedAt;

    private LocalDateTime verificationCompletedAt;

    private LocalDateTime lastVerificationEmailSentAt;

    private LocalDateTime lastPasswordResetEmailSentAt;

    private LocalDateTime passwordChangedAt;

    private boolean premium = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountPlan accountPlan = AccountPlan.FREE;

    private LocalDateTime premiumUntil;

    @Column(nullable = false)
    private boolean lifetimePremium = false;

    private String documentType;

    private String documentNumber;

    public enum LoginType {
        EMAIL, GOOGLE, APPLE
    }

    public enum AccountPlan {
        FREE, PREMIUM, ADMIN
    }

    @PrePersist
    public void onCreate() {
        if (verificationRequestedAt == null) {
            verificationRequestedAt = LocalDateTime.now();
        }
        if (accountPlan == null) {
            accountPlan = premium ? AccountPlan.PREMIUM : AccountPlan.FREE;
        }
    }
}
