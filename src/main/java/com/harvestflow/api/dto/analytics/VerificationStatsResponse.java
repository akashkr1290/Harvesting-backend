package com.harvestflow.api.dto.analytics;

import com.harvestflow.api.domain.enums.ImageDecision;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;

import java.util.Map;

/** Real figures, computed directly from {@code produce_image} — Phase 4's own data, no mock/demo values here. */
public record VerificationStatsResponse(
        long totalImages,
        Map<ImageVerificationStatus, Long> countsByStatus,
        Map<ImageDecision, Long> countsByDecision,
        long pendingReviewCount,
        Double acceptanceRate // ACCEPT / totalImages, null when totalImages == 0
) {
}
