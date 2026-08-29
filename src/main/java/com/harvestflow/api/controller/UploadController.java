package com.harvestflow.api.controller;

import com.harvestflow.api.dto.upload.UploadResponse;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> IMAGE_OR_PDF_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private final FileStorageService fileStorageService;

    @PostMapping(value = "/plot-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('PLOT_SELECTION')")
    public UploadResponse uploadPlotPhoto(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        var stored = fileStorageService.store(file, "plot-photos", IMAGE_TYPES, MAX_SIZE_BYTES);
        log.info("Plot photo uploaded by {}: {}", principal.getUsername(), stored.key());
        return UploadResponse.from(stored);
    }

    @PostMapping(value = "/weight-slip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPERVISOR')")
    public UploadResponse uploadWeightSlip(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        var stored = fileStorageService.store(file, "weight-slips", IMAGE_OR_PDF_TYPES, MAX_SIZE_BYTES);
        log.info("Weight slip uploaded by {}: {}", principal.getUsername(), stored.key());
        return UploadResponse.from(stored);
    }

    /**
     * Phase 2 — produce listing photos. Returns the same {@code url} shape
     * as every other upload endpoint; the frontend attaches it to a
     * listing via {@code POST /api/marketplace/listings/{id}/images}.
     * Storage-wise this is identical to a plot photo — same allowed types,
     * same size cap — just a different sub-folder and a different owning
     * role (Farmer/FPO, not Plot Selection).
     */
    @PostMapping(value = "/produce-photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public UploadResponse uploadProducePhoto(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal SecurityUser principal
    ) {
        var stored = fileStorageService.store(file, "produce-photos", IMAGE_TYPES, MAX_SIZE_BYTES);
        log.info("Produce photo uploaded by {}: {}", principal.getUsername(), stored.key());
        return UploadResponse.from(stored);
    }
}
