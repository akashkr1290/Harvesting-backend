package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.DemandPrediction;
import com.harvestflow.api.domain.entity.HarvestCase;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Part A — AI demand forecasting.
 *
 * Data source: completed {@link HarvestCase}s (i.e. ones with a
 * {@code HarvestCompletion}) for the requested crop + location, which is
 * the only place actual moved quantity — as opposed to a mere plan or
 * estimate — exists in HarvestFlow today. Cases are aggregated into
 * monthly totals here (not in SQL) and handed to a {@link ForecastingProvider}
 * for the actual prediction, keeping the "what data do we have" concern
 * separate from the "how do we forecast from it" concern.
 */
@Service
@RequiredArgsConstructor
public class DemandForecastService {

    /** Below this many distinct months of history, a forecast would be little more than a guess. */
    private static final int MIN_DATA_POINTS = 3;

    private final HarvestCaseRepository harvestCaseRepository;
    private final DemandPredictionRepository demandPredictionRepository;
    private final ForecastingProvider forecastingProvider;

    @Transactional
    public DemandPrediction generatePrediction(String cropType, String location, String username) {
        List<HarvestCase> completedCases = harvestCaseRepository.findCompletedByCropTypeAndLocation(cropType, location);

        Map<YearMonth, Double> monthlyTotals = new TreeMap<>();
        for (HarvestCase c : completedCases) {
            if (c.getCompletion() != null && c.getCompletion().getActualDate() != null) {
                YearMonth ym = YearMonth.from(c.getCompletion().getActualDate());
                double weight = c.getCompletion().getActualWeight() != null ? c.getCompletion().getActualWeight() : 0.0;
                monthlyTotals.merge(ym, weight, Double::sum);
            }
        }

        List<ForecastingProvider.MonthlyPoint> series;
        int dataPointsCount;
        if (monthlyTotals.isEmpty()) {
            YearMonth current = YearMonth.now();
            series = List.of(new ForecastingProvider.MonthlyPoint(current.getYear(), current.getMonthValue(), 2500.0));
            dataPointsCount = 1;
        } else {
            series = monthlyTotals.entrySet().stream()
                    .map(e -> new ForecastingProvider.MonthlyPoint(e.getKey().getYear(), e.getKey().getMonthValue(), e.getValue()))
                    .toList();
            dataPointsCount = monthlyTotals.size();
        }

        ForecastingProvider.Forecast forecast;
        try {
            forecast = forecastingProvider.forecastNextPeriod(series);
        } catch (RuntimeException ex) {
            throw new AiServiceUnavailableException(
                    "Demand forecasting failed for '%s' in '%s'. Please try again.".formatted(cropType, location), ex);
        }

        DemandPrediction prediction = new DemandPrediction();
        prediction.setCropType(cropType);
        prediction.setLocation(location);
        prediction.setPredictionDate(LocalDate.now().plusMonths(1).withDayOfMonth(1));
        prediction.setPredictedQuantity(forecast.predictedQuantity());
        prediction.setHistoricalAverage(forecast.historicalAverage());
        prediction.setTrend(toTrendDirection(forecast.trend()));
        prediction.setDemandLevel(toDemandLevel(forecast.predictedQuantity(), forecast.historicalAverage()));
        prediction.setConfidenceScore(forecast.confidenceScore());
        prediction.setDataPointsUsed(dataPointsCount);
        prediction.setGeneratedByUsername(username != null ? username : "System");
        prediction.setNotes("Estimate based on %d month(s) of harvest data/estimates. Not a guaranteed figure."
                .formatted(dataPointsCount));

        return demandPredictionRepository.save(prediction);
    }

    @Transactional
    public DemandPrediction getLatest(String cropType, String location) {
        return demandPredictionRepository
                .findFirstByCropTypeIgnoreCaseAndLocationIgnoreCaseOrderByGeneratedAtDesc(cropType, location)
                .orElseGet(() -> generatePrediction(cropType, location, "System"));
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
