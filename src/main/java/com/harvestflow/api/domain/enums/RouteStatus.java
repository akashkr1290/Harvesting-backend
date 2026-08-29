package com.harvestflow.api.domain.enums;

/**
 * Lifecycle of a route optimization result. Phase 3 only ever produces
 * OPTIMIZED (computation is synchronous) or ASSIGNED (once a vehicle is
 * attached) — IN_TRANSIT/DELIVERED/CANCELLED are here so Phase 4/5 and the
 * logistics execution flow have somewhere to record real-world progress
 * without another migration.
 */
public enum RouteStatus {
    OPTIMIZED,
    ASSIGNED,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}
