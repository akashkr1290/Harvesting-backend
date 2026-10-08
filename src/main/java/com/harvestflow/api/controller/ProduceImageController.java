package com.harvestflow.api.controller;

import com.harvestflow.api.dto.image.AdminReviewDecisionRequest;
import com.harvestflow.api.dto.image.ProduceImageResponse;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.ProduceImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Phase 4, Part A/B/verification-flow: upload a produce/crop photo, get its
 * AI authenticity verification result, and retry ("re-upload") when a check
 * comes back SUSPICIOUS or VERIFICATION_FAILED.
 *
 * <p>Authorization mirrors the existing {@code UploadController} pattern:
 * upload is restricted to the on-the-ground roles who can plausibly be
 * capturing a photo of the produce today (there is no FARMER/FPO/BUYER
 * role in this codebase — see class-level note in {@code ProduceImage}).
 * Reads are open to any authenticated user, matching how {@code FileController}
 * already treats stored files in this app (no per-case ACL exists beyond
 * role-gated actions).
 */
@RestController
@RequestMapping("/api/produce-images")
@RequiredArgsConstructor
public class ProduceImageController {

    private final ProduceImageService produceImageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('PLOT_SELECTION', 'SUPERVISOR', 'ADMIN', 'FARMER', 'FPO')")
    public ProduceImageResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "caseId", required = false) UUID caseId,
            @RequestParam(value = "listingId", required = false) UUID listingId,
            @RequestParam(value = "isPrimary", required = false, defaultValue = "false") boolean isPrimary,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        return produceImageService.upload(file, caseId, listingId, isPrimary, principal.getUsername());
    }

    @GetMapping("/{id}")
    public ProduceImageResponse getById(@PathVariable UUID id) {
        return produceImageService.getById(id);
    }

    @GetMapping("/case/{caseId}")
    @PreAuthorize(com.harvestflow.api.security.RoleExpressions.INTERNAL_STAFF)
    public List<ProduceImageResponse> getForCase(@PathVariable UUID caseId) {
        return produceImageService.getForCase(caseId);
    }

    /** Phase 5: listing-scoped equivalent of {@link #getForCase}, per the Phase 4 integration contract §7.4. */
    @GetMapping("/listing/{listingId}")
    public List<ProduceImageResponse> getForListing(
            @PathVariable UUID listingId,
            @AuthenticationPrincipal SecurityUser principal) {
        return produceImageService.getForListing(listingId, principal.getUser());
    }

    /** Admin verification queue — SOW verification flow's "Manual Review" step, made actionable. */
    @GetMapping("/review-queue")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProduceImageResponse> getReviewQueue() {
        return produceImageService.getReviewQueue();
    }

    /** Admin overrides the automated decision (accept/reject) on an image, with an optional note. */
    @PostMapping("/{id}/admin-decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ProduceImageResponse adminDecide(@PathVariable UUID id, @Valid @RequestBody AdminReviewDecisionRequest request,
                                             @AuthenticationPrincipal SecurityUser principal) {
        return produceImageService.adminDecide(id, request.decision(), principal.getUsername(), request.note());
    }

    /** Re-runs verification without re-uploading bytes — used after fixing a transient failure, or for admin re-checks. */
    @PostMapping("/{id}/reverify")
    @PreAuthorize("hasAnyRole('PLOT_SELECTION', 'SUPERVISOR', 'ADMIN', 'FARMER', 'FPO')")
    public ProduceImageResponse reverify(@PathVariable UUID id, @AuthenticationPrincipal SecurityUser principal) {
        return produceImageService.reverify(id, principal.getUser());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('PLOT_SELECTION', 'SUPERVISOR', 'ADMIN', 'FARMER', 'FPO')")
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal SecurityUser principal) {
        produceImageService.delete(id, principal.getUser());
    }
}
