package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.ProduceImage;
import com.harvestflow.api.domain.entity.ProduceListing;
import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.ImageDecision;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.dto.image.ProduceImageResponse;
import com.harvestflow.api.exception.ForbiddenRoleException;
import com.harvestflow.api.exception.InvalidUploadException;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.ProduceImageRepository;
import com.harvestflow.api.repository.ProduceListingRepository;
import com.harvestflow.api.service.image.AuthenticityResult;
import com.harvestflow.api.service.image.ImageAuthenticityService;
import com.harvestflow.api.service.notification.NotificationService;
import com.harvestflow.api.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the Phase 4 "VERIFICATION FLOW":
 * upload → file validation → storage → AI verification → risk assessment
 * → decision (accept / manual review / reject).
 *
 * <p>Deliberately the only class that touches both {@link FileStorageService}
 * and {@link ImageAuthenticityService} — controllers depend on this, not on
 * either of those directly, so the flow (and its ordering/error handling)
 * lives in exactly one place.
 */
@Service
@RequiredArgsConstructor
public class ProduceImageService {

    private static final Logger log = LoggerFactory.getLogger(ProduceImageService.class);

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final String SUBFOLDER = "produce-images";

    private final FileStorageService fileStorageService;
    private final ProduceImageRepository produceImageRepository;
    private final ImageAuthenticityService authenticityService;
    private final NotificationService notificationService;
    private final ProduceListingRepository produceListingRepository;

    /**
     * Full upload → verify pipeline for one image. Runs synchronously —
     * heuristic analysis is cheap enough (single-file, in-process) that a
     * background queue isn't warranted yet; if a real external vendor is
     * wired in later and adds meaningful latency, this is the one method
     * that would move to async/polling, with PENDING as the interim status
     * clients already know how to render.
     */
    @Transactional
    public ProduceImageResponse upload(MultipartFile file, UUID caseId, boolean markPrimary, String uploadedByUsername) {
        return upload(file, caseId, null, markPrimary, uploadedByUsername);
    }

    /**
     * Phase 5: overload accepting {@code listingId} alongside (or instead of)
     * {@code caseId}, per docs/phase4-integration-notes.md §7.2 ("(b) Upload
     * directly against a listing ... add listingId as an accepted parameter
     * ... thread it through ProduceImageService.upload next to caseId — both
     * already exist as entity/DTO fields; only the controller/service
     * signatures need the extra parameter"). Both may be null, either may be
     * set, or both — the row just records whichever context the upload came
     * from; nothing downstream requires exactly one to be present.
     */
    @Transactional
    public ProduceImageResponse upload(MultipartFile file, UUID caseId, UUID listingId, boolean markPrimary,
                                        String uploadedByUsername) {
        validateBasics(file);
        if (listingId != null) {
            requireListingUploadOwner(listingId, uploadedByUsername);
        }

        var stored = fileStorageService.store(file, SUBFOLDER, ALLOWED_IMAGE_TYPES, MAX_SIZE_BYTES);

        ProduceImage image = new ProduceImage();
        image.setCaseId(caseId);
        image.setListingId(listingId);
        image.setUploadedByUsername(uploadedByUsername);
        image.setStorageKey(stored.key());
        image.setOriginalFilename(stored.originalFilename());
        image.setContentType(stored.contentType());
        image.setFileSizeBytes(stored.sizeBytes());
        image.setPrimary(markPrimary);
        image.setUploadedAt(Instant.now());

        Path storedPath = fileStorageService.resolvedPath(stored.key());
        populateDimensionsAndChecksum(image, storedPath);

        if (markPrimary && caseId != null) {
            clearExistingPrimary(caseId);
        }
        if (markPrimary && listingId != null) {
            clearExistingPrimaryForListing(listingId);
        }

        runVerification(image, storedPath);
        applyDuplicateCheck(image);

        ProduceImage saved = produceImageRepository.save(image);
        refreshListingVerificationStatus(listingId);
        log.info("Produce image {} uploaded by {} for case {} / listing {}: status={} decision={}",
                saved.getId(), uploadedByUsername, caseId, listingId, saved.getVerificationStatus(), saved.getDecision());
        notifyOnNonAcceptDecision(saved, uploadedByUsername);
        return ProduceImageResponse.from(saved);
    }

    /** Re-runs verification on an already-stored image — the "re-upload"/retry path when a check FAILED or was SUSPICIOUS. */
    @Transactional
    public ProduceImageResponse reverify(UUID imageId, User requester) {
        ProduceImage image = produceImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        requireImageAccess(image, requester);
        Path storedPath = fileStorageService.resolvedPath(image.getStorageKey());
        runVerification(image, storedPath);
        applyDuplicateCheck(image);
        ProduceImage saved = produceImageRepository.save(image);
        refreshListingVerificationStatus(image.getListingId());
        return ProduceImageResponse.from(saved);
    }

    public ProduceImageResponse getById(UUID imageId) {
        ProduceImage image = produceImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        return ProduceImageResponse.from(image);
    }

    public List<ProduceImageResponse> getForCase(UUID caseId) {
        return produceImageRepository.findByCaseIdOrderByUploadedAtAsc(caseId).stream()
                .map(ProduceImageResponse::from)
                .toList();
    }

    /** Phase 5: listing-scoped read, mirroring {@link #getForCase}, per the integration contract §7.4. */
    public List<ProduceImageResponse> getForListing(UUID listingId, User requester) {
        List<ProduceImage> images = produceImageRepository.findByListingIdOrderByUploadedAtAsc(listingId);

        boolean owner = requester.getRole() == UserRole.FARMER || requester.getRole() == UserRole.FPO;
        if (owner) {
            ProduceListing listing = produceListingRepository.findById(listingId)
                    .orElseThrow(() -> new ResourceNotFoundException("Listing not found: " + listingId));
            if (!listing.getSeller().getId().equals(requester.getId())) {
                throw new ForbiddenRoleException("You do not own this listing.");
            }
        } else {
            // Buyers and other authenticated users only see photos that the
            // verification pipeline explicitly accepted.
            images = images.stream()
                    .filter(img -> img.getDecision() == ImageDecision.ACCEPT)
                    .toList();
        }

        return images.stream().map(ProduceImageResponse::from).toList();
    }

    /**
     * Phase 5: whether a listing is currently safe to show to buyers as
     * "photo-verified" — used by {@code MarketplaceService.publish()} to
     * enforce Part 9 of the master spec ("never claim 100% authenticity",
     * "if verification service fails, do not silently mark the image as
     * verified/authentic"). A listing with no images at all is treated as
     * not blocked here — whether a photo is mandatory before publish is a
     * product decision left to {@code MarketplaceService}, not this class.
     */
    public boolean hasBlockingVerification(UUID listingId) {
        return produceImageRepository.findByListingIdOrderByUploadedAtAsc(listingId).stream()
                .anyMatch(img -> img.getDecision() == ImageDecision.REJECT);
    }

    /** A listing can be published only after at least one photo is explicitly accepted. */
    public boolean hasPublishableVerification(UUID listingId) {
        List<ProduceImage> images = produceImageRepository.findByListingIdOrderByUploadedAtAsc(listingId);
        return !images.isEmpty()
                && images.stream().anyMatch(img -> img.getDecision() == ImageDecision.ACCEPT)
                && images.stream().noneMatch(img -> img.getDecision() == ImageDecision.REJECT);
    }

    @Transactional
    public void delete(UUID imageId, User requester) {
        ProduceImage image = produceImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        requireImageAccess(image, requester);
        UUID listingId = image.getListingId();
        produceImageRepository.delete(image);
        refreshListingVerificationStatus(listingId);
    }

    /**
     * Phase 5: a FARMER/FPO caller may only reverify/delete an image
     * attached to their own listing — matching Part 8 of the master spec
     * ("Farmer cannot alter another farmer's listing"). Internal roles
     * (ADMIN, PLOT_SELECTION, SUPERVISOR) keep their existing unrestricted
     * access to case-attached images, unchanged from Phase 4 — this app has
     * no per-case ACL for those roles and none is introduced here.
     */
    private void requireListingUploadOwner(UUID listingId, String username) {
        ProduceListing listing = produceListingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found: " + listingId));
        if (!listing.getSeller().getUsername().equals(username)) {
            throw new ForbiddenRoleException("You do not own the listing this photo is being uploaded for.");
        }
    }

    private void requireImageAccess(ProduceImage image, User requester) {
        if (requester.getRole() != UserRole.FARMER && requester.getRole() != UserRole.FPO) {
            return;
        }
        if (image.getListingId() == null) {
            throw new ForbiddenRoleException("You do not have access to this image.");
        }
        ProduceListing listing = produceListingRepository.findById(image.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found: " + image.getListingId()));
        if (!listing.getSeller().getId().equals(requester.getId())) {
            throw new ForbiddenRoleException("You do not own the listing this photo belongs to.");
        }
    }

    /** Admin verification queue (SOW verification flow: "Manual Review if supported") — everything awaiting a human decision. */
    public List<ProduceImageResponse> getReviewQueue() {
        return produceImageRepository.findByDecisionOrderByUploadedAtAsc(ImageDecision.MANUAL_REVIEW).stream()
                .map(ProduceImageResponse::from)
                .toList();
    }

    /**
     * An admin's manual accept/reject on an image the automated checker
     * routed to MANUAL_REVIEW (or on any image, for a second opinion).
     * Does not touch {@code verificationStatus}/{@code verifierId} — the
     * automated verdict is preserved for the record; only {@code decision}
     * is overridden, so "why" (automated reason) and "what happened"
     * (final decision) stay separately visible.
     */
    @Transactional
    public ProduceImageResponse adminDecide(UUID imageId, ImageDecision newDecision, String adminUsername, String note) {
        if (newDecision == null) {
            throw new IllegalArgumentException("A decision (ACCEPT or REJECT) is required.");
        }
        ProduceImage image = produceImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        image.setDecision(newDecision);
        image.setAdminReviewedByUsername(adminUsername);
        image.setAdminReviewNote(note);
        image.setAdminReviewedAt(Instant.now());
        ProduceImage saved = produceImageRepository.save(image);
        refreshListingVerificationStatus(saved.getListingId());

        notificationService.notify(
                saved.getUploadedByUsername(),
                NotificationCategory.IMAGE_VERIFICATION,
                "Photo review completed",
                "An admin reviewed your uploaded photo and set its status to " + newDecision + "."
                        + (note != null && !note.isBlank() ? " Note: " + note : ""),
                "PRODUCE_IMAGE",
                saved.getId()
        );
        return ProduceImageResponse.from(saved);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void notifyOnNonAcceptDecision(ProduceImage image, String uploadedByUsername) {
        if (image.getDecision() == ImageDecision.ACCEPT) {
            return;
        }
        String verb = image.getDecision() == ImageDecision.REJECT ? "was rejected" : "needs manual review";
        notificationService.notify(
                uploadedByUsername,
                NotificationCategory.IMAGE_VERIFICATION,
                "Photo " + (image.getDecision() == ImageDecision.REJECT ? "rejected" : "flagged for review"),
                "Your uploaded photo " + verb + " (" + image.getVerificationStatus() + ").",
                "PRODUCE_IMAGE",
                image.getId()
        );
    }

    private void validateBasics(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Uploaded file is empty.");
        }
        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext.toLowerCase())) {
            throw new InvalidUploadException("Unsupported file extension. Allowed: " + ALLOWED_EXTENSIONS);
        }
        // Content-type + size are re-checked by FileStorageService itself (single source of truth for those rules);
        // this pass adds the extension check the shared storage service doesn't know about.
    }

    private String extensionOf(String filename) {
        if (filename == null) return null;
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? null : filename.substring(dot + 1);
    }

    private void populateDimensionsAndChecksum(ProduceImage image, Path storedPath) {
        try {
            BufferedImage decoded = ImageIO.read(storedPath.toFile());
            if (decoded == null) {
                // Stored successfully as bytes but isn't a real image — let verification below
                // record this as VERIFICATION_FAILED rather than failing the upload outright,
                // so the farmer gets a clear "please re-upload" result instead of a raw error.
                log.warn("Stored file {} could not be decoded as an image.", storedPath);
            } else {
                image.setPixelWidth(decoded.getWidth());
                image.setPixelHeight(decoded.getHeight());
            }
            image.setChecksumSha256(sha256Hex(storedPath));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to inspect uploaded image.", e);
        }
    }

    private String sha256Hex(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(java.nio.file.Files.readAllBytes(path));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM.", e);
        }
    }

    private void runVerification(ProduceImage image, Path storedPath) {
        AuthenticityResult result = authenticityService.analyze(storedPath, image.getContentType());
        image.setVerificationStatus(result.status());
        image.setDecision(result.status().decision());
        image.setConfidenceScore(result.confidence());
        image.setVerificationReasons(String.join("|", result.reasons()));
        image.setVerifierId(result.verifierId());
        image.setVerifiedAt(Instant.now());
        image.setVerificationAttempts(image.getVerificationAttempts() + 1);
    }

    /**
     * Exact-hash reuse detection (SOW: "potentially reused image where technically feasible").
     * Runs after the primary verdict and can only escalate risk, never soften a verdict the
     * checker already reached on the image's own content.
     */
    private void applyDuplicateCheck(ProduceImage image) {
        if (image.getChecksumSha256() == null || image.getDecision() == ImageDecision.REJECT) {
            return;
        }
        boolean reusedElsewhere = produceImageRepository.findByChecksumSha256(image.getChecksumSha256()).stream()
                .anyMatch(other -> !other.getId().equals(image.getId())
                        && (image.getCaseId() == null || !image.getCaseId().equals(other.getCaseId())));
        if (reusedElsewhere) {
            image.setVerificationStatus(ImageVerificationStatus.SUSPICIOUS);
            image.setDecision(ImageDecision.MANUAL_REVIEW);
            String existingReasons = image.getVerificationReasons();
            String reuseNote = "Identical image content was already uploaded for a different case/listing.";
            image.setVerificationReasons(existingReasons == null || existingReasons.isBlank()
                    ? reuseNote
                    : existingReasons + "|" + reuseNote);
        }
    }

    private void refreshListingVerificationStatus(UUID listingId) {
        if (listingId == null) return;
        ProduceListing listing = produceListingRepository.findById(listingId).orElse(null);
        if (listing == null) return;

        List<ProduceImage> images = produceImageRepository.findByListingIdOrderByUploadedAtAsc(listingId);
        String status;
        if (images.isEmpty()) {
            status = "NOT_SUBMITTED";
        } else if (images.stream().anyMatch(i -> i.getDecision() == ImageDecision.REJECT)) {
            status = "REJECTED";
        } else if (images.stream().anyMatch(i -> i.getDecision() == ImageDecision.MANUAL_REVIEW)) {
            status = "MANUAL_REVIEW";
        } else if (images.stream().anyMatch(i -> i.getDecision() == ImageDecision.ACCEPT)) {
            status = "AUTHENTICITY_LIKELY";
        } else {
            status = "PENDING";
        }
        listing.setVerificationStatus(status);
        produceListingRepository.save(listing);
    }

    private void clearExistingPrimary(UUID caseId) {
        produceImageRepository.findByCaseIdAndPrimaryTrue(caseId)
                .ifPresent(existing -> {
                    existing.setPrimary(false);
                    produceImageRepository.save(existing);
                });
    }

    private void clearExistingPrimaryForListing(UUID listingId) {
        produceImageRepository.findByListingIdAndPrimaryTrue(listingId)
                .ifPresent(existing -> {
                    existing.setPrimary(false);
                    produceImageRepository.save(existing);
                });
    }
}
