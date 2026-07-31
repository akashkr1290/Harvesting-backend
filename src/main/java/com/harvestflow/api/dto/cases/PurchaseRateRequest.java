package com.harvestflow.api.dto.cases;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseRateRequest(
        @NotNull @Positive Double ratePerUnit
) {
}
