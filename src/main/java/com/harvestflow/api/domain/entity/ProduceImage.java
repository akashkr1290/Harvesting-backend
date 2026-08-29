package com.harvestflow.api.domain.entity;

import com.harvestflow.api.domain.enums.ImageDecision;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A crop/produce photograph uploaded for a {@link HarvestCase}, plus the
 * outcome of running it through {@link com.harvestflow.api.service.image.ImageAuthenticityService}
 * (Phase 4, Parts A &amp; B).
 *
 * <p><b>Phase 5 integration note:</b> the marketplace's ProduceListing entity
 * does not exist in this codebase yet (see PROJECT_COMPLETE_DETAILS.md —
 * Phases 1-3 are separate, unmerged branches). Rather than invent that
 * entity here, this table carries two independent, both-nullable link
 * columns:
 * <ul>
 *   <li>{@code caseId} — the existing {@link HarvestCase} this photo documents
 *       (used today; every image currently uploaded through Phase 4's own
 *       screens sets this).</li>
 *   <li>{@code listingId} — an opaque UUID reserved for the future
 *       marketplace {@code ProduceListing.id}. Phase 5 can populate this on
 *       existing rows (a case's images become a listing's images) or start
 *       setting it directly on new uploads once that entity exists, without
 *       any schema change here.</li>
 * </ul>
 * See {@code docs/phase4-integration-notes.md} for the exact contract.
 */
@Entity
@Table(name = "produce_image")
@Getter
@Setter
@NoArgsConstructor
public class ProduceImage {

    @Id
    @GeneratedValue
    private UUID id;

    /** The harvest case this photo documents. Nullable only to tolerate future listing-only uploads (see class javadoc). */
    private UUID caseId;

    /** Reserved for Phase 5's marketplace ProduceListing.id. Not used by any Phase 4 code path yet. */
    private UUID listingId;

    @Column(nullable = false)
    private String uploadedByUsername;

    /** Key returned by {@link com.harvestflow.api.service.storage.FileStorageService}, e.g. "produce-images/&lt;uuid&gt;.jpg". */
    @Column(nullable = false)
    private String storageKey;

    private String originalFilename;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long fileSizeBytes;

    private Integer pixelWidth;

    private Integer pixelHeight;

    /** SHA-256 of the file bytes, hex-encoded. Used for exact-duplicate/reuse detection across listings. */
    @Column(length = 64)
    private String checksumSha256;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageVerificationStatus verificationStatus = ImageVerificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImageDecision decision = ImageDecision.MANUAL_REVIEW;

    /** 0.0-1.0 confidence the verifier attaches to its own verdict; null when not applicable (e.g. VERIFICATION_FAILED). */
    private Double confidenceScore;

    /** Short, human-readable reasons the verifier flagged (or cleared) the image. Never a raw ML score dump. */
    @Column(columnDefinition = "TEXT")
    private String verificationReasons;

    /** Which checker produced this result — "heuristic-v1", or a provider name once Phase 5 wires a real vendor. */
    private String verifierId;

    private Instant verifiedAt;

    @Column(nullable = false)
    private int verificationAttempts = 0;

    // ---- Admin manual review (SOW: "Manual Review if supported") ----
    private String adminReviewedByUsername;

    @Column(length = 500)
    private String adminReviewNote;

    private Instant adminReviewedAt;
}
