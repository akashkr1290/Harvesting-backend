package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.PackingStage;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "packing_material_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"case_id", "stage"})
)
@Getter
@Setter
@NoArgsConstructor
public class PackingMaterialRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "case_id", nullable = false)
    private HarvestCase harvestCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackingStage stage;

    @Column(nullable = false)
    private Instant recordedAt = Instant.now();

    /** Receiver name (issue) or reconciled-by name (return). */
    @Column(nullable = false)
    private String handledBy;

    @Column(length = 1000)
    private String remarks;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PackingMaterialLine> lines = new ArrayList<>();

    public void addLine(String itemName, double quantity, String unit) {
        PackingMaterialLine line = new PackingMaterialLine();
        line.setRecord(this);
        line.setItemName(itemName);
        line.setQuantity(quantity);
        line.setUnit(unit == null || unit.isBlank() ? "pcs" : unit);
        this.lines.add(line);
    }
}
