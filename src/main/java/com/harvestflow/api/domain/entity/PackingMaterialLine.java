package com.harvestflow.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "packing_material_line")
@Getter
@Setter
@NoArgsConstructor
public class PackingMaterialLine {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "record_id", nullable = false)
    private PackingMaterialRecord record;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false)
    private String unit = "pcs";
}
