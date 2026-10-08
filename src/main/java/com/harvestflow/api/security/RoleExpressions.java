package com.harvestflow.api.security;

/**
 * Shared {@code @PreAuthorize} expressions. Marketplace accounts (FARMER, FPO,
 * CONSUMER, BULK_BUYER) live in the same UserRole enum as company staff, so any
 * endpoint that only checked "is logged in" was open to every buyer. Internal
 * case data (farmer phone numbers, purchase rates, invoices, exports) must use
 * {@link #INTERNAL_STAFF}.
 */
public final class RoleExpressions {

    public static final String INTERNAL_STAFF = "hasAnyRole('ADMIN','PLOT_SELECTION','PLANNING','GODOWN','SUPERVISOR',"
            + "'PURCHASE_ACCOUNT','PICKUP_PERSON','TRANSPORT_PERSON','LABOR_COORDINATOR','EICHER_DRIVER')";

    private RoleExpressions() {
    }
}
