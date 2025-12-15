package com.paygoon.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tracks", indexes = {
        @Index(name = "idx_tracks_route", columnList = "route_id"),
        @Index(name = "idx_tracks_route_modality", columnList = "route_id, modality_id"),
        @Index(name = "idx_tracks_created_by", columnList = "created_by"),
        @Index(name = "idx_tracks_uploaded_at", columnList = "uploaded_at")
})
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "route_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Route route;

    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "route_id", referencedColumnName = "route_id", insertable = false, updatable = false, columnDefinition = "BIGINT UNSIGNED"),
            @JoinColumn(name = "modality_id", referencedColumnName = "id", columnDefinition = "BIGINT UNSIGNED")
    })
    private RouteModality modality;

    @Column(length = 120, nullable = false)
    private String nickname;

    @Column(length = 60)
    private String category;

    @Column(name = "bike_type", length = 30)
    private String bikeType;

    @Column(name = "time_seconds")
    private Integer timeSeconds;

    @Column(name = "distance_km", precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "ascent_m")
    private Integer ascentM;

    @Column(name = "gpx_asset_url", length = 512)
    private String gpxAssetUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private AppUser createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
