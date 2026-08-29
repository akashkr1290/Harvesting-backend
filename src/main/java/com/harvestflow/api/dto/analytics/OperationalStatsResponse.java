package com.harvestflow.api.dto.analytics;

import com.harvestflow.api.domain.enums.CaseStatus;

import java.util.Map;

/**
 * Real figures aggregated from the existing HarvestFlow data (HarvestCase,
 * PurchaseInvoice, SalesInvoice) — every number here reflects this
 * deployment's actual case/invoice records, not marketplace or mock data.
 */
public record OperationalStatsResponse(
        long totalCases,
        Map<CaseStatus, Long> casesByStatus,
        long distinctFarmers,
        long salesCompletedCount,
        double totalNetPayableToFarmers,
        double totalSalesValue
) {
}
