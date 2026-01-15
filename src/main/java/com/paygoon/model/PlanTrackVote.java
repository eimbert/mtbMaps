package com.paygoon.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "plan_track_votes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_plan_track_votes_one_per_user", columnNames = {"folder_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_plan_track_votes_folder_track", columnList = "folder_id, track_id"),
                @Index(name = "idx_plan_track_votes_user", columnList = "user_id")
        })
public class PlanTrackVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "folder_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private PlanFolder folder;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private AppUser user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "track_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private PlanTrack track;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
