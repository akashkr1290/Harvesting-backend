package com.harvestflow.api.service.forecasting;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticalForecastingProviderTest {

    private final StatisticalForecastingProvider provider = new StatisticalForecastingProvider();

    @Test
    void forecastNextPeriod_withEmptySeries_throws() {
        assertThatThrownBy(() -> provider.forecastNextPeriod(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forecastNextPeriod_withRisingSeries_detectsIncreasingTrend() {
        List<ForecastingProvider.MonthlyPoint> series = List.of(
                new ForecastingProvider.MonthlyPoint(2026, 1, 1000),
                new ForecastingProvider.MonthlyPoint(2026, 2, 1200),
                new ForecastingProvider.MonthlyPoint(2026, 3, 1400),
                new ForecastingProvider.MonthlyPoint(2026, 4, 1700)
        );

        ForecastingProvider.Forecast forecast = provider.forecastNextPeriod(series);

        assertThat(forecast.trend()).isEqualTo(ForecastingProvider.Trend.INCREASING);
        assertThat(forecast.predictedQuantity()).isGreaterThan(0);
        assertThat(forecast.confidenceScore()).isBetween(0.3, 0.95);
    }

    @Test
    void forecastNextPeriod_withFlatSeries_detectsStableTrend() {
        List<ForecastingProvider.MonthlyPoint> series = List.of(
                new ForecastingProvider.MonthlyPoint(2026, 1, 1000),
                new ForecastingProvider.MonthlyPoint(2026, 2, 1010),
                new ForecastingProvider.MonthlyPoint(2026, 3, 990),
                new ForecastingProvider.MonthlyPoint(2026, 4, 1005)
        );

        ForecastingProvider.Forecast forecast = provider.forecastNextPeriod(series);

        assertThat(forecast.trend()).isEqualTo(ForecastingProvider.Trend.STABLE);
    }

    @Test
    void forecastNextPeriod_withFallingSeries_detectsDecreasingTrend() {
        List<ForecastingProvider.MonthlyPoint> series = List.of(
                new ForecastingProvider.MonthlyPoint(2026, 1, 1800),
                new ForecastingProvider.MonthlyPoint(2026, 2, 1500),
                new ForecastingProvider.MonthlyPoint(2026, 3, 1200),
                new ForecastingProvider.MonthlyPoint(2026, 4, 900)
        );

        ForecastingProvider.Forecast forecast = provider.forecastNextPeriod(series);

        assertThat(forecast.trend()).isEqualTo(ForecastingProvider.Trend.DECREASING);
    }

    @Test
    void forecastNextPeriod_confidenceNeverExceedsBounds_evenWithLongVolatileSeries() {
        List<ForecastingProvider.MonthlyPoint> series = List.of(
                new ForecastingProvider.MonthlyPoint(2025, 1, 100),
                new ForecastingProvider.MonthlyPoint(2025, 2, 5000),
                new ForecastingProvider.MonthlyPoint(2025, 3, 50),
                new ForecastingProvider.MonthlyPoint(2025, 4, 4000),
                new ForecastingProvider.MonthlyPoint(2025, 5, 200)
        );

        ForecastingProvider.Forecast forecast = provider.forecastNextPeriod(series);

        assertThat(forecast.confidenceScore()).isBetween(0.3, 0.95);
    }
}
