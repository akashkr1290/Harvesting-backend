package com.harvestflow.api.dto.image;

import com.harvestflow.api.domain.entity.ProduceImage;
import com.harvestflow.api.domain.enums.ImageDecision;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProduceImageResponse(
        UUID id,
        UUID caseId,
        UUID listingId,
        String url,
        String originalFilename,
        String contentType,
        long sizeBytes,
        boolean isPrimary,
        Instant uploadedAt,
        ImageVerificationStatus verificationStatus,
        ImageDecision decision,
        Double confidenceScore,
        List<String> verificationReasons,
        String verifierId,
        Instant verifiedAt,
        String adminReviewedByUsername,
        String adminReviewNote,
        Instant adminReviewedAt
) {
    public static ProduceImageResponse from(ProduceImage image) {
        List<String> reasons = image.getVerificationReasons() == null || image.getVerificationReasons().isBlank()
                ? List.of()
                : List.of(image.getVerificationReasons().split("\\|"));
        return new ProduceImageResponse(
                image.getId(),
                image.getCaseId(),
                image.getListingId(),
                "/api/files/" + image.getStorageKey(),
                image.getOriginalFilename(),
                image.getContentType(),
                image.getFileSizeBytes(),
                image.isPrimary(),
                image.getUploadedAt(),
                image.getVerificationStatus(),
                image.getDecision(),
                image.getConfidenceScore(),
                reasons,
                image.getVerifierId(),
                image.getVerifiedAt(),
                image.getAdminReviewedByUsername(),
                image.getAdminReviewNote(),
                image.getAdminReviewedAt()
        );
    }
}
