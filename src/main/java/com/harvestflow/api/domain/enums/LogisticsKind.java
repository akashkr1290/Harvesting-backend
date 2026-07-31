package com.harvestflow.api.domain.enums;

/**
 * SOW steps 6, 8, 9, 10 are structurally identical: a name, an amount, an
 * optional advance payment, optional remarks. Modeled as one entity type
 * tagged with a kind rather than four near-duplicate tables.
 */
public enum LogisticsKind {
    PICKUP,
    TRANSPORT,
    LABOR,
    LOCAL_LABOR
}
