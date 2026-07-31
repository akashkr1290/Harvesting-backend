package com.harvestflow.api.dto.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record LogisticsLineDto(
        @NotBlank String personOrContractorName,
        @NotNull @PositiveOrZero Double amount,
        Double advancePayment,
        String remarks
) {
}
