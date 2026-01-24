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
@Table(name = "plan_tracks_votes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_plan_tracks_votes_one_per_user", columnNames = {"idFolder", "idUsu"})
        },
        indexes = {
                @Index(name = "idx_plan_tracks_votes_folder_track", columnList = "idFolder, idTrack"),
                @Index(name = "idx_plan_tracks_votes_user", columnList = "idUsu")
        })
public class PlanTrackVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "idFolder", nullable = false, columnDefinition = "BIGINT")
    private PlanFolder folder;

    @ManyToOne(optional = false)
    @JoinColumn(name = "idUsu", nullable = false, columnDefinition = "BIGINT")
    private AppUser user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "idTrack", nullable = false, columnDefinition = "BIGINT")
    private PlanTrack track;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
