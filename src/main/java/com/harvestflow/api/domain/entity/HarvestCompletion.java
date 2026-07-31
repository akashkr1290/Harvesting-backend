package com.harvestflow.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "harvest_completion")
@Getter
@Setter
@NoArgsConstructor
public class HarvestCompletion {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private HarvestCase harvestCase;

    @Column(nullable = false)
    private LocalDate actualDate;

    private Integer actualBagsOrCrates;

    @Column(nullable = false)
    private Double actualWeight;

    @Column(length = 1000)
    private String qualityNotes;

    @Column(length = 1000)
    private String wastageNotes;

    private Double finalVolume;
    private Double finalRecovery;
    private Double finalPulp;

    @Column(length = 1000)
    private String remarks;

    /** Reference to the uploaded weight slip file (stored via the file
     * service once wired up; for now just the filename/key). */
    private String weightSlipFileName;
}
