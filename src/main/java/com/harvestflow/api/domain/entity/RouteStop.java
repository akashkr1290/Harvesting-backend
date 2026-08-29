package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.StopType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** One stop in an optimized route's final sequence. */
@Entity
@Table(name = "route_stop")
@Getter
@Setter
@NoArgsConstructor
public class RouteStop {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_result_id", nullable = false)
    private RouteOptimizationResult routeResult;

    /** Position in the optimized visiting order, starting at 0 (the pickup). */
    @Column(nullable = false)
    private Integer sequenceIndex;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StopType stopType;
}
