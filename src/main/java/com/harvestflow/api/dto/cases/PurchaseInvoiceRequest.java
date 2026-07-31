package com.harvestflow.api.dto.cases;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PurchaseInvoiceRequest(
        @NotNull @Positive Double rate,
        @NotNull @Positive Double weight,
        boolean includeCommission,
        Double commissionAmount,
        boolean includePackingCost,
        Double packingCost,
        boolean includeTransportCost,
        Double transportCost,
        Double otherCharges,
        Double deductions,
        Double additions
) {
}
