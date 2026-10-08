package com.harvestflow.api.dto.demand;

import com.harvestflow.api.domain.entity.DemandPrediction;
import com.harvestflow.api.domain.enums.DemandLevel;
import com.harvestflow.api.domain.enums.TrendDirection;

import java.time.Instant;
import java.time.LocalDate;

public record DemandPredictionResponse(
        String id,
        String cropType,
        String location,
        LocalDate predictionDate,
        Double predictedQuantity,
        Double historicalAverage,
        DemandLevel demandLevel,
        TrendDirection trend,
        Double confidenceScore,
        int dataPointsUsed,
        String generatedByUsername,
        Instant generatedAt,
        String notes,
        /** Always true — a UI-facing reminder that this is a statistical estimate, never a guaranteed figure. */
        boolean estimate,
        String forecastEngine,
        Double predictedLow,
        Double predictedHigh,
        Double predictedPricePerKg,
        String priceTrend,
        String dataSource
) {
    public static DemandPredictionResponse from(DemandPrediction p) {
        return new DemandPredictionResponse(
                p.getId().toString(),
                p.getCropType(),
                p.getLocation(),
                p.getPredictionDate(),
                p.getPredictedQuantity(),
                p.getHistoricalAverage(),
                p.getDemandLevel(),
                p.getTrend(),
                p.getConfidenceScore(),
                p.getDataPointsUsed(),
                p.getGeneratedByUsername(),
                p.getGeneratedAt(),
                p.getNotes(),
                true,
                p.getForecastEngine(),
                p.getPredictedLow(),
                p.getPredictedHigh(),
                p.getPredictedPricePerKg(),
                p.getPriceTrend(),
                p.getDataSource()
        );
    }
}
