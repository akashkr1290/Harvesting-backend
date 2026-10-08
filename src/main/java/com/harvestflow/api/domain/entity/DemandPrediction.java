package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.DemandLevel;
import com.harvestflow.api.domain.enums.TrendDirection;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single AI/statistical demand forecast run for one crop + location.
 * Runs are appended, never overwritten, so both "latest forecast" and
 * "forecast history/trend over time" read off the same table.
 */
@Entity
@Table(name = "demand_prediction")
@Getter
@Setter
@NoArgsConstructor
public class DemandPrediction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String cropType;

    @Column(nullable = false)
    private String location;

    /** First day of the period this forecast is for (currently: next calendar month). */
    @Column(nullable = false)
    private LocalDate predictionDate;

    @Column(nullable = false)
    private Double predictedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DemandLevel demandLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrendDirection trend;

    /** 0.0–1.0 heuristic confidence, driven by sample size and volatility. Always an estimate. */
    private Double confidenceScore;

    @Column(nullable = false)
    private Integer dataPointsUsed;

    /** Mean of the historical periods this forecast was computed from — lets the UI show "was X, predicted Y". */
    private Double historicalAverage;

    @Column(nullable = false)
    private String generatedByUsername;

    @Column(nullable = false)
    private Instant generatedAt = Instant.now();

    @Column(length = 1000)
    private String notes;

    /** Which model produced the forecast, e.g. "prophet" or "holt-damped-trend" (AI service) or "statistical-local". */
    @Column(length = 60)
    private String forecastEngine;

    /** Low and high end of the 80% range for predictedQuantity. */
    private Double predictedLow;
    private Double predictedHigh;

    /** Expected price next month in Rs/kg (from orders and mandi prices), when available. */
    private Double predictedPricePerKg;

    @Column(length = 20)
    private String priceTrend;

    /** Where the history came from: "orders", "harvest-volumes", "mandi-prices", "mandi-arrivals". */
    @Column(length = 120)
    private String dataSource;
}
