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

    public MasterItem(MasterCategory category, String name) {
        this.category = category;
        this.name = name;
    }
}
