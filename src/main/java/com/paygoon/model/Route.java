package com.paygoon.model;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    private String name;

    private String population;

    @Column(name = "autonomous_community")
    private String autonomousCommunity;

    private Integer year;
    
    @Column(name = "master_xml", columnDefinition = "LONGTEXT")
    private String gpxMaster;

    @Lob
    @Column(name = "logo_blob", columnDefinition = "LONGBLOB")
    private byte[] logoBlob;

    @Column(name = "logo_mime")
    private String logoMime;
    
    private String province;

    @ManyToOne
    @JoinColumn(name = "created_by", nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private AppUser createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
