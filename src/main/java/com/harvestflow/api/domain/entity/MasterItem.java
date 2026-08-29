package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.MasterCategory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "master_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"category", "name"})
)
@Getter
@Setter
@NoArgsConstructor
public class MasterItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MasterCategory category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /**
     * Optional coordinates (Phase 3). Populated for LOCATIONS/COMPANIES
     * entries so route optimization can resolve a stop from a name alone
     * instead of requiring latitude/longitude on every request. Unused
     * (and harmless if null) for other categories.
     */
    private Double latitude;
    private Double longitude;

    public MasterItem(MasterCategory category, String name) {
        this.category = category;
        this.name = name;
    }
}
