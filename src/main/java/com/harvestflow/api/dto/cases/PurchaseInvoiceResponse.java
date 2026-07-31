package com.harvestflow.api.dto.cases;

import com.harvestflow.api.domain.entity.PurchaseInvoice;

public record PurchaseInvoiceResponse(
        String invoiceNumber,
        Double rate,
        Double weight,
        boolean includeCommission,
        Double commissionAmount,
        boolean includePackingCost,
        Double packingCost,
        boolean includeTransportCost,
        Double transportCost,
        Double otherCharges,
        Double deductions,
        Double additions,
        double baseAmount,
        double netPayable
) {
    public static PurchaseInvoiceResponse from(PurchaseInvoice inv) {
        if (inv == null) return null;
        return new PurchaseInvoiceResponse(
                inv.getInvoiceNumber(), inv.getRate(), inv.getWeight(),
                inv.isIncludeCommission(), inv.getCommissionAmount(),
                inv.isIncludePackingCost(), inv.getPackingCost(),
                inv.isIncludeTransportCost(), inv.getTransportCost(),
                inv.getOtherCharges(), inv.getDeductions(), inv.getAdditions(),
                inv.getBaseAmount(), inv.getNetPayable()
        );
    }
}
