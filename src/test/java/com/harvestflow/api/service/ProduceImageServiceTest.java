package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.ProduceListing;
import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.ImageDecision;
import com.harvestflow.api.domain.enums.ImageVerificationStatus;
import com.harvestflow.api.domain.enums.ListingStatus;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.dto.image.ProduceImageResponse;
import com.harvestflow.api.exception.ForbiddenRoleException;
import com.harvestflow.api.exception.InvalidUploadException;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.ProduceListingRepository;
import com.harvestflow.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real upload → store → verify → decide pipeline end-to-end
 * against the default (heuristic) provider — the same wiring
 * {@code application.yml} uses when no override is set.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProduceImageServiceTest {

    @Autowired
    private ProduceImageService produceImageService;

    @Autowired
    private ProduceListingRepository produceListingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Phase 5: a FARMER may reverify/delete a photo on their own listing but
     * not on another farmer's — the same ownership rule
     * {@code MarketplaceService.requireOwner} already enforces for the
     * listing itself (Part 8 of the master spec).
     */
    @Test
    void farmerCanManageOwnListingImageButNotAnotherFarmersListingImage() throws IOException {
        User ownerFarmer = saveFarmer("owner.farmer");
        User otherFarmer = saveFarmer("other.farmer");
        ProduceListing listing = saveDraftListing(ownerFarmer);

        ProduceImageResponse uploaded = produceImageService.upload(
                pngFile("listing-photo.png", 200, 200), null, listing.getId(), true, ownerFarmer.getUsername());

        assertThatThrownBy(() -> produceImageService.reverify(uploaded.id(), otherFarmer))
                .isInstanceOf(ForbiddenRoleException.class);
        assertThatThrownBy(() -> produceImageService.delete(uploaded.id(), otherFarmer))
                .isInstanceOf(ForbiddenRoleException.class);

        ProduceImageResponse reverified = produceImageService.reverify(uploaded.id(), ownerFarmer);
        assertThat(reverified.id()).isEqualTo(uploaded.id());
    }

    private User saveFarmer(String username) {
        User farmer = new User();
        farmer.setName(username);
        farmer.setUsername(username);
        farmer.setEmail(username + "@example.test");
        farmer.setRole(UserRole.FARMER);
        farmer.setPasswordHash(passwordEncoder.encode("irrelevant"));
        farmer.setMustChangePassword(false);
        return userRepository.save(farmer);
    }

    private ProduceListing saveDraftListing(User seller) {
        ProduceListing listing = new ProduceListing();
        listing.setSeller(seller);
        listing.setSellerRole(seller.getRole());
        listing.setSellerName(seller.getName());
        listing.setCropName("Tomato");
        listing.setQuantity(100.0);
        listing.setUnit("kg");
        listing.setHarvestDate(java.time.LocalDate.now().plusDays(1));
        listing.setLocation("Nashik");
        listing.setExpectedPricePerUnit(32.0);
        listing.setStatus(ListingStatus.DRAFT);
        return produceListingRepository.save(listing);
    }

    @Test
    void uploadingAnOrdinaryPngIsAcceptedAndPersisted() throws IOException {
        MockMultipartFile file = pngFile("crop.png", 300, 200);

        ProduceImageResponse response = produceImageService.upload(file, null, true, "plot.tester");

        assertThat(response.id()).isNotNull();
        assertThat(response.decision()).isEqualTo(ImageDecision.ACCEPT);
        assertThat(response.verificationStatus()).isEqualTo(ImageVerificationStatus.AUTHENTICITY_LIKELY);
        assertThat(response.isPrimary()).isTrue();
        assertThat(response.url()).contains("produce-images");

        ProduceImageResponse fetched = produceImageService.getById(response.id());
        assertThat(fetched.id()).isEqualTo(response.id());
    }

    @Test
    void unsupportedExtensionIsRejectedBeforeStorage() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> produceImageService.upload(file, null, false, "plot.tester"))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> produceImageService.upload(file, null, false, "plot.tester"))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void reuploadingIdenticalImageForADifferentCaseIsFlaggedSuspicious() throws IOException {
        UUID caseA = UUID.randomUUID();
        UUID caseB = UUID.randomUUID();
        MockMultipartFile file = pngFile("crop.png", 250, 250);

        produceImageService.upload(file, caseA, false, "plot.tester");
        ProduceImageResponse second = produceImageService.upload(file, caseB, false, "plot.tester");

        assertThat(second.verificationStatus()).isEqualTo(ImageVerificationStatus.SUSPICIOUS);
        assertThat(second.decision()).isEqualTo(ImageDecision.MANUAL_REVIEW);
        assertThat(second.verificationReasons()).anyMatch(r -> r.contains("already uploaded"));
    }

    @Test
    void reverifyOnUnknownImageThrowsNotFound() {
        // findById() is checked before the (Phase 5) ownership check, so any
        // requester — including this bare ADMIN stand-in — reaches the same
        // ResourceNotFoundException the pre-Phase-5 1-arg overload asserted.
        com.harvestflow.api.domain.entity.User admin = new com.harvestflow.api.domain.entity.User();
        admin.setRole(com.harvestflow.api.domain.enums.UserRole.ADMIN);
        assertThatThrownBy(() -> produceImageService.reverify(UUID.randomUUID(), admin))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void gettingImagesForACaseReturnsOnlyThatCasesImages() throws IOException {
        UUID caseA = UUID.randomUUID();
        UUID caseB = UUID.randomUUID();
        produceImageService.upload(pngFile("a1.png", 100, 100), caseA, true, "plot.tester");
        produceImageService.upload(pngFile("a2.png", 100, 100), caseA, false, "plot.tester");
        produceImageService.upload(pngFile("b1.png", 100, 100), caseB, true, "plot.tester");

        assertThat(produceImageService.getForCase(caseA)).hasSize(2);
        assertThat(produceImageService.getForCase(caseB)).hasSize(1);
    }

    private MockMultipartFile pngFile(String name, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", name, "image/png", out.toByteArray());
    }
}
