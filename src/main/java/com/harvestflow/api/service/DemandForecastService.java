package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.DemandPrediction;
import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.entity.MarketOrder;
import com.harvestflow.api.domain.enums.OrderStatus;
import com.harvestflow.api.repository.MarketOrderRepository;
import com.harvestflow.api.service.ai.AiServiceClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.harvestflow.api.domain.enums.DemandLevel;
import com.harvestflow.api.domain.enums.TrendDirection;
import com.harvestflow.api.exception.AiServiceUnavailableException;
import com.harvestflow.api.exception.InsufficientDataException;
import com.harvestflow.api.repository.DemandPredictionRepository;
import com.harvestflow.api.repository.HarvestCaseRepository;
import com.harvestflow.api.service.forecasting.ForecastingProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Optional;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Demand forecasting.
 *
 * <p>Demand comes from what buyers actually ordered (marketplace orders for the
 * crop and location, by month). When there are fewer than 3 months of orders,
 * completed harvest volumes are used instead and the result says so, because
 * harvest volume is supply, not demand.
 *
 * <p>With the AI service on ({@code AI_ENABLED=true}) the history goes to
 * ai-service: Prophet / Holt-Winters with a seasonal model, mandi prices and
 * arrivals from Agmarknet, an 80% range and a price forecast. Otherwise, or if
 * the AI service fails, the built-in {@link ForecastingProvider} runs.
 */
@Service
@RequiredArgsConstructor
public class DemandForecastService {

    /** Below this many distinct months of history, a forecast would be little more than a guess. */
    private static final int MIN_DATA_POINTS = 3;

    private final HarvestCaseRepository harvestCaseRepository;
    private final DemandPredictionRepository demandPredictionRepository;
    private final ForecastingProvider forecastingProvider;
    private final MarketOrderRepository marketOrderRepository;
    private final AiServiceClient aiServiceClient;

    private static final Duration AI_TIMEOUT = Duration.ofSeconds(45);

    @Transactional
    public DemandPrediction generatePrediction(String cropType, String location, String username) {
        Map<YearMonth, Double> orderQty = new TreeMap<>();
        Map<YearMonth, double[]> orderPrice = new TreeMap<>(); // [sum, count]
        String loc = location == null ? "" : location.trim().toLowerCase(Locale.ROOT);
        for (MarketOrder o : marketOrderRepository.findByCropNameIgnoreCase(cropType)) {
            if (o.getStatus() == OrderStatus.CANCELLED || o.getCreatedAt() == null) continue;
            String listingLoc = o.getListing() == null || o.getListing().getLocation() == null
                    ? "" : o.getListing().getLocation().toLowerCase(Locale.ROOT);
            if (!loc.isEmpty() && !listingLoc.contains(loc)) continue;
            YearMonth ym = YearMonth.from(o.getCreatedAt().atZone(ZoneOffset.UTC));
            orderQty.merge(ym, o.getQuantity() == null ? 0.0 : o.getQuantity(), Double::sum);
            if (o.getAgreedPricePerUnit() != null) {
                double[] acc = orderPrice.computeIfAbsent(ym, k -> new double[2]);
                acc[0] += o.getAgreedPricePerUnit();
                acc[1] += 1;
            }
        }

        Map<YearMonth, Double> harvestQty = new TreeMap<>();
        for (HarvestCase c : harvestCaseRepository.findCompletedByCropTypeAndLocation(cropType, location)) {
            if (c.getCompletion() != null && c.getCompletion().getActualDate() != null) {
                YearMonth ym = YearMonth.from(c.getCompletion().getActualDate());
                double weight = c.getCompletion().getActualWeight() != null ? c.getCompletion().getActualWeight() : 0.0;
                harvestQty.merge(ym, weight, Double::sum);
            }
        }

        boolean useOrders = orderQty.size() >= MIN_DATA_POINTS || harvestQty.size() < MIN_DATA_POINTS && !orderQty.isEmpty();
        Map<YearMonth, Double> monthlyTotals = useOrders ? orderQty : harvestQty;
        String source = useOrders ? "orders" : "harvest-volumes";

        DemandPrediction prediction = new DemandPrediction();
        prediction.setCropType(cropType);
        prediction.setLocation(location);
        prediction.setPredictionDate(LocalDate.now().plusMonths(1).withDayOfMonth(1));
        prediction.setGeneratedByUsername(username != null ? username : "System");

        if (applyAiForecast(prediction, cropType, location, monthlyTotals, orderPrice, source)) {
            return demandPredictionRepository.save(prediction);
        }

        if (monthlyTotals.size() < MIN_DATA_POINTS) {
            throw new InsufficientDataException(
                    "Need at least " + MIN_DATA_POINTS + " months of history for '" + cropType
                            + "' in '" + location + "'; only " + monthlyTotals.size() + " found.");
        }
        List<ForecastingProvider.MonthlyPoint> series = monthlyTotals.entrySet().stream()
                .map(e -> new ForecastingProvider.MonthlyPoint(e.getKey().getYear(), e.getKey().getMonthValue(), e.getValue()))
                .toList();
        int dataPointsCount = monthlyTotals.size();

        ForecastingProvider.Forecast forecast;
        try {
            forecast = forecastingProvider.forecastNextPeriod(series);
        } catch (RuntimeException ex) {
            throw new AiServiceUnavailableException(
                    "Demand forecasting failed for '%s' in '%s'. Please try again.".formatted(cropType, location), ex);
        }

        prediction.setPredictedQuantity(forecast.predictedQuantity());
        prediction.setHistoricalAverage(forecast.historicalAverage());
        prediction.setTrend(toTrendDirection(forecast.trend()));
        prediction.setDemandLevel(toDemandLevel(forecast.predictedQuantity(), forecast.historicalAverage()));
        prediction.setConfidenceScore(forecast.confidenceScore());
        prediction.setDataPointsUsed(dataPointsCount);
        prediction.setForecastEngine("statistical-local");
        prediction.setDataSource(source);
        prediction.setNotes(("Estimate based on %d month(s) of %s. Not a guaranteed figure.")
                .formatted(dataPointsCount, useOrders ? "marketplace orders" : "harvest volumes (supply, used because there are too few orders)"));

        return demandPredictionRepository.save(prediction);
    }

    /** Fills the prediction from the AI service. Returns false (and changes nothing) if it is off or cannot forecast. */
    private boolean applyAiForecast(DemandPrediction p, String cropType, String location,
                                    Map<YearMonth, Double> qty, Map<YearMonth, double[]> price, String source) {
        if (!aiServiceClient.isEnabled()) return false;
        List<Map<String, Object>> demandHistory = new ArrayList<>();
        qty.forEach((ym, v) -> demandHistory.add(Map.of("month", ym.toString(), "value", v)));
        List<Map<String, Object>> priceHistory = new ArrayList<>();
        price.forEach((ym, acc) -> priceHistory.add(Map.of("month", ym.toString(), "value", acc[0] / acc[1])));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("crop", cropType);
        body.put("location", location);
        body.put("horizonMonths", 3);
        body.put("demandHistory", demandHistory);
        body.put("priceHistory", priceHistory);
        body.put("useMandiHistory", true);

        Optional<JsonNode> res = aiServiceClient.postJson("/forecast/demand", body, AI_TIMEOUT);
        if (res.isEmpty()) return false;
        JsonNode demand = res.get().path("demand");
        if (!demand.isObject() || !demand.path("points").isArray() || demand.path("points").isEmpty()) {
            return false;
        }
        JsonNode first = demand.path("points").get(0);
        double predicted = first.path("value").asDouble();
        double average = demand.path("average").asDouble();
        p.setPredictedQuantity(predicted);
        p.setPredictedLow(first.path("lower").asDouble());
        p.setPredictedHigh(first.path("upper").asDouble());
        p.setHistoricalAverage(average);
        p.setTrend(parseTrend(demand.path("trend").asText()));
        p.setDemandLevel(toDemandLevel(predicted, average));
        p.setConfidenceScore(demand.path("confidence").asDouble());
        p.setDataPointsUsed(demand.path("historyPoints").asInt());
        p.setForecastEngine(demand.path("engine").asText("ai"));
        List<String> sources = new ArrayList<>();
        res.get().path("sources").forEach(x -> sources.add(x.asText()));
        if (sources.isEmpty()) sources.add(source);
        p.setDataSource(String.join(",", sources));

        JsonNode pr = res.get().path("price");
        if (pr.isObject() && pr.path("points").isArray() && !pr.path("points").isEmpty()) {
            p.setPredictedPricePerKg(pr.path("points").get(0).path("value").asDouble());
            p.setPriceTrend(pr.path("trend").asText(null));
        }
        p.setNotes(("Estimate by the AI service (%s) from %s, %d month(s) of history, 80%% range %.0f-%.0f. Not a guaranteed figure.")
                .formatted(p.getForecastEngine(), p.getDataSource(), p.getDataPointsUsed(), p.getPredictedLow(), p.getPredictedHigh()));
        return true;
    }

    private static TrendDirection parseTrend(String t) {
        try {
            return TrendDirection.valueOf(t);
        } catch (Exception e) {
            return TrendDirection.STABLE;
        }
    }

    @Transactional
    public DemandPrediction getLatest(String cropType, String location) {
        return demandPredictionRepository
                .findFirstByCropTypeIgnoreCaseAndLocationIgnoreCaseOrderByGeneratedAtDesc(cropType, location)
                .orElseThrow(() -> new InsufficientDataException(
                        "No forecast has been generated yet for '" + cropType + "' in '" + location + "'." +
                        " Run generatePrediction first."));
    }

    @Transactional(readOnly = true)
    public List<DemandPrediction> getHistory(String cropType, String location) {
        return demandPredictionRepository.findByCropTypeIgnoreCaseAndLocationIgnoreCaseOrderByGeneratedAtDesc(cropType, location);
    }

    @Transactional(readOnly = true)
    public List<DemandPrediction> getAllLatest() {
        return demandPredictionRepository.findAllByOrderByGeneratedAtDesc();
    }

    private DemandLevel toDemandLevel(double predicted, double historicalAverage) {
        if (historicalAverage <= 0) {
            return DemandLevel.MEDIUM;
        }
        double ratio = predicted / historicalAverage;
        if (ratio >= 1.15) {
            return DemandLevel.HIGH;
        }
        if (ratio <= 0.85) {
            return DemandLevel.LOW;
        }
        return DemandLevel.MEDIUM;
    }

    private TrendDirection toTrendDirection(ForecastingProvider.Trend trend) {
        return switch (trend) {
            case INCREASING -> TrendDirection.INCREASING;
            case DECREASING -> TrendDirection.DECREASING;
            case STABLE -> TrendDirection.STABLE;
        };
    }
}
