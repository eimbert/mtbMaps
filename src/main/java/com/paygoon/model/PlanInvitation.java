package com.paygoon.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plan_invitations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_plan_invitations_token", columnNames = "token")
        },
        indexes = {
                @Index(name = "idx_plan_invitations_folder", columnList = "folder_id"),
                @Index(name = "idx_plan_invitations_invited_user", columnList = "invited_user_id"),
                @Index(name = "idx_plan_invitations_invited_email", columnList = "invited_email"),
                @Index(name = "idx_plan_invitations_status", columnList = "status")
        })
public class PlanInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "folder_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private PlanFolder folder;

    @ManyToOne
    @JoinColumn(name = "invited_user_id", columnDefinition = "BIGINT UNSIGNED")
    private AppUser invitedUser;

    @Column(name = "invited_email", length = 254)
    private String invitedEmail;

    @ManyToOne(optional = false)
    @JoinColumn(name = "invited_by_user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private AppUser invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.viewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.pending;

    @Column(length = 36, nullable = false)
    private String token;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public enum Role {
        editor,
        viewer
    }

    public enum Status {
        pending,
        accepted,
        declined,
        revoked,
        expired
    }
}
