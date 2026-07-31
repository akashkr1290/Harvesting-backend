package com.harvestflow.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "planning_details")
@Getter
@Setter
@NoArgsConstructor
public class PlanningDetails {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private HarvestCase harvestCase;

    @Column(nullable = false)
    private LocalDate plannedDate;

    private String timeWindow;

    @Column(nullable = false)
    private String supervisorName;

    @Column(nullable = false)
    private String pickupPersonName;

    private String vehiclePlan;
    private Double expectedQuantity;

    @Column(length = 1000)
    private String notesForGodown;

    @Column(length = 1000)
    private String notesForSupervisor;
}
