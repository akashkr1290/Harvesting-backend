package com.harvestflow.api.dto.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SalesInvoiceRequest(
        @NotBlank String buyerCompany,
        @NotNull @Positive Double salesRate,
        @NotNull @Positive Double weight,
        @PositiveOrZero Double taxPercent
) {
}
