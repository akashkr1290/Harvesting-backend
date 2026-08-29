package com.harvestflow.api.service.image;

import com.harvestflow.api.domain.enums.ImageDecision;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicImageAuthenticityServiceTest {

    private final HeuristicImageAuthenticityService service = new HeuristicImageAuthenticityService();

    @Test
    void ordinaryPngWithNoSignalsIsAuthenticityLikely(@TempDir Path tempDir) throws IOException {
        Path file = writePng(tempDir.resolve("plain.png"), 400, 300);

        AuthenticityResult result = service.analyze(file, "image/png");

        assertThat(result.status()).isEqualTo(ImageVerificationStatus.AUTHENTICITY_LIKELY);
        assertThat(result.status().decision()).isEqualTo(ImageDecision.ACCEPT);
        assertThat(result.verifierId()).isEqualTo("heuristic-v1");
        assertThat(result.confidence()).isNotNull();
    }

    @Test
    void corruptFileIsVerificationFailed(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("corrupt.jpg");
        Files.write(file, "not a real image".getBytes(StandardCharsets.UTF_8));

        AuthenticityResult result = service.analyze(file, "image/jpeg");

        assertThat(result.status()).isEqualTo(ImageVerificationStatus.VERIFICATION_FAILED);
        assertThat(result.status().decision()).isEqualTo(ImageDecision.REJECT);
        assertThat(result.confidence()).isNull();
    }

    @Test
    void embeddedStableDiffusionMetadataIsHighAiGenerationRisk(@TempDir Path tempDir) throws IOException {
        Path file = writePng(tempDir.resolve("generated.png"), 512, 512);
        // Append a fake-but-realistic tEXt-style marker after the PNG bytes. The heuristic
        // does a raw substring scan (documented limitation), so this is sufficient to
        // exercise the marker-detection branch without hand-rolling real PNG chunk bytes.
        Files.write(file,
                "\nparameters:\nSteps: 20, Sampler: Euler a, Model hash: abc123".getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);

        AuthenticityResult result = service.analyze(file, "image/png");

        assertThat(result.status()).isEqualTo(ImageVerificationStatus.HIGH_AI_GENERATION_RISK);
        assertThat(result.status().decision()).isEqualTo(ImageDecision.REJECT);
        assertThat(result.reasons()).isNotEmpty();
    }

    @Test
    void jpegMissingCameraExifAloneIsStillAcceptedWithWeakSignalNote(@TempDir Path tempDir) throws IOException {
        Path file = writeJpeg(tempDir.resolve("plain.jpg"), 800, 600);

        AuthenticityResult result = service.analyze(file, "image/jpeg");

        // A single weak signal (no EXIF) must not, by itself, reach a HIGH_* verdict.
        assertThat(result.status()).isIn(ImageVerificationStatus.AUTHENTICITY_LIKELY, ImageVerificationStatus.SUSPICIOUS);
        assertThat(result.status()).isNotEqualTo(ImageVerificationStatus.HIGH_AI_GENERATION_RISK);
        assertThat(result.status()).isNotEqualTo(ImageVerificationStatus.HIGH_MANIPULATION_RISK);
    }

    private Path writePng(Path path, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", path.toFile());
        return path;
    }

    private Path writeJpeg(Path path, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "jpg", path.toFile());
        return path;
    }
}
