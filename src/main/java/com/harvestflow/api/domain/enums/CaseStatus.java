package com.harvestflow.api.domain.enums;

/**
 * The canonical stage sequence (see Working Approach doc, section 1).
 * Declaration order matters — {@link #ordinal()} is used to enforce that
 * transitions only ever move forward, never skip or reverse.
 */
public enum CaseStatus {
    /** Marketplace orders wait here until Plot Selection accepts the handoff. */
    AWAITING_PLOT_SELECTION,
    SUBMITTED_FOR_PLANNING,
    PLANNED,
    RATE_UPDATED,
    PACKING_MATERIAL_ISSUED,
    HARVESTING_COMPLETED,
    PICKUP_ASSIGNED,
    TRANSPORT_ASSIGNED,
    COST_ENTRIES_DONE,
    MATERIAL_RECONCILED,
    PURCHASE_COMPLETED,
    SALES_COMPLETED;

    /** Which role owns the NEXT action while a case sits in this status. */
    public UserRole nextActorRole() {
        return switch (this) {
            case AWAITING_PLOT_SELECTION -> UserRole.PLOT_SELECTION;
            case SUBMITTED_FOR_PLANNING -> UserRole.PLANNING;
            case PLANNED -> UserRole.PURCHASE_ACCOUNT;
            case RATE_UPDATED -> UserRole.GODOWN;
            case PACKING_MATERIAL_ISSUED -> UserRole.SUPERVISOR;
            case HARVESTING_COMPLETED -> UserRole.PICKUP_PERSON;
            case PICKUP_ASSIGNED -> UserRole.TRANSPORT_PERSON;
            case TRANSPORT_ASSIGNED -> UserRole.LABOR_COORDINATOR;
            case COST_ENTRIES_DONE -> UserRole.GODOWN;
            case MATERIAL_RECONCILED -> UserRole.PURCHASE_ACCOUNT;
            case PURCHASE_COMPLETED -> UserRole.ADMIN; // Accounts/Sales
            case SALES_COMPLETED -> UserRole.ADMIN;
        };
    }

    /** SALES_COMPLETED is the only terminal state — nothing is "pending" on it. */
    public boolean isTerminal() {
        return this == SALES_COMPLETED;
    }
}
