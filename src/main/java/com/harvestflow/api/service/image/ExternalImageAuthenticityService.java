package com.harvestflow.api.service.image;

import com.fasterxml.jackson.databind.JsonNode;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import com.harvestflow.api.service.ai.AiServiceClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Crop photo checks by the Python AI service (ai-service/, POST /image/verify):
 * AI-generator metadata, EXIF, GPS vs farm, near-duplicate photos across sellers,
 * and optionally CLIP crop matching and an AI-image detector.
 *
 * <p>Select with {@code IMAGE_VERIFICATION_PROVIDER=external} and {@code AI_ENABLED=true}.
 * If the AI service is off or does not answer, the built-in
 * {@link HeuristicImageAuthenticityService} runs instead, so uploads never fail
 * because of the AI service.
 */
@Service
@ConditionalOnProperty(name = "harvestflow.image-verification.provider", havingValue = "external")
public class ExternalImageAuthenticityService implements ImageAuthenticityService {

    private static final Duration TIMEOUT = Duration.ofSeconds(25);

    private final AiServiceClient ai;
    private final HeuristicImageAuthenticityService fallback = new HeuristicImageAuthenticityService();

    public ExternalImageAuthenticityService(AiServiceClient ai) {
        this.ai = ai;
    }

    @Override
    public AuthenticityResult analyze(Path imagePath, String contentType) {
        return analyze(imagePath, contentType, new ImageContext(null, null, null, null, null, null, false));
    }

    @Override
    public AuthenticityResult analyze(Path imagePath, String contentType, ImageContext ctx) {
        Map<String, String> fields = new HashMap<>();
        if (ctx != null) {
            put(fields, "ownerId", ctx.ownerUsername());
            put(fields, "listingId", ctx.listingId());
            put(fields, "imageId", ctx.imageId());
            put(fields, "claimedCrop", ctx.cropName());
            if (ctx.farmLatitude() != null && ctx.farmLongitude() != null) {
                fields.put("farmLat", ctx.farmLatitude().toString());
                fields.put("farmLng", ctx.farmLongitude().toString());
            }
            fields.put("capturedInApp", Boolean.toString(ctx.capturedInApp()));
        }
        Optional<JsonNode> res = ai.postImage("/image/verify", imagePath, contentType, fields, TIMEOUT);
        if (res.isEmpty()) {
            AuthenticityResult local = fallback.analyze(imagePath, contentType);
            List<String> reasons = new ArrayList<>(local.reasons());
            reasons.add("AI service unavailable: built-in checks used.");
            return new AuthenticityResult(local.status(), local.confidence(), reasons, local.verifierId());
        }
        JsonNode n = res.get();
        List<String> reasons = new ArrayList<>();
        n.path("reasons").forEach(r -> reasons.add(r.asText()));
        JsonNode crop = n.path("cropMatch");
        if (crop.isObject() && crop.has("probability")) {
            reasons.add("Crop match (" + crop.path("crop").asText() + "): " + Math.round(crop.path("probability").asDouble() * 100) + "%");
        }
        if (n.hasNonNull("aiGeneratedProbability")) {
            reasons.add("AI-image detector: " + Math.round(n.path("aiGeneratedProbability").asDouble() * 100) + "% likely generated");
        }
        return new AuthenticityResult(toStatus(n.path("status").asText(), n.path("decision").asText()),
                n.path("authenticityScore").isNumber() ? n.path("authenticityScore").asDouble() : null,
                reasons,
                n.path("verifier").asText(verifierId()));
    }

    static ImageVerificationStatus toStatus(String status, String decision) {
        try {
            if ("FAILED".equals(status)) return ImageVerificationStatus.VERIFICATION_FAILED;
            return ImageVerificationStatus.valueOf(status);
        } catch (Exception e) {
            return switch (decision) {
                case "ACCEPT" -> ImageVerificationStatus.AUTHENTICITY_LIKELY;
                case "REJECT" -> ImageVerificationStatus.HIGH_MANIPULATION_RISK;
                default -> ImageVerificationStatus.SUSPICIOUS;
            };
        }
    }

    private static void put(Map<String, String> m, String k, String v) {
        if (v != null && !v.isBlank()) m.put(k, v);
    }

    @Override
    public String verifierId() {
        return "harvestflow-ai";
    }
}
