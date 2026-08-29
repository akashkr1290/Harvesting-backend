package com.harvestflow.api.service.image;

import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Default {@link ImageAuthenticityService} implementation.
 *
 * <p><b>What this is:</b> a small set of file-level, rule-based checks — it
 * reads pixel data, scans for known AI-generator metadata signatures, and
 * looks at a couple of weak structural signals (missing camera EXIF,
 * unusually low bytes-per-pixel for the claimed resolution). Every one of
 * these is a real, individually well-known technique.
 *
 * <p><b>What this is not:</b> a trained deepfake/AI-image classifier. It
 * cannot reliably catch a modern generated image that has had its metadata
 * stripped, nor subtle local edits (splicing, inpainting) with no metadata
 * trace. It is intentionally conservative: it only escalates to a HIGH_*
 * risk verdict on a strong, specific signal (an embedded AI-generation
 * parameter block), and falls back to SUSPICIOUS — routed to manual review,
 * never silently accepted or silently rejected — whenever signals are
 * present but not conclusive.
 *
 * <p>This is the seam described in {@link ImageAuthenticityService}: Phase 5
 * (or any later phase) can add a real ML/vendor-backed implementation and
 * switch to it via {@code harvestflow.image-verification.provider} without
 * touching {@link com.harvestflow.api.service.ProduceImageService}.
 */
@Service
@ConditionalOnProperty(
        name = "harvestflow.image-verification.provider",
        havingValue = "heuristic",
        matchIfMissing = true
)
public class HeuristicImageAuthenticityService implements ImageAuthenticityService {

    private static final Logger log = LoggerFactory.getLogger(HeuristicImageAuthenticityService.class);
    private static final String VERIFIER_ID = "heuristic-v1";

    /**
     * ASCII/UTF-8 substrings that popular AI image generators write into
     * PNG tEXt/iTXt chunks or JPEG EXIF/XMP "Software"/"CreatorTool" fields
     * by default. A hit here is a strong signal — these are not phrases
     * that appear in ordinary camera or phone photos.
     */
    private static final List<String> AI_GENERATION_MARKERS = List.of(
            "Stable Diffusion", "stable-diffusion", "AUTOMATIC1111",
            "ComfyUI", "Midjourney", "DALL\u00b7E", "DALL-E", "DALLE",
            "NightCafe", "Leonardo.Ai", "Leonardo Ai", "Playground AI",
            "Adobe Firefly", "RunwayML", "Ideogram", "parameters:\nSteps:",
            "\"prompt\":", "negative_prompt"
    );

    private static final int MAX_SCAN_BYTES = 2 * 1024 * 1024; // metadata lives near the start/end; no need to scan huge files fully

    @Override
    public AuthenticityResult analyze(Path imagePath, String contentType) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(imagePath);
        } catch (IOException e) {
            log.warn("Could not read stored image for verification: {}", imagePath, e);
            return AuthenticityResult.failed(VERIFIER_ID, "Stored file could not be read for verification.");
        }

        BufferedImage decoded;
        try {
            decoded = ImageIO.read(imagePath.toFile());
        } catch (IOException e) {
            decoded = null;
        }
        if (decoded == null) {
            return AuthenticityResult.failed(VERIFIER_ID,
                    "File could not be decoded as a valid image (corrupted, truncated, or not a genuine image file).");
        }

        List<String> reasons = new ArrayList<>();
        boolean strongAiSignal = scanForAiGenerationMarkers(bytes, reasons);
        boolean missingCameraExif = isJpegWithoutCameraExif(bytes, contentType);
        boolean unusuallyLowDensity = hasUnusuallyLowByteDensity(bytes.length, decoded.getWidth(), decoded.getHeight());

        if (missingCameraExif) {
            reasons.add("No camera EXIF (Make/Model) metadata found — common in AI-generated, screenshotted, or downloaded images, but also true of many phone photos after re-saving or messaging-app compression.");
        }
        if (unusuallyLowDensity) {
            reasons.add("File size is unusually small relative to its pixel resolution, consistent with heavy re-compression or re-saving.");
        }

        if (strongAiSignal) {
            return new AuthenticityResult(ImageVerificationStatus.HIGH_AI_GENERATION_RISK, 0.85, reasons, VERIFIER_ID);
        }

        // Weak/ambiguous signals alone never justify a HIGH_* verdict — only manual review.
        int weakSignalCount = (missingCameraExif ? 1 : 0) + (unusuallyLowDensity ? 1 : 0);
        if (weakSignalCount >= 2) {
            return new AuthenticityResult(ImageVerificationStatus.SUSPICIOUS, 0.4, reasons, VERIFIER_ID);
        }
        if (weakSignalCount == 1) {
            reasons.add("Signal is weak on its own; not sufficient to withhold the listing.");
            return new AuthenticityResult(ImageVerificationStatus.AUTHENTICITY_LIKELY, 0.55, reasons, VERIFIER_ID);
        }

        reasons.add("No manipulation or AI-generation signals detected by the current checks.");
        return new AuthenticityResult(ImageVerificationStatus.AUTHENTICITY_LIKELY, 0.6, reasons, VERIFIER_ID);
    }

    @Override
    public String verifierId() {
        return VERIFIER_ID;
    }

    private boolean scanForAiGenerationMarkers(byte[] bytes, List<String> reasons) {
        int scanLen = Math.min(bytes.length, MAX_SCAN_BYTES);
        String haystack = new String(bytes, 0, scanLen, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        for (String marker : AI_GENERATION_MARKERS) {
            if (haystack.contains(marker.toLowerCase(Locale.ROOT))) {
                reasons.add("Embedded metadata matches a known AI-image-generator signature.");
                return true;
            }
        }
        return false;
    }

    /** JPEG-only: real camera photos almost always carry an EXIF "Make"/"Model" pair; most generated/downloaded images don't. */
    private boolean isJpegWithoutCameraExif(byte[] bytes, String contentType) {
        if (!"image/jpeg".equals(contentType)) {
            return false; // PNG doesn't carry EXIF the same way; don't penalize it for this
        }
        int scanLen = Math.min(bytes.length, MAX_SCAN_BYTES);
        String ascii = new String(bytes, 0, scanLen, StandardCharsets.ISO_8859_1);
        boolean hasExifMarker = ascii.contains("Exif");
        boolean hasMakeLikeTag = ascii.contains("Make") && ascii.contains("Model");
        return !(hasExifMarker && hasMakeLikeTag);
    }

    private boolean hasUnusuallyLowByteDensity(long fileSizeBytes, int width, int height) {
        long pixels = (long) width * (long) height;
        if (pixels == 0) return false;
        double bytesPerPixel = fileSizeBytes / (double) pixels;
        // Heavily-recompressed JPEGs commonly fall well under this; a first-generation
        // camera JPEG or a PNG is almost always well above it. Threshold is intentionally
        // conservative so this fires rarely and only alongside other signals.
        return bytesPerPixel < 0.05;
    }
}
