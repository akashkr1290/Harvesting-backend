package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.ProduceImage;
import com.harvestflow.api.domain.enums.ImageDecision;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProduceImageRepository extends JpaRepository<ProduceImage, UUID> {

    List<ProduceImage> findByCaseIdOrderByUploadedAtAsc(UUID caseId);

    Optional<ProduceImage> findByCaseIdAndPrimaryTrue(UUID caseId);

    long countByCaseId(UUID caseId);

    /**
     * Phase 5: listing-scoped equivalent of {@link #findByCaseIdOrderByUploadedAtAsc},
     * added once {@code produce_listing} existed (see V6__phase5_integration.sql and
     * docs/phase4-integration-notes.md §7.4 — this was the exact method the notes
     * said to add "the same way findByCaseId... exists").
     */
    List<ProduceImage> findByListingIdOrderByUploadedAtAsc(UUID listingId);

    Optional<ProduceImage> findByListingIdAndPrimaryTrue(UUID listingId);

    /** Exact-duplicate / reused-photo detection (SOW: "potentially reused image where technically feasible"). */
    List<ProduceImage> findByChecksumSha256(String checksumSha256);

    /** Feeds the admin/SIH "image verification statistics" analytics card. */
    long countByDecision(ImageDecision decision);

    long countByVerificationStatus(ImageVerificationStatus status);

    /** Admin verification queue — everything still awaiting a human decision, oldest first. */
    List<ProduceImage> findByDecisionOrderByUploadedAtAsc(ImageDecision decision);
}
