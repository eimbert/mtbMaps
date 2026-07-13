package com.paygoon.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
@Table(name = "route_analyses", indexes = {
        @Index(name = "idx_route_analyses_user", columnList = "user_id"),
        @Index(name = "idx_route_analyses_source_track", columnList = "source, source_track_id"),
        @Index(name = "idx_route_analyses_gpx_hash", columnList = "gpx_hash")
})
public class RouteAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long userId;

    @Column(length = 30, nullable = false)
    private String source;

    @Column(name = "source_track_id")
    private Long sourceTrackId;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "gpx_hash", length = 64, nullable = false)
    private String gpxHash;

    @Column(name = "analysis_version", length = 30, nullable = false)
    private String analysisVersion;

    @Column(name = "user_instructions", length = 1200)
    private String userInstructions;

    @Column(name = "instructions_hash", length = 64)
    private String instructionsHash;

    @Column(length = 80)
    private String model;

    @Column(name = "fallback_reason", length = 600)
    private String fallbackReason;

    @Column(name = "elevation_source", length = 80)
    private String elevationSource;

    @Column(name = "route_xml", columnDefinition = "LONGTEXT", nullable = false)
    private String routeXml;

    @Column(name = "report_json", columnDefinition = "LONGTEXT", nullable = false)
    private String reportJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}


