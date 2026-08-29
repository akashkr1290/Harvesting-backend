package com.harvestflow.api.service;

import com.harvestflow.api.domain.enums.ImageDecision;
import com.harvestflow.api.dto.image.ProduceImageResponse;
import com.harvestflow.api.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProduceImageAdminReviewTest {

    @Autowired
    private ProduceImageService produceImageService;

    @Test
    void duplicateUploadLandsInTheReviewQueueAndAdminCanAccept() throws IOException {
        UUID caseA = UUID.randomUUID();
        UUID caseB = UUID.randomUUID();
        MockMultipartFile file = pngFile("crop.png", 200, 200);

        produceImageService.upload(file, caseA, false, "plot.tester");
        ProduceImageResponse flagged = produceImageService.upload(file, caseB, false, "plot.tester");
        assertThat(flagged.decision()).isEqualTo(ImageDecision.MANUAL_REVIEW);

        assertThat(produceImageService.getReviewQueue()).extracting(ProduceImageResponse::id)
                .contains(flagged.id());

        ProduceImageResponse decided = produceImageService.adminDecide(
                flagged.id(), ImageDecision.ACCEPT, "admin.tester", "Confirmed by admin, looks fine.");

        assertThat(decided.decision()).isEqualTo(ImageDecision.ACCEPT);
        assertThat(decided.adminReviewedByUsername()).isEqualTo("admin.tester");
        assertThat(decided.adminReviewNote()).contains("Confirmed by admin");
        assertThat(decided.adminReviewedAt()).isNotNull();
        // The automated verdict is preserved even though the final decision was overridden.
        assertThat(decided.verificationStatus().name()).isEqualTo("SUSPICIOUS");

        assertThat(produceImageService.getReviewQueue()).extracting(ProduceImageResponse::id)
                .doesNotContain(flagged.id());
    }

    @Test
    void adminDecideOnUnknownImageThrowsNotFound() {
        assertThatThrownBy(() -> produceImageService.adminDecide(
                UUID.randomUUID(), ImageDecision.ACCEPT, "admin.tester", null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminDecideWithNullDecisionIsRejected() throws IOException {
        ProduceImageResponse image = produceImageService.upload(pngFile("x.png", 50, 50), UUID.randomUUID(), false, "plot.tester");

        assertThatThrownBy(() -> produceImageService.adminDecide(image.id(), null, "admin.tester", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MockMultipartFile pngFile(String name, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", name, "image/png", out.toByteArray());
    }
}
