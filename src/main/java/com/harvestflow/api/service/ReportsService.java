package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.dto.reports.ReportsSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.harvestflow.api.repository.HarvestCaseRepository;

@Service
@RequiredArgsConstructor
public class ReportsService {

    private final HarvestCaseRepository caseRepository;

    public ReportsSummaryResponse summary() {
        List<HarvestCase> cases = caseRepository.findAll();
        LocalDate today = LocalDate.now();

        Map<CaseStatus, Long> stageWisePending = new HashMap<>();
        for (CaseStatus status : CaseStatus.values()) {
            stageWisePending.put(status, cases.stream().filter(c -> c.getStatus() == status).count());
        }

        Map<String, Double> purchaseByFarmer = new HashMap<>();
        Map<String, Double> salesByCompany = new HashMap<>();
        for (HarvestCase c : cases) {
            if (c.getPurchaseInvoice() != null) {
                purchaseByFarmer.merge(c.getFarmerName(), c.getPurchaseInvoice().getNetPayable(), Double::sum);
            }
            if (c.getSalesInvoice() != null) {
                salesByCompany.merge(c.getSalesInvoice().getBuyerCompany(), c.getSalesInvoice().getTotal(), Double::sum);
            }
        }

        double avgRecovery = cases.stream().mapToDouble(c -> nz(c.getRecovery())).average().orElse(0.0);
        double avgPulp = cases.stream().mapToDouble(c -> nz(c.getPulp())).average().orElse(0.0);

        int selectionsToday = (int) cases.stream()
                .filter(c -> c.getVisitDate() != null && c.getVisitDate().isEqual(today)).count();
        int plannedCount = (int) cases.stream().filter(c -> c.getStatus() == CaseStatus.PLANNED).count();
        int completedCount = (int) cases.stream()
                .filter(c -> c.getStatus() == CaseStatus.HARVESTING_COMPLETED).count();

        return new ReportsSummaryResponse(
                cases.size(),
                selectionsToday,
                plannedCount,
                completedCount,
                stageWisePending,
                purchaseByFarmer,
                salesByCompany,
                avgRecovery,
                avgPulp
        );
    }

    private double nz(Double value) {
        return value == null ? 0.0 : value;
    }
}
