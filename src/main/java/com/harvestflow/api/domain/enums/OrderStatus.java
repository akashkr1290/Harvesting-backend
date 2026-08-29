package com.harvestflow.api.domain.enums;

/**
 * Order lifecycle once a farmer/FPO accepts an offer (Phase 2). Deliberately
 * shallow — Phase 3 owns logistics, so there's no pickup/in-transit/delivered
 * breakdown here, just enough to show buyer and farmer that a deal exists.
 */
public enum OrderStatus {
    CONFIRMED,
    COMPLETED,
    CANCELLED
}
