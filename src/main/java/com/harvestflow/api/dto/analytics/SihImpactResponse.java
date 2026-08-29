package com.harvestflow.api.dto.analytics;

import java.time.Instant;
import java.util.List;

/** See ImpactMetric for what AVAILABLE/DEMO_DATA/NOT_AVAILABLE mean on each line. */
public record SihImpactResponse(List<ImpactMetric> metrics, Instant generatedAt) {
}
