package com.harvestflow.api.dto.analytics;

/**
 * One line of the SIH impact dashboard.
 *
 * @param label  which SOW-listed impact area this is ("Farmer earning improvement", etc.)
 * @param status "AVAILABLE" (real, computed from this codebase's own data),
 *               "DEMO_DATA" (computed from Phase 4's own mock subsystems —
 *               labeled as such, never presented as real-world performance),
 *               or "NOT_AVAILABLE" (would require Phase 1-3 marketplace/
 *               forecasting/logistics data that doesn't exist yet)
 * @param value  the figure itself, or null when NOT_AVAILABLE
 * @param note   why — always populated for NOT_AVAILABLE and DEMO_DATA
 */
public record ImpactMetric(String label, String status, String value, String note) {

    public static ImpactMetric available(String label, String value, String note) {
        return new ImpactMetric(label, "AVAILABLE", value, note);
    }

    public static ImpactMetric demoData(String label, String value, String note) {
        return new ImpactMetric(label, "DEMO_DATA", value, note);
    }

    public static ImpactMetric notAvailable(String label, String reason) {
        return new ImpactMetric(label, "NOT_AVAILABLE", null, reason);
    }
}
