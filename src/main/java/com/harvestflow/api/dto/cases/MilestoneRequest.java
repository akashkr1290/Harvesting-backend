package com.harvestflow.api.dto.cases;

import jakarta.validation.constraints.NotBlank;

public record MilestoneRequest(
        @NotBlank String milestone
) {
}
