package com.harvestflow.api.dto.cases;

import com.harvestflow.api.domain.entity.LogisticsEntry;
import com.harvestflow.api.domain.enums.LogisticsKind;

public record LogisticsResponse(
        LogisticsKind kind,
        String personOrContractorName,
        Double amount,
        Double advancePayment,
        String remarks
) {
    public static LogisticsResponse from(LogisticsEntry e) {
        if (e == null) return null;
        return new LogisticsResponse(e.getKind(), e.getPersonOrContractorName(), e.getAmount(),
                e.getAdvancePayment(), e.getRemarks());
    }
}
