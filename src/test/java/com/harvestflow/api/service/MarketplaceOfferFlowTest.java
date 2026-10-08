package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.entity.MarketOrder;
import com.harvestflow.api.domain.entity.Offer;
import com.harvestflow.api.domain.entity.ProduceListing;
import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.ListingStatus;
import com.harvestflow.api.domain.enums.OfferStatus;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.dto.marketplace.CreateListingRequest;
import com.harvestflow.api.dto.marketplace.SubmitOfferRequest;
import com.harvestflow.api.exception.ForbiddenRoleException;
import com.harvestflow.api.exception.InvalidMarketplaceStateException;
import com.harvestflow.api.repository.HarvestCaseRepository;
import com.harvestflow.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the Phase 2 marketplace happy path (create → publish → offer →
 * accept → order + earnings) and the guard rails around it — the
 * marketplace equivalent of {@link CaseServiceStateMachineTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketplaceOfferFlowTest {

    @Autowired private MarketplaceService marketplaceService;
    @Autowired private OfferService offerService;
    @Autowired private OrderService orderService;
    @Autowired private CaseService caseService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ProduceImageService produceImageService;
    @Autowired private HarvestCaseRepository harvestCaseRepository;

    private User farmer;
    private User buyer;
    private User otherBuyer;

    @BeforeEach
    void setUp() {
        farmer = createUser("farmer.tester", UserRole.FARMER);
        buyer = createUser("buyer.tester", UserRole.CONSUMER);
        otherBuyer = createUser("buyer2.tester", UserRole.BULK_BUYER);
    }

    private User createUser(String username, UserRole role) {
        User user = new User();
        user.setName(username);
        user.setMobile("9000000000");
        user.setEmail(username + "@test.local");
        user.setRole(role);
        user.setLocation("Nashik");
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("irrelevant"));
        user.setActive(true);
        user.setMustChangePassword(false);
        return userRepository.save(user);
    }

    private CreateListingRequest sampleListingRequest() {
        return new CreateListingRequest("Sugarcane", 1000.0, "kg", "Grade A",
                LocalDate.now().plusDays(2), "Nashik", 3.0, "Fresh cut");
    }

    private void publishWithVerifiedPhoto(ProduceListing listing, User seller) throws IOException {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, listing.getId().hashCode());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        produceImageService.upload(
                new MockMultipartFile("file", "crop-" + listing.getId() + ".png", "image/png", out.toByteArray()),
                null, listing.getId(), true, seller.getUsername());
        marketplaceService.publish(listing.getId(), seller);
    }

    @Test
    void creatingAListing_startsAsDraft_andIsInvisibleToBuyerSearch() {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());

        assertThat(listing.getStatus()).isEqualTo(ListingStatus.DRAFT);
        assertThat(marketplaceService.searchMarketplace(null, null, null, null, null)).isEmpty();
    }

    @Test
    void onlyFarmerOrFpo_canCreateAListing() {
        assertThatThrownBy(() -> marketplaceService.create(buyer, sampleListingRequest()))
                .isInstanceOf(ForbiddenRoleException.class);
    }

    @Test
    void publishingAListing_makesItVisibleToBuyerSearch() throws IOException {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);

        assertThat(marketplaceService.searchMarketplace(null, null, null, null, null))
                .extracting(ProduceListing::getId)
                .containsExactly(listing.getId());
    }

    @Test
    void submittingAnOffer_onADraftListing_isRejected() {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());

        assertThatThrownBy(() -> offerService.submit(buyer, listing.getId(),
                new SubmitOfferRequest(500.0, 3.5, "Interested")))
                .isInstanceOf(InvalidMarketplaceStateException.class);
    }

    @Test
    void acceptingAPartialOffer_createsAnOrder_keepsTheRestListed_andRejectsOffersThatNoLongerFit() throws IOException {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);

        Offer accepted = offerService.submit(buyer, listing.getId(),
                new SubmitOfferRequest(500.0, 3.5, "I'll take half"));
        Offer other = offerService.submit(otherBuyer, listing.getId(),
                new SubmitOfferRequest(1000.0, 3.2, "I'll take it all"));

        MarketOrder order = offerService.accept(accepted.getId(), farmer);

        assertThat(order.getBuyer().getId()).isEqualTo(buyer.getId());
        assertThat(order.getTotalAmount()).isEqualTo(500.0 * 3.5);

        var internalCase = harvestCaseRepository.findByMarketOrderId(order.getId()).orElseThrow();
        assertThat(internalCase.getStatus()).isEqualTo(com.harvestflow.api.domain.enums.CaseStatus.AWAITING_PLOT_SELECTION);
        assertThat(internalCase.getMarketOrderId()).isEqualTo(order.getId());
        assertThat(internalCase.getFarmerName()).isEqualTo(farmer.getName());
        assertThat(internalCase.getCropType()).isEqualTo("Sugarcane");

        ProduceListing reloaded = marketplaceService.getById(listing.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.PUBLISHED);
        assertThat(reloaded.getAvailableQuantity()).isEqualTo(500.0);

        // 1000 kg no longer fits in the 500 kg left, so that buyer is told now.
        Offer otherReloaded = offerService.getById(other.getId());
        assertThat(otherReloaded.getStatus()).isEqualTo(OfferStatus.REJECTED);
    }

    @Test
    void sellingTheLastQuantity_closesTheListing() throws IOException {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);

        Offer first = offerService.submit(buyer, listing.getId(), new SubmitOfferRequest(600.0, 3.5, null));
        Offer second = offerService.submit(otherBuyer, listing.getId(), new SubmitOfferRequest(400.0, 3.4, null));

        offerService.accept(first.getId(), farmer);
        assertThat(offerService.getById(second.getId()).getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(marketplaceService.getById(listing.getId()).getAvailableQuantity()).isEqualTo(400.0);

        offerService.accept(second.getId(), farmer);
        ProduceListing reloaded = marketplaceService.getById(listing.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.CLOSED);
        assertThat(reloaded.getAvailableQuantity()).isEqualTo(0.0);
    }

    @Test
    void anOfferAboveTheAvailableQuantity_isRejected() throws IOException {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);
        Offer first = offerService.submit(buyer, listing.getId(), new SubmitOfferRequest(600.0, 3.5, null));
        offerService.accept(first.getId(), farmer);

        assertThatThrownBy(() -> offerService.submit(otherBuyer, listing.getId(), new SubmitOfferRequest(500.0, 3.5, null)))
                .isInstanceOf(InvalidMarketplaceStateException.class);
    }

    @Test
    void anExpiredOffer_cannotBeAccepted_andTheJobMarksItExpired() throws IOException {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);
        Offer offer = offerService.submit(buyer, listing.getId(), new SubmitOfferRequest(100.0, 3.5, null));
        assertThat(offer.getExpiresAt()).isNotNull();
        offer.setExpiresAt(java.time.Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> offerService.accept(offer.getId(), farmer))
                .isInstanceOf(InvalidMarketplaceStateException.class);
        assertThat(offerService.expireStaleOffers()).isGreaterThanOrEqualTo(1);
        assertThat(offerService.getById(offer.getId()).getStatus()).isEqualTo(OfferStatus.EXPIRED);
    }

    @Test
    void closingAListing_rejectsItsPendingOffers() throws IOException {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);
        Offer offer = offerService.submit(buyer, listing.getId(), new SubmitOfferRequest(100.0, 3.5, null));

        marketplaceService.close(listing.getId(), farmer);

        assertThat(offerService.getById(offer.getId()).getStatus()).isEqualTo(OfferStatus.REJECTED);
    }

    @Test
    void smallOrders_skipTheInternalWorkflow_whenTheDirectLaneIsEnabled() throws IOException {
        OfferService target = org.springframework.test.util.AopTestUtils.getTargetObject(offerService);
        org.springframework.test.util.ReflectionTestUtils.setField(target, "directMaxKg", 100.0);
        try {
            ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
            publishWithVerifiedPhoto(listing, farmer);
            Offer small = offerService.submit(buyer, listing.getId(), new SubmitOfferRequest(20.0, 3.5, null));

            MarketOrder order = offerService.accept(small.getId(), farmer);

            assertThat(order.getFulfilmentMode()).isEqualTo(com.harvestflow.api.domain.enums.FulfilmentMode.DIRECT);
            assertThat(harvestCaseRepository.findByMarketOrderId(order.getId())).isEmpty();
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(target, "directMaxKg", 0.0);
        }
    }

    @Test
    void plotSelection_acceptMovesMarketplaceCaseIntoExistingPlanningQueue() throws IOException {
        User plotSelection = createUser("plot.tester", UserRole.PLOT_SELECTION);
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);
        Offer offer = offerService.submit(buyer, listing.getId(),
                new SubmitOfferRequest(500.0, 3.5, null));

        MarketOrder order = offerService.accept(offer.getId(), farmer);
        var pending = harvestCaseRepository.findByMarketOrderId(order.getId()).orElseThrow();

        assertThat(pending.getStatus()).isEqualTo(com.harvestflow.api.domain.enums.CaseStatus.AWAITING_PLOT_SELECTION);
        assertThat(caseService.pendingFor(UserRole.PLOT_SELECTION))
                .extracting(HarvestCase::getId)
                .contains(pending.getId());
        assertThat(caseService.pendingFor(UserRole.PLANNING))
                .extracting(HarvestCase::getId)
                .doesNotContain(pending.getId());

        HarvestCase accepted = caseService.acceptPlotSelection(pending.getId(), plotSelection);

        assertThat(accepted.getStatus()).isEqualTo(com.harvestflow.api.domain.enums.CaseStatus.SUBMITTED_FOR_PLANNING);
        assertThat(accepted.getTimeline()).anyMatch(t -> t.getAction().contains("Accepted plot selection"));
    }

    @Test
    void plotSelection_acceptRejectsWrongRoleAndWrongState() throws IOException {
        User plotSelection = createUser("plot.tester", UserRole.PLOT_SELECTION);
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);
        Offer offer = offerService.submit(buyer, listing.getId(),
                new SubmitOfferRequest(500.0, 3.5, null));
        MarketOrder order = offerService.accept(offer.getId(), farmer);
        var pending = harvestCaseRepository.findByMarketOrderId(order.getId()).orElseThrow();

        assertThatThrownBy(() -> caseService.acceptPlotSelection(pending.getId(), buyer))
                .isInstanceOf(ForbiddenRoleException.class);

        caseService.acceptPlotSelection(pending.getId(), plotSelection);
        assertThatThrownBy(() -> caseService.acceptPlotSelection(pending.getId(), plotSelection))
                .isInstanceOf(com.harvestflow.api.exception.InvalidTransitionException.class);
    }

    @Test
    void aFarmer_cannotAcceptAnOfferOnSomeoneElsesListing() throws IOException {
        User otherFarmer = createUser("farmer2.tester", UserRole.FARMER);
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);
        Offer offer = offerService.submit(buyer, listing.getId(),
                new SubmitOfferRequest(500.0, 3.5, null));

        assertThatThrownBy(() -> offerService.accept(offer.getId(), otherFarmer))
                .isInstanceOf(ForbiddenRoleException.class);
    }

    @Test
    void earningsSummary_reflectsAcceptedOrders_andNothingElse() throws IOException {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        publishWithVerifiedPhoto(listing, farmer);
        Offer offer = offerService.submit(buyer, listing.getId(),
                new SubmitOfferRequest(500.0, 3.5, null));
        offerService.accept(offer.getId(), farmer);

        var earnings = orderService.earningsFor(farmer.getId());

        assertThat(earnings.totalEarnings()).isEqualTo(500.0 * 3.5);
        assertThat(earnings.confirmedOrders()).isEqualTo(1);
        assertThat(earnings.completedOrders()).isEqualTo(0);
        assertThat(earnings.receivedAmount()).isEqualTo(0.0);
        assertThat(earnings.expectedAmount()).isEqualTo(500.0 * 3.5);
    }
}
