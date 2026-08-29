package com.harvestflow.api.dto.logistics;

import jakarta.validation.constraints.NotBlank;

/**
 * A stop to visit. Provide latitude/longitude directly, OR leave them
 * null and the service will try to resolve them from MasterItem entries
 * (LOCATIONS/COMPANIES) whose name matches — see {@code RouteOptimizationService}.
 */
public record RouteStopRequest(
        @NotBlank String name,
        Double latitude,
        Double longitude
) {
}
