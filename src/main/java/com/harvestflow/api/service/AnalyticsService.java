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
    private final MarketPriceService marketPriceService;
    private final com.harvestflow.api.service.ai.AiServiceClient aiServiceClient;

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
                farmerPriceVsMandi(),
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
                        aiServiceClient.isEnabled()
                                ? "Computed from this deployment's own route_optimization_result rows. Routes are planned by Google OR-Tools in the AI service (capacity-aware vehicle routing), with the built-in nearest-neighbour + 2-opt heuristic as fallback."
                                : "Computed from this deployment's own route_optimization_result rows. The optimizer is a nearest-neighbour + 2-opt heuristic (RouteOptimizer); enable the AI service for OR-Tools multi-truck routing."
                ),
                ImpactMetric.available(
                        "Demand visibility",
                        forecastsGenerated == 0
                                ? "No forecasts generated yet"
                                : String.format(Locale.ROOT, "%d demand forecast runs generated across crop/location combinations", forecastsGenerated),
                        aiServiceClient.isEnabled()
                                ? "Computed from this deployment's own demand_prediction rows. Forecasts come from the AI service (Prophet / Holt-Winters seasonal models over marketplace orders and Agmarknet mandi data) and are estimates with a range."
                                : "Computed from this deployment's own demand_prediction rows. The method is exponential smoothing (StatisticalForecastingProvider); enable the AI service for seasonal Prophet/Holt-Winters forecasts."
                )
        );

        return new SihImpactResponse(metrics, Instant.now());
    }

    /**
     * Agreed marketplace price vs. the latest Agmarknet mandi modal price for the same crop.
     * Uses today's mandi price, not the price on each order's date, so it is labelled approximate.
     * Stays NOT_AVAILABLE when there are no orders or no mandi data: never invented.
     */
    private ImpactMetric farmerPriceVsMandi() {
        java.util.Map<String, Double> mandiByCrop = new java.util.HashMap<>();
        double weightedDiff = 0, weight = 0;
        int compared = 0;
        for (var o : marketOrderRepository.findAll()) {
            if (o.getStatus() == OrderStatus.CANCELLED || o.getAgreedPricePerUnit() == null || o.getQuantity() == null) continue;
            String crop = o.getCropName() == null ? "" : o.getCropName().trim();
            if (crop.isEmpty()) continue;
            String key = crop.toLowerCase(Locale.ROOT);
            if (!mandiByCrop.containsKey(key)) {
                Double price = null;
                if (mandiByCrop.size() < 20) { // keep the dashboard fast
                    try {
                        double v = marketPriceService.latest(crop, null, null).path("modalPerKg").asDouble(0);
                        price = v > 0 ? v : null;
                    } catch (RuntimeException e) {
                        price = null;
                    }
                }
                mandiByCrop.put(key, price); // nulls cached too: one lookup per crop
            }
            Double mandi = mandiByCrop.get(key);
            if (mandi == null || mandi <= 0) continue;
            weightedDiff += (o.getAgreedPricePerUnit() - mandi) / mandi * o.getQuantity();
            weight += o.getQuantity();
            compared++;
        }
        if (compared == 0 || weight <= 0) {
            return ImpactMetric.notAvailable(
                    "Farmer earning improvement vs. traditional channel",
                    "Needs marketplace orders and mandi prices. Turn on the AI service and add a data.gov.in key (DATAGOV_API_KEY) or a mandi_prices.csv file.");
        }
        double pct = weightedDiff / weight * 100;
        return ImpactMetric.available(
                "Farmer earning improvement vs. traditional channel",
                String.format(Locale.ROOT, "Farmers got %+.1f%% compared with the mandi modal price, across %d orders (quantity-weighted)", pct, compared),
                "Agreed marketplace price vs. the latest Agmarknet (data.gov.in) mandi modal price for the same crop. Approximate: uses the current mandi price, not the price on each order date, and ignores transport costs.");
    }
}
