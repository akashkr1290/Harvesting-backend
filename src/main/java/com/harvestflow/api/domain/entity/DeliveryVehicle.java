package com.harvestflow.api.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A physical delivery vehicle available for route assignment. Distinct
 * from {@code MasterCategory.VEHICLE_TYPES} (which is only a type-name
 * lookup for dropdowns like "Eicher Truck") — this is a specific vehicle
 * with a real load capacity, used to validate route optimization
 * requests against actual capacity limits.
 */
@Entity
@Table(name = "delivery_vehicle")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryVehicle {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String registrationNumber;

    /** Free-text, normally chosen from MasterCategory.VEHICLE_TYPES for consistency with the rest of the app. */
    @Column(nullable = false)
    private String vehicleType;

    @Column(nullable = false)
    private Double capacityKg;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
