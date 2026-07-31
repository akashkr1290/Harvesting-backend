package com.harvestflow.api.dto.cases;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.entity.PackingMaterialRecord;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.domain.enums.LogisticsKind;
import com.harvestflow.api.domain.enums.PackingStage;
import com.harvestflow.api.domain.enums.UserRole;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record CaseDetailResponse(
        String id,
        CaseStatus status,
        UserRole nextActorRole,
        boolean plotSelectionEditable,
        Instant createdAt,
        String createdByUsername,

        // Plot Selection
        String location,
        LocalDate visitDate,
        String commissionAgentName,
        String farmerName,
        String farmerPhone,
        String village,
        Integer numberOfPlants,
        LocalDate harvestingDate,
        String remark,
        Double volume,
        Double recovery,
        Double pulp,
        String selectedCompany,
        String plotCode,
        String gpsNote,
        String priority,
        String photoPath,

        Double ratePerUnit,
        PlanningResponse planning,
        PackingRecordResponse packingIssue,
        PackingRecordResponse packingReturn,
        CompletionResponse completion,
        LogisticsResponse pickup,
        LogisticsResponse transport,
        LogisticsResponse labor,
        LogisticsResponse localLabor,
        PurchaseInvoiceResponse purchaseInvoice,
        SalesInvoiceResponse salesInvoice,
        List<TimelineEntryResponse> timeline
) {
    public static CaseDetailResponse from(HarvestCase c) {
        Optional<PackingMaterialRecord> issue = c.getPackingRecords().stream()
                .filter(r -> r.getStage() == PackingStage.ISSUE).findFirst();
        Optional<PackingMaterialRecord> ret = c.getPackingRecords().stream()
                .filter(r -> r.getStage() == PackingStage.RETURN).findFirst();

        var pickup = c.getLogisticsEntries().stream().filter(e -> e.getKind() == LogisticsKind.PICKUP).findFirst();
        var transport = c.getLogisticsEntries().stream().filter(e -> e.getKind() == LogisticsKind.TRANSPORT).findFirst();
        var labor = c.getLogisticsEntries().stream().filter(e -> e.getKind() == LogisticsKind.LABOR).findFirst();
        var localLabor = c.getLogisticsEntries().stream().filter(e -> e.getKind() == LogisticsKind.LOCAL_LABOR).findFirst();

        List<TimelineEntryResponse> timeline = c.getTimeline().stream()
                .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .map(TimelineEntryResponse::from)
                .toList();

        return new CaseDetailResponse(
                c.getId().toString(),
                c.getStatus(),
                c.getStatus().nextActorRole(),
                c.getStatus() == CaseStatus.SUBMITTED_FOR_PLANNING,
                c.getCreatedAt(),
                c.getCreatedByUsername(),
                c.getLocation(), c.getVisitDate(), c.getCommissionAgentName(), c.getFarmerName(),
                c.getFarmerPhone(), c.getVillage(), c.getNumberOfPlants(), c.getHarvestingDate(),
                c.getRemark(), c.getVolume(), c.getRecovery(), c.getPulp(), c.getSelectedCompany(),
                c.getPlotCode(), c.getGpsNote(), c.getPriority(), c.getPhotoPath(),
                c.getRatePerUnit(),
                PlanningResponse.from(c.getPlanning()),
                PackingRecordResponse.from(issue.orElse(null)),
                PackingRecordResponse.from(ret.orElse(null)),
                CompletionResponse.from(c.getCompletion()),
                LogisticsResponse.from(pickup.orElse(null)),
                LogisticsResponse.from(transport.orElse(null)),
                LogisticsResponse.from(labor.orElse(null)),
                LogisticsResponse.from(localLabor.orElse(null)),
                PurchaseInvoiceResponse.from(c.getPurchaseInvoice()),
                SalesInvoiceResponse.from(c.getSalesInvoice()),
                timeline
        );
    }
}
