package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.RouteStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One route optimization run: a pickup plus one or more delivery stops,
 * optimized for total distance. Optionally tied to the {@link HarvestCase}
 * it was requested for — that link is what makes this the
 * marketplace/logistics integration point (Part C): once a case's produce
 * is ready to move, a route can be optimized against it and the result is
 * visible from the case's own timeline.
 */
@Entity
@Table(name = "route_optimization_result")
@Getter
@Setter
@NoArgsConstructor
public class RouteOptimizationResult {

    @Id
    @GeneratedValue
    private UUID id;

    /** Optional — a route can be optimized standalone (no HarvestCase yet) or against a specific case. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private HarvestCase harvestCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id")
    private DeliveryVehicle vehicle;

    /**
     * Phase 5: optional link to the marketplace order this route was
     * optimized for (V6__phase5_integration.sql) — the real "confirmed
     * MarketplaceOrder -> logistics -> route optimization" path from the
     * master spec (Part 11), alongside the pre-existing {@link #harvestCase}
     * link that Phase 3 used for internal-ops-only routes.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_order_id")
    private MarketOrder marketOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RouteStatus status = RouteStatus.OPTIMIZED;

    private Double totalQuantityKg;

    @Column(nullable = false)
    private Double originalDistanceKm;

    @Column(nullable = false)
    private Double optimizedDistanceKm;

    @Column(nullable = false)
    private Double distanceSavedKm;

    @Column(nullable = false)
    private Double estimatedDurationMinutes;

    @Column(nullable = false)
    private Double estimatedCost;

    @Column(nullable = false)
    private String requestedByUsername;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "routeResult", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceIndex ASC")
    private List<RouteStop> stops = new ArrayList<>();
}
