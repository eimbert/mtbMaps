package com.paygoon.model;

import java.math.BigDecimal;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "plan_tracks", indexes = {
        @Index(name = "idx_plan_tracks_folder", columnList = "folder_id"),
        @Index(name = "idx_plan_tracks_created_by", columnList = "created_by_user_id"),
        @Index(name = "idx_plan_tracks_folder_sort", columnList = "folder_id, sort_order")
})
public class PlanTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "folder_id", nullable = false, columnDefinition = "BIGINT")
    private PlanFolder folder;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, columnDefinition = "BIGINT")
    private AppUser createdBy;

    @Column(length = 160)
    private String name;

    @Column(name = "start_lat", precision = 15, scale = 12, nullable = false)
    private BigDecimal startLat;

    @Column(name = "start_lon", precision = 15, scale = 12, nullable = false)
    private BigDecimal startLon;

    @Column(name = "start_population", length = 140)
    private String startPopulation;

    @Column(name = "distance_km", precision = 8, scale = 3)
    private BigDecimal distanceKm;

    @Column(name = "moving_time_sec", columnDefinition = "INT UNSIGNED")
    private Integer movingTimeSec;

    @Column(name = "total_time_sec", columnDefinition = "INT UNSIGNED")
    private Integer totalTimeSec;

    @Column(name = "how_to_get_url", length = 600)
    private String howToGetUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 30)
    private SourceType sourceType;

    @Column(name = "gpx_storage_path", length = 600)
    private String gpxStoragePath;

    @Column(name = "sort_order", columnDefinition = "INT UNSIGNED", nullable = false)
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum SourceType {
        import_gpx,
        copy_from_library,
        external_link,
        drawn
    }
}
