package com.harvestflow.api.dto.cases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record LaborRequest(
        @NotNull @Valid LogisticsLineDto labor,
        @Valid LogisticsLineDto localLabor
) {
}
