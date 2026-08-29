package com.harvestflow.api.service;

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
import com.harvestflow.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

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
    void publishingAListing_makesItVisibleToBuyerSearch() {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        marketplaceService.publish(listing.getId(), farmer);

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
    void acceptingAnOffer_createsAnOrder_closesTheListing_andRejectsOtherOffers() {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        marketplaceService.publish(listing.getId(), farmer);

        Offer accepted = offerService.submit(buyer, listing.getId(),
                new SubmitOfferRequest(500.0, 3.5, "I'll take half"));
        Offer other = offerService.submit(otherBuyer, listing.getId(),
                new SubmitOfferRequest(1000.0, 3.2, "I'll take it all"));

        MarketOrder order = offerService.accept(accepted.getId(), farmer);

        assertThat(order.getBuyer().getId()).isEqualTo(buyer.getId());
        assertThat(order.getTotalAmount()).isEqualTo(500.0 * 3.5);

        ProduceListing reloaded = marketplaceService.getById(listing.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ListingStatus.CLOSED);

        Offer otherReloaded = offerService.getById(other.getId());
        assertThat(otherReloaded.getStatus()).isEqualTo(OfferStatus.REJECTED);
    }

    @Test
    void aFarmer_cannotAcceptAnOfferOnSomeoneElsesListing() {
        User otherFarmer = createUser("farmer2.tester", UserRole.FARMER);
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        marketplaceService.publish(listing.getId(), farmer);
        Offer offer = offerService.submit(buyer, listing.getId(),
                new SubmitOfferRequest(500.0, 3.5, null));

        assertThatThrownBy(() -> offerService.accept(offer.getId(), otherFarmer))
                .isInstanceOf(ForbiddenRoleException.class);
    }

    @Test
    void earningsSummary_reflectsAcceptedOrders_andNothingElse() {
        ProduceListing listing = marketplaceService.create(farmer, sampleListingRequest());
        marketplaceService.publish(listing.getId(), farmer);
        Offer offer = offerService.submit(buyer, listing.getId(),
                new SubmitOfferRequest(500.0, 3.5, null));
        offerService.accept(offer.getId(), farmer);

        var earnings = orderService.earningsFor(farmer.getId());

        assertThat(earnings.totalEarnings()).isEqualTo(500.0 * 3.5);
        assertThat(earnings.confirmedOrders()).isEqualTo(1);
        assertThat(earnings.completedOrders()).isEqualTo(0);
    }
}
