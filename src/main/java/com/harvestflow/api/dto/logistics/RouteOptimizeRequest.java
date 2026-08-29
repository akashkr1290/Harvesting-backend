package com.harvestflow.api.dto.logistics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/**
 * Optimize a route from one pickup to one or more delivery stops.
 * {@code caseId} is optional — the original (Phase 3) marketplace/logistics
 * integration point: pass the HarvestCase this delivery is for and the
 * result gets linked to it and logged on its timeline. {@code marketOrderId}
 * is the Phase 5 equivalent for a real marketplace order (Part 11 of the
 * master spec: confirmed MarketplaceOrder -> logistics -> route
 * optimization) — both are optional and independent; a route may be linked
 * to neither, either, or (unusually) both. {@code vehicleId} is optional
 * too; when given, the request is validated against that vehicle's real
 * capacity.
 */
public record RouteOptimizeRequest(
        UUID caseId,
        UUID marketOrderId,
        UUID vehicleId,

        @NotNull @Valid RouteStopRequest pickup,

        @NotEmpty List<@Valid RouteStopRequest> deliveries,

        @NotNull @Positive Double totalQuantityKg,

        /** INR per km used to estimate cost when no vehicle-specific rate is configured. */
        Double costPerKm
) {
    public RouteOptimizeRequest {
        if (costPerKm == null) {
            costPerKm = 20.0;
        }
    }
}
