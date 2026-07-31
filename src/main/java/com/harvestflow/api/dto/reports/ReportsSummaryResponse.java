package com.harvestflow.api.dto.reports;

import com.harvestflow.api.domain.enums.CaseStatus;

import java.util.List;
import java.util.Map;

public record ReportsSummaryResponse(
        int totalCases,
        int selectionsToday,
        int plannedCount,
        int completedCount,
        Map<CaseStatus, Long> stageWisePending,
        Map<String, Double> purchaseSummaryByFarmer,
        Map<String, Double> salesSummaryByCompany,
        double averageRecovery,
        double averagePulp
) {
}
