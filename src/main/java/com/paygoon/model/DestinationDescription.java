package com.paygoon.model;

import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "destination_descriptions", uniqueConstraints =
        @UniqueConstraint(name = "uk_destination_description_key", columnNames = "place_key"))
public class DestinationDescription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "place_key", nullable = false, length = 320)
    private String placeKey;
    @Column(name = "place_type", nullable = false, length = 40)
    private String placeType;
    @Column(name = "place_name", nullable = false, length = 180)
    private String placeName;
    @Column(name = "context_name", length = 180)
    private String contextName;
    @Column(nullable = false, length = 600)
    private String description;
    @Column(length = 80)
    private String model;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
