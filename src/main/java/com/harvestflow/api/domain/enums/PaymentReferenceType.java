package com.harvestflow.api.domain.enums;

/**
 * What a {@link com.harvestflow.api.domain.entity.PaymentRecord} is paying
 * against. Originally limited to the two internal-ops money-movement
 * records (Phase 4 was built with no Order/Transaction entity available —
 * see the git history), so a payment could only be against an existing
 * {@code PurchaseInvoice} (farmer payout) or {@code SalesInvoice}
 * (buyer-side sale). {@link #MARKET_ORDER} was added in Phase 5 once the
 * marketplace's {@code MarketOrder} entity existed, so the simulated
 * "buyer pays, farmer earns" demo step (Part 39 of the master spec) has a
 * real reference row rather than reusing an unrelated invoice record.
 */
public enum PaymentReferenceType {
    /** Paying the farmer — mirrors PurchaseInvoice.getNetPayable(). */
    PURCHASE_INVOICE,
    /** Recording a buyer-side payment — mirrors SalesInvoice.getTotal(). */
    SALES_INVOICE,
    /** Phase 5: a marketplace order's buyer-pays/farmer-earns settlement (simulated). */
    MARKET_ORDER
}
