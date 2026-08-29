package com.harvestflow.api.dto.logistics;

import com.harvestflow.api.domain.entity.RouteOptimizationResult;
import com.harvestflow.api.domain.enums.RouteStatus;

import java.time.Instant;
import java.util.List;

public record RouteOptimizeResponse(
        String id,
        String caseId,
        String marketOrderId,
        String vehicleId,
        String vehicleRegistrationNumber,
        RouteStatus status,
        Double totalQuantityKg,
        double originalDistanceKm,
        double optimizedDistanceKm,
        double distanceSavedKm,
        double estimatedDurationMinutes,
        double estimatedCost,
        String requestedByUsername,
        Instant createdAt,
        List<RouteStopResponse> stops
) {
    public static RouteOptimizeResponse from(RouteOptimizationResult r) {
        return new RouteOptimizeResponse(
                r.getId().toString(),
                r.getHarvestCase() == null ? null : r.getHarvestCase().getId().toString(),
                r.getMarketOrder() == null ? null : r.getMarketOrder().getId().toString(),
                r.getVehicle() == null ? null : r.getVehicle().getId().toString(),
                r.getVehicle() == null ? null : r.getVehicle().getRegistrationNumber(),
                r.getStatus(),
                r.getTotalQuantityKg(),
                r.getOriginalDistanceKm(),
                r.getOptimizedDistanceKm(),
                r.getDistanceSavedKm(),
                r.getEstimatedDurationMinutes(),
                r.getEstimatedCost(),
                r.getRequestedByUsername(),
                r.getCreatedAt(),
                r.getStops().stream().map(RouteStopResponse::from).toList()
        );
    }
}
