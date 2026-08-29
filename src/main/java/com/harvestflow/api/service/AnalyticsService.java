package com.harvestflow.api.service;

import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.domain.enums.ImageDecision;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import com.harvestflow.api.domain.enums.OrderStatus;
import com.harvestflow.api.domain.enums.PaymentStatus;
import com.harvestflow.api.dto.analytics.ImpactMetric;
import com.harvestflow.api.dto.analytics.OperationalStatsResponse;
import com.harvestflow.api.dto.analytics.SihImpactResponse;
import com.harvestflow.api.dto.analytics.VerificationStatsResponse;
import com.harvestflow.api.repository.DemandPredictionRepository;
import com.harvestflow.api.repository.HarvestCaseRepository;
import com.harvestflow.api.repository.MarketOrderRepository;
import com.harvestflow.api.repository.PaymentRecordRepository;
import com.harvestflow.api.repository.ProduceImageRepository;
import com.harvestflow.api.repository.PurchaseInvoiceRepository;
import com.harvestflow.api.repository.RouteOptimizationResultRepository;
import com.harvestflow.api.repository.SalesInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 4, Part C ("Marketplace/SIH impact analytics"), extended in Phase 5
 * once the marketplace/logistics/forecast data Phase 4 was built without
 * actually existed in the merged codebase. See {@link #getSihImpact()} —
 * every line is still traced to a real query or an explicitly-labeled
 * demo/mock source; nothing is estimated or invented. In particular,
 * "reduced intermediary dependency" and "consumer/buyer price improvement"
 * remain NOT_AVAILABLE even after Phase 5: this app has no traditional-
 * channel (mandi/agent) price baseline to compare against, so a real
 * savings percentage cannot be computed from data that doesn't exist here,
 * only order volume/count can be reported as AVAILABLE.
 *
 * <p>{@link #getOperationalStats()} does a full-table scan of purchase/sales
 * invoices to sum amounts (there's no running total maintained anywhere in
 * this codebase). Fine at this app's actual scale (an internal ops tool);
 * would need a materialized/aggregate query at real marketplace volume.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ProduceImageRepository produceImageRepository;
    private final HarvestCaseRepository harvestCaseRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final MarketOrderRepository marketOrderRepository;
    private final RouteOptimizationResultRepository routeOptimizationResultRepository;
    private final DemandPredictionRepository demandPredictionRepository;

    public VerificationStatsResponse getVerificationStats() {
        long total = produceImageRepository.count();

        Map<ImageVerificationStatus, Long> byStatus = new EnumMap<>(ImageVerificationStatus.class);
        for (ImageVerificationStatus status : ImageVerificationStatus.values()) {
            byStatus.put(status, produceImageRepository.countByVerificationStatus(status));
        }

        Map<ImageDecision, Long> byDecision = new EnumMap<>(ImageDecision.class);
        for (ImageDecision decision : ImageDecision.values()) {
            byDecision.put(decision, produceImageRepository.countByDecision(decision));
        }

        long pendingReview = byDecision.getOrDefault(ImageDecision.MANUAL_REVIEW, 0L);
        Double acceptanceRate = total == 0 ? null : byDecision.getOrDefault(ImageDecision.ACCEPT, 0L) / (double) total;

        return new VerificationStatsResponse(total, byStatus, byDecision, pendingReview, acceptanceRate);
    }

    public OperationalStatsResponse getOperationalStats() {
        long totalCases = harvestCaseRepository.count();

        Map<CaseStatus, Long> byStatus = new EnumMap<>(CaseStatus.class);
        for (CaseStatus status : CaseStatus.values()) {
            byStatus.put(status, harvestCaseRepository.countByStatus(status));
        }

        double totalNetPayable = purchaseInvoiceRepository.findAll().stream()
                .mapToDouble(pi -> pi.getNetPayable())
                .sum();
        double totalSalesValue = salesInvoiceRepository.findAll().stream()
                .mapToDouble(si -> si.getTotal())
                .sum();

        return new OperationalStatsResponse(
                totalCases,
                byStatus,
                harvestCaseRepository.countDistinctFarmers(),
                byStatus.getOrDefault(CaseStatus.SALES_COMPLETED, 0L),
                totalNetPayable,
                totalSalesValue
        );
    }

    /**
     * SOW "SIH IMPACT" section, mapped one line per listed impact area.
     * Every AVAILABLE/DEMO_DATA figure traces to a real query above or to
     * Phase 4's own (clearly-labeled) mock payment subsystem — nothing here
     * is invented. See {@code docs/phase4-integration-notes.md} §Analytics
     * for the full rationale.
     */
    public SihImpactResponse getSihImpact() {
        VerificationStatsResponse verification = getVerificationStats();
        long flaggedOrBlocked = verification.countsByDecision().getOrDefault(ImageDecision.MANUAL_REVIEW, 0L)
                + verification.countsByDecision().getOrDefault(ImageDecision.REJECT, 0L);

        long succeededPayments = paymentRecordRepository.countByStatus(PaymentStatus.SUCCEEDED);
        long failedPayments = paymentRecordRepository.countByStatus(PaymentStatus.FAILED);
        double mockPaymentValue = paymentRecordRepository.findByStatusOrderByCreatedAtDesc(PaymentStatus.SUCCEEDED)
                .stream().mapToDouble(p -> p.getAmount()).sum();

        // Phase 5: real MarketOrder rows now exist (Phase 2's marketplace) —
        // folded into the "reduced intermediary dependency" note below
        // rather than a new metric line, so the count is visible without
        // claiming a comparative reduction this codebase can't compute.
        long totalOrders = marketOrderRepository.findAll().size();
        double directFarmerRevenue = marketOrderRepository.findAll().stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(o -> o.getTotalAmount()).sum();

        // Phase 5: real RouteOptimizationResult rows now exist (Phase 3's logistics).
        var routeResults = routeOptimizationResultRepository.findAll();
        double totalDistanceSavedKm = routeResults.stream().mapToDouble(r -> r.getDistanceSavedKm()).sum();
        long routesOptimized = routeResults.size();

        // Phase 5: real DemandPrediction rows now exist (Phase 3's forecasting).
        long forecastsGenerated = demandPredictionRepository.count();

        List<ImpactMetric> metrics = List.of(
                ImpactMetric.available(
                        "Trust in crop images",
                        verification.acceptanceRate() == null
                                ? "No images uploaded yet"
                                : String.format(Locale.ROOT, "%.0f%% of %d uploaded photos passed automated authenticity checks; %d flagged or rejected",
                                verification.acceptanceRate() * 100, verification.totalImages(), flaggedOrBlocked),
                        "Computed from this deployment's own produce_image verification results (Phase 4)."
                ),
                ImpactMetric.demoData(
                        "Marketplace transparency (payment recording)",
                        String.format(Locale.ROOT, "%d simulated payments succeeded (%.2f INR), %d failed",
                                succeededPayments, mockPaymentValue, failedPayments),
                        "MOCK — no real payment gateway is integrated (see MockPaymentGatewayService). Demonstrates the recording/notification flow only; not a real transaction value."
                ),
                ImpactMetric.notAvailable(
                        "Farmer earning improvement vs. traditional channel",
                        "Requires a traditional-market (mandi/commission-agent) price baseline to compare the agreed marketplace price against. No such baseline is captured anywhere in this codebase — only the marketplace-side agreed price is known."
                ),
                ImpactMetric.notAvailable(
                        "Consumer/buyer price improvement",
                        "Same limitation as above: requires a traditional-channel retail price to compare the marketplace price against, which this codebase does not capture."
                ),
                ImpactMetric.notAvailable(
                        "Reduced intermediary dependency",
                        String.format(Locale.ROOT,
                                "Requires a comparable traditional-channel transaction volume/step-count to compare against direct-order volume, which this codebase does not capture. "
                                        + "For reference, this deployment's own MarketOrder rows record %d direct farmer-to-buyer orders totaling %.2f INR in agreed sale value — a raw count, not a reduction percentage.",
                                totalOrders, directFarmerRevenue)
                ),
                ImpactMetric.available(
                        "Logistics efficiency (route optimization)",
                        routesOptimized == 0
                                ? "No routes optimized yet"
                                : String.format(Locale.ROOT, "%d routes optimized, %.1f km saved in total vs. the unoptimized stop order",
                                routesOptimized, totalDistanceSavedKm),
                        "Computed from this deployment's own route_optimization_result rows. The optimizer itself is a nearest-neighbor + 2-opt heuristic (RouteOptimizer, Phase 3) — not a routing-API ETA and not a trained ML model."
                ),
                ImpactMetric.available(
                        "Demand visibility",
                        forecastsGenerated == 0
                                ? "No forecasts generated yet"
                                : String.format(Locale.ROOT, "%d demand forecast runs generated across crop/location combinations", forecastsGenerated),
                        "Computed from this deployment's own demand_prediction rows. The forecasting method is exponential smoothing over historical harvest volumes (StatisticalForecastingProvider, Phase 3) — a statistical technique, not a trained ML model."
                )
        );

        return new SihImpactResponse(metrics, Instant.now());
    }
}
