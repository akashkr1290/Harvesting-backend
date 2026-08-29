package com.harvestflow.api.service.forecasting;

import java.util.List;

/**
 * Abstraction over "the thing that turns a historical series into a
 * forecast". {@code DemandForecastService} depends only on this
 * interface, never on a concrete algorithm — so a future phase can swap
 * in a real ML model or an external AI provider (configured via
 * environment/credentials, never hardcoded) without touching the
 * service, controller, or persistence layer.
 *
 * The only implementation shipped in Phase 3 is
 * {@link StatisticalForecastingProvider}: a lightweight, dependency-free
 * exponential-smoothing + linear-trend model. That's a deliberate MVP
 * choice — see its Javadoc.
 */
public interface ForecastingProvider {

    record MonthlyPoint(int year, int month, double totalQuantity) {
    }

    record Forecast(
            double predictedQuantity,
            double historicalAverage,
            Trend trend,
            double confidenceScore
    ) {
    }

    enum Trend {
        INCREASING, STABLE, DECREASING
    }

    /**
     * @param series monthly totals, oldest first, with at least one point.
     * @throws IllegalArgumentException if the series is empty.
     */
    Forecast forecastNextPeriod(List<MonthlyPoint> series);
}
