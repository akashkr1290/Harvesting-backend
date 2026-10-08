package com.harvestflow.api.domain.enums;

/**
 * How a marketplace order reaches the buyer.
 * <ul>
 *   <li>{@link #ASSISTED}: the company runs its internal 11-stage harvest workflow
 *       (plot, planning, packing, logistics, invoices). Used for bulk lots.</li>
 *   <li>{@link #DIRECT}: small orders; the platform only connects farmer and buyer
 *       and arranges a delivery person. No internal case is created, so the
 *       company does not become another middle layer.</li>
 * </ul>
 */
public enum FulfilmentMode {
    ASSISTED,
    DIRECT
}
