package com.harvestflow.api.controller;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.dto.cases.*;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.CaseService;
import com.harvestflow.api.service.excel.ExcelExportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;
    private final ExcelExportService excelExportService;

    // ---------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------

    /** Search/filter list (SOW 5.3 D) — by farmer/village/agent/company, optional status. */
    @GetMapping
    public List<CaseSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CaseStatus status
    ) {
        return caseService.search(q, status).stream().map(CaseSummaryResponse::from).toList();
    }

    /** The logged-in user's own pending queue — drives every role's dashboard. */
    @GetMapping("/pending")
    public List<CaseSummaryResponse> pending(@AuthenticationPrincipal SecurityUser principal) {
        return caseService.pendingFor(principal.getUser().getRole()).stream()
                .map(CaseSummaryResponse::from).toList();
    }

    /** Cases currently in transit — feeds the Eicher Truck Driver's milestone screen. */
    @GetMapping("/active-trips")
    public List<CaseSummaryResponse> activeTrips() {
        return caseService.activeTrips().stream().map(CaseSummaryResponse::from).toList();
    }

    /** Full detail for one case — every stage's captured data plus the timeline. */
    @GetMapping("/{id}")
    public CaseDetailResponse detail(@PathVariable UUID id) {
        return CaseDetailResponse.from(caseService.getById(id));
    }

    /** Excel export backing Admin Overview's export button — optionally scoped to one location. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String location) {
        byte[] bytes = excelExportService.generateCaseListWorkbook(caseService.allForExport(location));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("harvestflow-cases.xlsx").build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    // ---------------------------------------------------------------------
    // Stage 1 — Plot Selection
    // ---------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasRole('PLOT_SELECTION')")
    public CaseDetailResponse createPlotSelection(
            @Valid @RequestBody PlotSelectionRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        HarvestCase created = caseService.createPlotSelection(request, principal.getUser());
        return CaseDetailResponse.from(created);
    }

    /** Accept a marketplace handoff and move the case into the existing Planning queue. */
    @PostMapping("/{id}/plot-selection/accept")
    @PreAuthorize("hasRole('PLOT_SELECTION')")
    public CaseDetailResponse acceptPlotSelection(
            @PathVariable UUID id,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.acceptPlotSelection(id, principal.getUser()));
    }

    /** Editable only while status is SUBMITTED_FOR_PLANNING — enforced in the service (SOW 5.3 C). */
    @PutMapping("/{id}/plot-selection")
    @PreAuthorize("hasRole('PLOT_SELECTION')")
    public CaseDetailResponse updatePlotSelection(
            @PathVariable UUID id,
            @Valid @RequestBody PlotSelectionRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.updatePlotSelection(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stage 2 — Planning
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/planning")
    @PreAuthorize("hasRole('PLANNING')")
    public CaseDetailResponse savePlanning(
            @PathVariable UUID id,
            @Valid @RequestBody PlanningRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.savePlanning(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stage 3 — Purchase rate update
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/purchase-rate")
    @PreAuthorize("hasRole('PURCHASE_ACCOUNT')")
    public CaseDetailResponse updatePurchaseRate(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseRateRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.updatePurchaseRate(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stage 4 — Packing material issue
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/packing-issue")
    @PreAuthorize("hasRole('GODOWN')")
    public CaseDetailResponse issuePackingMaterial(
            @PathVariable UUID id,
            @Valid @RequestBody PackingIssueRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.issuePackingMaterial(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stage 5 — Harvest completion / weight slip
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/harvest-completion")
    @PreAuthorize("hasRole('SUPERVISOR')")
    public CaseDetailResponse completeHarvest(
            @PathVariable UUID id,
            @Valid @RequestBody HarvestCompletionRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.completeHarvest(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stage 6 — Pickup
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/pickup")
    @PreAuthorize("hasRole('PICKUP_PERSON')")
    public CaseDetailResponse addPickup(
            @PathVariable UUID id,
            @Valid @RequestBody LogisticsLineDto request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.addPickup(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stage 7 — Transport
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/transport")
    @PreAuthorize("hasRole('TRANSPORT_PERSON')")
    public CaseDetailResponse addTransport(
            @PathVariable UUID id,
            @Valid @RequestBody LogisticsLineDto request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.addTransport(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stages 8–9 — Labor + optional Local Labor
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/labor")
    @PreAuthorize("hasRole('LABOR_COORDINATOR')")
    public CaseDetailResponse addLaborEntries(
            @PathVariable UUID id,
            @Valid @RequestBody LaborRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.addLaborEntries(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stage 10 — Packing material return / reconciliation
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/packing-return")
    @PreAuthorize("hasRole('GODOWN')")
    public CaseDetailResponse returnPackingMaterial(
            @PathVariable UUID id,
            @Valid @RequestBody PackingReturnRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.returnPackingMaterial(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stage 11 — Purchase invoice
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/purchase-invoice")
    @PreAuthorize("hasRole('PURCHASE_ACCOUNT')")
    public CaseDetailResponse createPurchaseInvoice(
            @PathVariable UUID id,
            @Valid @RequestBody PurchaseInvoiceRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.createPurchaseInvoice(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Stage 12 — Sales invoice
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/sales-invoice")
    @PreAuthorize("hasRole('ADMIN')")
    public CaseDetailResponse createSalesInvoice(
            @PathVariable UUID id,
            @Valid @RequestBody SalesInvoiceRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.createSalesInvoice(id, request, principal.getUser()));
    }

    // ---------------------------------------------------------------------
    // Eicher Truck Driver — informational milestone logging
    // ---------------------------------------------------------------------

    @PostMapping("/{id}/milestone")
    @PreAuthorize("hasRole('EICHER_DRIVER')")
    public CaseDetailResponse logMilestone(
            @PathVariable UUID id,
            @Valid @RequestBody MilestoneRequest request,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return CaseDetailResponse.from(caseService.logMilestone(id, request, principal.getUser()));
    }
}
