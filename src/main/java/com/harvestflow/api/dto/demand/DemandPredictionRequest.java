package com.harvestflow.api.dto.demand;

import jakarta.validation.constraints.NotBlank;

/** Generate a new forecast for one crop/location pair. */
public record DemandPredictionRequest(
        @NotBlank String cropType,
        @NotBlank String location
) {
}
