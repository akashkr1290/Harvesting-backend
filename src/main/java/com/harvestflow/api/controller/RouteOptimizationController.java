package com.harvestflow.api.controller;

import com.harvestflow.api.domain.entity.RouteOptimizationResult;
import com.harvestflow.api.dto.logistics.RouteOptimizeRequest;
import com.harvestflow.api.dto.logistics.RouteOptimizeResponse;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.RouteOptimizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Part B (route optimization) + Part C (marketplace-to-logistics
 * integration — {@code caseId} on the request, see
 * {@link RouteOptimizationService}). Triggering an optimization is
 * restricted to the roles that actually move produce; any authenticated
 * user can look up a result.
 */
@RestController
@RequestMapping("/api/logistics/routes")
@RequiredArgsConstructor
public class RouteOptimizationController {

    private final RouteOptimizationService routeOptimizationService;

    @PostMapping("/optimize")
    @PreAuthorize("hasAnyRole('ADMIN', 'PICKUP_PERSON', 'TRANSPORT_PERSON')")
    @ResponseStatus(HttpStatus.CREATED)
    public RouteOptimizeResponse optimize(
            @Valid @RequestBody RouteOptimizeRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        RouteOptimizationResult result = routeOptimizationService.optimize(request, principal.getUser().getUsername());
        return RouteOptimizeResponse.from(result);
    }

    /**
     * Plans the day's pickups across several trucks (AI service, OR-Tools). Each stop needs
     * coordinates or a Master Data name that has them, and its load in kg.
     */
    @PostMapping("/fleet")
    @PreAuthorize("hasAnyRole('ADMIN', 'PICKUP_PERSON', 'TRANSPORT_PERSON', 'PLANNING')")
    public com.fasterxml.jackson.databind.JsonNode planFleet(@Valid @RequestBody FleetPlanRequest request) {
        return routeOptimizationService.planFleet(request.depot(), request.stops(), request.vehicleIds(),
                request.returnToDepot() == null || request.returnToDepot());
    }

    public record FleetPlanRequest(
            @jakarta.validation.constraints.NotNull @Valid com.harvestflow.api.dto.logistics.RouteStopRequest depot,
            @jakarta.validation.constraints.NotEmpty List<RouteOptimizationService.FleetStop> stops,
            @jakarta.validation.constraints.NotEmpty List<UUID> vehicleIds,
            Boolean returnToDepot) {
    }

    @GetMapping("/{id}")
    public RouteOptimizeResponse detail(@PathVariable UUID id) {
        return RouteOptimizeResponse.from(routeOptimizationService.getById(id));
    }

    /** Every route optimization run against a given case, most recent first. */
    @GetMapping("/by-case/{caseId}")
    public List<RouteOptimizeResponse> byCase(@PathVariable UUID caseId) {
        return routeOptimizationService.getByCase(caseId).stream().map(RouteOptimizeResponse::from).toList();
    }

    /** Phase 5: every route optimization run against a given marketplace order, most recent first. */
    @GetMapping("/by-order/{marketOrderId}")
    public List<RouteOptimizeResponse> byMarketOrder(@PathVariable UUID marketOrderId) {
        return routeOptimizationService.getByMarketOrder(marketOrderId).stream().map(RouteOptimizeResponse::from).toList();
    }
}
