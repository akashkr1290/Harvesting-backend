package com.harvestflow.api.dto.image;

import com.harvestflow.api.domain.enums.ImageDecision;
import jakarta.validation.constraints.NotNull;

public record AdminReviewDecisionRequest(
        @NotNull ImageDecision decision,
        String note
) {
}
