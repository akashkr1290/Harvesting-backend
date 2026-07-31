package com.harvestflow.api.dto.cases;

import com.harvestflow.api.domain.entity.SalesInvoice;

public record SalesInvoiceResponse(
        String invoiceNumber,
        String buyerCompany,
        Double salesRate,
        Double weight,
        Double taxPercent,
        double subtotal,
        double taxAmount,
        double total
) {
    public static SalesInvoiceResponse from(SalesInvoice inv) {
        if (inv == null) return null;
        return new SalesInvoiceResponse(
                inv.getInvoiceNumber(), inv.getBuyerCompany(), inv.getSalesRate(), inv.getWeight(),
                inv.getTaxPercent(), inv.getSubtotal(), inv.getTaxAmount(), inv.getTotal()
        );
    }
}
