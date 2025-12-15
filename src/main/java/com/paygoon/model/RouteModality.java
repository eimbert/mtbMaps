package com.paygoon.model;

import java.math.BigDecimal;

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
@Table(name = "route_modalities", indexes = {
        @Index(name = "idx_modalities_route", columnList = "route_id")
})
public class RouteModality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "route_id", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Route route;

    @Column(length = 120, nullable = false)
    private String name;

    @Column(name = "distance_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal distanceKm;
}
