package com.harvestflow.api.dto.cases;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.domain.enums.UserRole;

import java.time.LocalDate;

public record CaseSummaryResponse(
        String id,
        CaseStatus status,
        String statusLabel,
        UserRole nextActorRole,
        String farmerName,
        String village,
        String location,
        String selectedCompany,
        LocalDate visitDate,
        LocalDate harvestingDate,
        String priority
) {
    public static CaseSummaryResponse from(HarvestCase c) {
        return new CaseSummaryResponse(
                c.getId().toString(),
                c.getStatus(),
                humanize(c.getStatus()),
                c.getStatus().nextActorRole(),
                c.getFarmerName(),
                c.getVillage(),
                c.getLocation(),
                c.getSelectedCompany(),
                c.getVisitDate(),
                c.getHarvestingDate(),
                c.getPriority()
        );
    }

    private static String humanize(CaseStatus status) {
        String[] words = status.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(w.charAt(0)).append(w.substring(1).toLowerCase()).append(' ');
        }
        return sb.toString().trim();
    }
}
