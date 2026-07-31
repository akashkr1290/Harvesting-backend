package com.harvestflow.api.dto.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PackingLineDto(
        @NotBlank String itemName,
        @NotNull @Positive Double quantity,
        String unit
) {
}
