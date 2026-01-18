package com.paygoon.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "plan_folder_members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_plan_folder_members", columnNames = {"folder_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_plan_folder_members_user", columnList = "user_id"),
                @Index(name = "fk_plan_folder_members_invited_by", columnList = "invited_by")
        })
public class PlanFolderMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "folder_id", nullable = false, columnDefinition = "BIGINT")
    private PlanFolder folder;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "BIGINT")
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role = Role.viewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.pending;

    @Column(name = "nickname", length = 120)
    private String nickname;

    @Column(name = "invited_email", length = 320)
    private String invitedEmail;

    @ManyToOne
    @JoinColumn(name = "invited_by", columnDefinition = "BIGINT")
    private AppUser invitedBy;

    @Column(name = "can_vote", nullable = false)
    private Boolean canVote = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    public enum Role {
        owner,
        editor,
        viewer
    }

    public enum Status {
        pending,
        sending,
        accepted,
        rejected,
        revoked
    }
}
