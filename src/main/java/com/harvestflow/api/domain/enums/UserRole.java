package com.harvestflow.api.domain.enums;

/** All roles defined in the SOW (section 3). */
public enum UserRole {
    ADMIN,
    PLOT_SELECTION,
    PLANNING,
    GODOWN,
    SUPERVISOR,
    PURCHASE_ACCOUNT,
    PICKUP_PERSON,
    TRANSPORT_PERSON,
    LABOR_COORDINATOR,
    EICHER_DRIVER,

    // ---- Phase 2: Marketplace roles (separate from the internal
    // harvest-operations roles above — a marketplace participant never
    // touches the plot-selection-through-sales-invoice pipeline). ----
    FARMER,
    FPO,
    CONSUMER,
    BULK_BUYER
}
