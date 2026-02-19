package com.paygoon.model;

import java.time.LocalDate;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plan_folders", indexes = {
        @Index(name = "idx_plan_folders_owner", columnList = "owner_user_id"),
        @Index(name = "idx_plan_folders_planned_date", columnList = "planned_date")
})
public class PlanFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false, columnDefinition = "BIGINT")
    private AppUser owner;

    @Column(length = 120, nullable = false)
    private String name;

    @Column(name = "planned_date")
    private LocalDate plannedDate;

    @Column(columnDefinition = "TEXT")
    private String observations;

    @Column(name = "event_url", length = 500)
    private String eventUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
