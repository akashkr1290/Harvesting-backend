package com.harvestflow.api.service.forecasting;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default {@link ForecastingProvider}: exponential smoothing for the
 * point forecast, a simple first-half-vs-second-half average comparison
 * for trend, and a heuristic confidence score from sample size + volatility.
 *
 * This is intentionally a statistical model, not a trained ML model or an
 * external AI API call — per the Phase 3 brief ("do not over-engineer the
 * MVP", "a practical lightweight model/service is appropriate"). A real
 * HarvestFlow deployment has, at best, a few dozen monthly data points per
 * crop/location; a neural forecaster would be both unjustified and
 * unmaintainable at that scale, and would need infrastructure (training
 * pipeline, model storage, inference service) this project has nowhere to
 * run. Swapping this implementation for a hosted model later only means
 * writing a new {@link ForecastingProvider} and wiring it in — nothing
 * else changes.
 */
@Component
public class StatisticalForecastingProvider implements ForecastingProvider {

    /** Smoothing weight for the most recent period. 0.5 balances responsiveness against noise for short series. */
    private static final double SMOOTHING_ALPHA = 0.5;

    @Override
    public Forecast forecastNextPeriod(List<MonthlyPoint> series) {
        if (series.isEmpty()) {
            throw new IllegalArgumentException("Cannot forecast from an empty series");
        }

        double[] values = series.stream().mapToDouble(MonthlyPoint::totalQuantity).toArray();

        double smoothed = values[0];
        for (int i = 1; i < values.length; i++) {
            smoothed = SMOOTHING_ALPHA * values[i] + (1 - SMOOTHING_ALPHA) * smoothed;
        }

        double average = mean(values);
        Trend trend = computeTrend(values, average);
        double confidence = computeConfidence(values, average);

        return new Forecast(round(smoothed), round(average), trend, confidence);
    }

    private Trend computeTrend(double[] values, double average) {
        if (values.length < 2) {
            return Trend.STABLE;
        }
        int mid = values.length / 2;
        double firstHalfAvg = mean(java.util.Arrays.copyOfRange(values, 0, Math.max(mid, 1)));
        double secondHalfAvg = mean(java.util.Arrays.copyOfRange(values, mid, values.length));

        if (average <= 0) {
            return Trend.STABLE;
        }
        double changeRatio = (secondHalfAvg - firstHalfAvg) / average;
        if (changeRatio > 0.10) {
            return Trend.INCREASING;
        }
        if (changeRatio < -0.10) {
            return Trend.DECREASING;
        }
        return Trend.STABLE;
    }

    /** More data points and lower relative volatility both raise confidence. Always clamped to a plausible, humble range. */
    private double computeConfidence(double[] values, double average) {
        double sampleSizeFactor = Math.min(1.0, values.length / 12.0);

        double variance = 0;
        for (double v : values) {
            variance += Math.pow(v - average, 2);
        }
        variance /= values.length;
        double stdDev = Math.sqrt(variance);
        double coefficientOfVariation = average > 0 ? stdDev / average : 1.0;
        double volatilityFactor = Math.max(0.0, 1.0 - Math.min(1.0, coefficientOfVariation));

        double raw = 0.5 * sampleSizeFactor + 0.5 * volatilityFactor;
        return round(Math.max(0.3, Math.min(0.95, raw)));
    }

    private double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return values.length == 0 ? 0 : sum / values.length;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
