package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.*;
import com.harvestflow.api.domain.enums.*;
import com.harvestflow.api.dto.cases.*;
import com.harvestflow.api.exception.ForbiddenRoleException;
import com.harvestflow.api.exception.InvalidTransitionException;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.HarvestCaseRepository;
import com.harvestflow.api.util.InvoiceNumberGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaseService {

    private final HarvestCaseRepository caseRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final com.harvestflow.api.repository.MarketOrderRepository marketOrderRepository;
    private final PaymentService paymentService;
    private final com.harvestflow.api.service.notification.NotificationService notificationService;

    // -------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------

    public HarvestCase getById(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Case not found: " + id));
    }

    public List<HarvestCase> pendingFor(UserRole role) {
        return caseRepository.findByStatusNotOrderByHarvestingDateAsc(CaseStatus.SALES_COMPLETED).stream()
                .filter(c -> c.getStatus().nextActorRole() == role)
                .toList();
    }

    public List<HarvestCase> search(String query, CaseStatus status) {
        return caseRepository.search(query, status);
    }

    /** Backs the case-list Excel export — optionally scoped to one location, matching Admin Overview's filter. */
    public List<HarvestCase> allForExport(String location) {
        List<HarvestCase> all = caseRepository.findAll();
        if (location == null || location.isBlank()) return all;
        return all.stream().filter(c -> location.equalsIgnoreCase(c.getLocation())).toList();
    }

    public List<HarvestCase> activeTrips() {
        return caseRepository.findByStatusInOrderByHarvestingDateAsc(
                List.of(CaseStatus.PICKUP_ASSIGNED, CaseStatus.TRANSPORT_ASSIGNED));
    }

    // -------------------------------------------------------------------
    // Stage 1 — Plot Selection
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase createPlotSelection(PlotSelectionRequest req, User actor) {
        requireRole(actor, UserRole.PLOT_SELECTION);

        if (req.harvestingDate().isBefore(req.visitDate())) {
            throw new IllegalArgumentException("Harvesting date cannot be before the visit date.");
        }
        if (caseRepository.existsByFarmerNameIgnoreCaseAndLocationIgnoreCaseAndHarvestingDate(
                req.farmerName(), req.location(), req.harvestingDate())) {
            throw new IllegalArgumentException(
                    "A selection already exists for this farmer, location, and harvesting date.");
        }

        HarvestCase c = new HarvestCase();
        applyPlotSelectionFields(c, req);
        c.setCreatedByUsername(actor.getUsername());
        c.setStatus(CaseStatus.SUBMITTED_FOR_PLANNING);
        c.addTimelineEntry(actor.getUsername(), "Created plot selection record");

        return caseRepository.save(c);
    }

    /**
     * Plot Selection Team can edit only until Planning starts — enforced
     * here server-side (SOW 5.3 C), not just hidden client-side.
     */
    @Transactional
    public HarvestCase updatePlotSelection(UUID caseId, PlotSelectionRequest req, User actor) {
        requireRole(actor, UserRole.PLOT_SELECTION);
        HarvestCase c = getById(caseId);

        if (c.getStatus() != CaseStatus.SUBMITTED_FOR_PLANNING) {
            throw new ForbiddenRoleException(
                    "This case is already in Planning or beyond and is now view-only for Plot Selection.");
        }
        if (req.harvestingDate().isBefore(req.visitDate())) {
            throw new IllegalArgumentException("Harvesting date cannot be before the visit date.");
        }

        applyPlotSelectionFields(c, req);
        c.addTimelineEntry(actor.getUsername(), "Updated plot selection details");
        return caseRepository.save(c);
    }

    private void applyPlotSelectionFields(HarvestCase c, PlotSelectionRequest req) {
        c.setLocation(req.location());
        c.setVisitDate(req.visitDate());
        c.setCommissionAgentName(req.commissionAgentName());
        c.setFarmerName(req.farmerName());
        c.setFarmerPhone(req.farmerPhone());
        c.setVillage(req.village());
        c.setNumberOfPlants(req.numberOfPlants());
        c.setHarvestingDate(req.harvestingDate());
        c.setRemark(req.remark());
        c.setVolume(req.volume());
        c.setRecovery(req.recovery());
        c.setPulp(req.pulp());
        c.setSelectedCompany(req.selectedCompany());
        c.setPlotCode(req.plotCode());
        c.setGpsNote(req.gpsNote());
        c.setPriority(req.priority() == null || req.priority().isBlank() ? "medium" : req.priority());
        if (req.photoPath() != null) {
            c.setPhotoPath(req.photoPath());
        }
    }

    // -------------------------------------------------------------------
    // Stage 2 — Planning
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase savePlanning(UUID caseId, PlanningRequest req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.SUBMITTED_FOR_PLANNING);

        PlanningDetails planning = new PlanningDetails();
        planning.setHarvestCase(c);
        planning.setPlannedDate(req.plannedDate());
        planning.setTimeWindow(req.timeWindow());
        planning.setSupervisorName(req.supervisorName());
        planning.setPickupPersonName(req.pickupPersonName());
        planning.setVehiclePlan(req.vehiclePlan());
        planning.setExpectedQuantity(req.expectedQuantity());
        planning.setNotesForGodown(req.notesForGodown());
        planning.setNotesForSupervisor(req.notesForSupervisor());
        c.setPlanning(planning);

        c.setStatus(CaseStatus.PLANNED);
        c.addTimelineEntry(actor.getUsername(), "Created harvest plan — supervisor " + req.supervisorName());
        return caseRepository.save(c);
    }

    // -------------------------------------------------------------------
    // Stage 3 — Purchase rate update
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase updatePurchaseRate(UUID caseId, PurchaseRateRequest req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.PLANNED);

        c.setRatePerUnit(req.ratePerUnit());
        c.setStatus(CaseStatus.RATE_UPDATED);
        c.addTimelineEntry(actor.getUsername(), "Updated purchase rate to " + req.ratePerUnit() + "/unit");
        return caseRepository.save(c);
    }

    // -------------------------------------------------------------------
    // Stage 4 — Packing material issue
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase issuePackingMaterial(UUID caseId, PackingIssueRequest req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.RATE_UPDATED);

        PackingMaterialRecord record = new PackingMaterialRecord();
        record.setHarvestCase(c);
        record.setStage(PackingStage.ISSUE);
        record.setHandledBy(req.issuedTo());
        record.setRemarks(req.remarks());
        req.lines().forEach(line -> record.addLine(line.itemName(), line.quantity(), line.unit()));
        c.getPackingRecords().add(record);

        c.setStatus(CaseStatus.PACKING_MATERIAL_ISSUED);
        c.addTimelineEntry(actor.getUsername(),
                "Issued " + req.lines().size() + " packing item(s) to " + req.issuedTo());
        return caseRepository.save(c);
    }

    // -------------------------------------------------------------------
    // Stage 5 — Harvest completion / weight slip
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase completeHarvest(UUID caseId, HarvestCompletionRequest req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.PACKING_MATERIAL_ISSUED);

        HarvestCompletion completion = new HarvestCompletion();
        completion.setHarvestCase(c);
        completion.setActualDate(req.actualDate());
        completion.setActualBagsOrCrates(req.actualBagsOrCrates());
        completion.setActualWeight(req.actualWeight());
        completion.setQualityNotes(req.qualityNotes());
        completion.setWastageNotes(req.wastageNotes());
        completion.setFinalVolume(req.finalVolume());
        completion.setFinalRecovery(req.finalRecovery());
        completion.setFinalPulp(req.finalPulp());
        completion.setRemarks(req.remarks());
        completion.setWeightSlipFileName(req.weightSlipFileName());
        c.setCompletion(completion);

        c.setStatus(CaseStatus.HARVESTING_COMPLETED);
        c.addTimelineEntry(actor.getUsername(),
                "Confirmed harvest completion — " + req.actualWeight() + " kg actual weight");
        return caseRepository.save(c);
    }

    // -------------------------------------------------------------------
    // Stage 6 — Pickup
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase addPickup(UUID caseId, LogisticsLineDto req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.HARVESTING_COMPLETED);

        c.getLogisticsEntries().add(buildLogisticsEntry(c, LogisticsKind.PICKUP, req));
        c.setStatus(CaseStatus.PICKUP_ASSIGNED);
        c.addTimelineEntry(actor.getUsername(), "Added pickup driver " + req.personOrContractorName());
        return caseRepository.save(c);
    }

    // -------------------------------------------------------------------
    // Stage 7 — Transport
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase addTransport(UUID caseId, LogisticsLineDto req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.PICKUP_ASSIGNED);

        c.getLogisticsEntries().add(buildLogisticsEntry(c, LogisticsKind.TRANSPORT, req));
        c.setStatus(CaseStatus.TRANSPORT_ASSIGNED);
        c.addTimelineEntry(actor.getUsername(), "Added transporter " + req.personOrContractorName());
        return caseRepository.save(c);
    }

    // -------------------------------------------------------------------
    // Stages 8–9 — Labor + optional Local Labor, entered together since one
    // role owns both and a case can only sit in one queue at a time.
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase addLaborEntries(UUID caseId, LaborRequest req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.TRANSPORT_ASSIGNED);

        c.getLogisticsEntries().add(buildLogisticsEntry(c, LogisticsKind.LABOR, req.labor()));
        String note = "Recorded labor cost — " + req.labor().personOrContractorName();
        if (req.localLabor() != null) {
            c.getLogisticsEntries().add(buildLogisticsEntry(c, LogisticsKind.LOCAL_LABOR, req.localLabor()));
            note = "Recorded labor (" + req.labor().personOrContractorName() + ") and local labor ("
                    + req.localLabor().personOrContractorName() + ") costs";
        }

        c.setStatus(CaseStatus.COST_ENTRIES_DONE);
        c.addTimelineEntry(actor.getUsername(), note);
        return caseRepository.save(c);
    }

    private LogisticsEntry buildLogisticsEntry(HarvestCase c, LogisticsKind kind, LogisticsLineDto req) {
        LogisticsEntry entry = new LogisticsEntry();
        entry.setHarvestCase(c);
        entry.setKind(kind);
        entry.setPersonOrContractorName(req.personOrContractorName());
        entry.setAmount(req.amount());
        entry.setAdvancePayment(req.advancePayment() == null ? 0.0 : req.advancePayment());
        entry.setRemarks(req.remarks());
        return entry;
    }

    // -------------------------------------------------------------------
    // Stage 10 — Packing material return / reconciliation
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase returnPackingMaterial(UUID caseId, PackingReturnRequest req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.COST_ENTRIES_DONE);

        PackingMaterialRecord record = new PackingMaterialRecord();
        record.setHarvestCase(c);
        record.setStage(PackingStage.RETURN);
        record.setHandledBy(actor.getUsername());
        record.setRemarks(req.remarks());
        req.lines().forEach(line -> record.addLine(line.itemName(), line.quantity(), line.unit()));
        c.getPackingRecords().add(record);

        c.setStatus(CaseStatus.MATERIAL_RECONCILED);
        c.addTimelineEntry(actor.getUsername(), "Reconciled packing material return");
        return caseRepository.save(c);
    }

    // -------------------------------------------------------------------
    // Stage 11 — Purchase invoice
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase createPurchaseInvoice(UUID caseId, PurchaseInvoiceRequest req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.MATERIAL_RECONCILED);

        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setHarvestCase(c);
        invoice.setInvoiceNumber(invoiceNumberGenerator.nextPurchaseInvoiceNumber());
        invoice.setRate(req.rate());
        invoice.setWeight(req.weight());
        invoice.setIncludeCommission(req.includeCommission());
        invoice.setCommissionAmount(req.commissionAmount());
        invoice.setIncludePackingCost(req.includePackingCost());
        invoice.setPackingCost(req.packingCost());
        invoice.setIncludeTransportCost(req.includeTransportCost());
        invoice.setTransportCost(req.transportCost());
        invoice.setOtherCharges(req.otherCharges());
        invoice.setDeductions(req.deductions());
        invoice.setAdditions(req.additions());
        c.setPurchaseInvoice(invoice);

        c.setStatus(CaseStatus.PURCHASE_COMPLETED);
        c.addTimelineEntry(actor.getUsername(),
                "Generated purchase invoice " + invoice.getInvoiceNumber()
                        + " — net payable " + String.format("%.2f", invoice.getNetPayable()));
        return caseRepository.save(c);
    }

    // -------------------------------------------------------------------
    // Stage 12 — Sales invoice
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase createSalesInvoice(UUID caseId, SalesInvoiceRequest req, User actor) {
        HarvestCase c = getById(caseId);
        assertTransition(c, actor, CaseStatus.PURCHASE_COMPLETED);

        SalesInvoice invoice = new SalesInvoice();
        invoice.setHarvestCase(c);
        invoice.setInvoiceNumber(invoiceNumberGenerator.nextSalesInvoiceNumber());

        com.harvestflow.api.domain.entity.MarketOrder marketOrder = c.getMarketOrderId() == null
                ? null
                : marketOrderRepository.findById(c.getMarketOrderId()).orElse(null);

        // Marketplace price/quantity are locked at offer acceptance. Do not
        // allow the internal sales-invoice form to silently change the farmer's
        // agreed marketplace price.
        if (marketOrder != null) {
            invoice.setBuyerCompany(marketOrder.getBuyer().getName());
            invoice.setSalesRate(marketOrder.getAgreedPricePerUnit());
            invoice.setWeight(c.getCompletion() != null && c.getCompletion().getActualWeight() != null
                    ? c.getCompletion().getActualWeight()
                    : marketOrder.getQuantity());
        } else {
            invoice.setBuyerCompany(req.buyerCompany());
            invoice.setSalesRate(req.salesRate());
            invoice.setWeight(req.weight());
        }
        invoice.setTaxPercent(req.taxPercent());
        c.setSalesInvoice(invoice);

        c.setStatus(CaseStatus.SALES_COMPLETED);
        c.addTimelineEntry(actor.getUsername(),
                "Generated sales invoice " + invoice.getInvoiceNumber() + " to " + invoice.getBuyerCompany());

        HarvestCase savedCase = caseRepository.save(c);

        if (marketOrder != null && marketOrder.getStatus() == com.harvestflow.api.domain.enums.OrderStatus.CONFIRMED) {
            marketOrder.setStatus(com.harvestflow.api.domain.enums.OrderStatus.COMPLETED);
            marketOrder.setDeliveredAt(java.time.Instant.now());
            com.harvestflow.api.domain.entity.MarketOrder savedOrder = marketOrderRepository.save(marketOrder);

            paymentService.initiate(
                    com.harvestflow.api.domain.enums.PaymentReferenceType.MARKET_ORDER,
                    savedOrder.getId(),
                    savedOrder.getTotalAmount(),
                    "INR",
                    "Marketplace order settlement after internal delivery/invoicing",
                    actor.getUsername()
            );

            notificationService.notify(
                    savedOrder.getSeller().getUsername(),
                    com.harvestflow.api.domain.enums.NotificationCategory.PAYMENT,
                    "Marketplace delivery completed",
                    "Order " + savedOrder.getId() + " reached final internal delivery/invoicing and payment was released (simulated).",
                    "MARKET_ORDER",
                    savedOrder.getId()
            );
            notificationService.notify(
                    savedOrder.getBuyer().getUsername(),
                    com.harvestflow.api.domain.enums.NotificationCategory.PAYMENT,
                    "Order delivered",
                    "Your " + savedOrder.getCropName() + " order reached final delivery and payment was completed (simulated).",
                    "MARKET_ORDER",
                    savedOrder.getId()
            );
        }

        return savedCase;
    }

    // -------------------------------------------------------------------
    // Eicher Truck Driver milestones — informational, doesn't gate the
    // state machine (no status in the SOW's table is owned by this role).
    // -------------------------------------------------------------------

    @Transactional
    public HarvestCase logMilestone(UUID caseId, MilestoneRequest req, User actor) {
        requireRole(actor, UserRole.EICHER_DRIVER);
        HarvestCase c = getById(caseId);

        if (c.getStatus() != CaseStatus.PICKUP_ASSIGNED && c.getStatus() != CaseStatus.TRANSPORT_ASSIGNED) {
            throw new InvalidTransitionException("This case isn't currently in an active trip.");
        }

        c.addTimelineEntry(actor.getUsername(), req.milestone());
        return caseRepository.save(c);
    }

    // -------------------------------------------------------------------
    // Shared enforcement helpers
    // -------------------------------------------------------------------

    private void requireRole(User actor, UserRole required) {
        if (actor.getRole() != required) {
            throw new ForbiddenRoleException(
                    "This action belongs to " + required + ", not " + actor.getRole() + ".");
        }
    }

    /**
     * The core guard used by every stage transition: the case must
     * currently be in the exact status this action expects, AND the
     * requesting user's role must be the one that status's
     * {@link CaseStatus#nextActorRole()} names. Both checks matter —
     * status alone would let any authenticated user advance any case;
     * role alone would let the right role act on a case at the wrong
     * stage.
     */
    private void assertTransition(HarvestCase c, User actor, CaseStatus expectedStatus) {
        if (c.getStatus() != expectedStatus) {
            throw new InvalidTransitionException(
                    "Case is currently \"" + c.getStatus() + "\"; this action requires \"" + expectedStatus + "\".");
        }
        UserRole requiredRole = expectedStatus.nextActorRole();
        if (actor.getRole() != requiredRole) {
            throw new ForbiddenRoleException(
                    "This action belongs to " + requiredRole + ", not " + actor.getRole() + ".");
        }
    }
}
