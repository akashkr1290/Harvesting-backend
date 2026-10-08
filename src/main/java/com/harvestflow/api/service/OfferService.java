package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.MarketOrder;
import com.harvestflow.api.domain.entity.Offer;
import com.harvestflow.api.domain.entity.ProduceListing;
import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.ListingStatus;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.domain.enums.OfferStatus;
import com.harvestflow.api.domain.enums.OrderStatus;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.dto.marketplace.SubmitOfferRequest;
import com.harvestflow.api.exception.ForbiddenRoleException;
import com.harvestflow.api.exception.InvalidMarketplaceStateException;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.MarketOrderRepository;
import com.harvestflow.api.repository.OfferRepository;
import com.harvestflow.api.service.notification.NotificationService;
import com.harvestflow.api.domain.enums.FulfilmentMode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

/**
 * Buyer offer lifecycle and the accept-creates-an-order step.
 *
 * <p>A listing can be sold in parts: accepting an offer subtracts its quantity
 * from the listing's available quantity. Pending offers that no longer fit in
 * what is left are rejected, and the listing closes when nothing is left.
 * Offers expire after {@code harvestflow.marketplace.offer-validity-hours}.
 * Orders up to {@code harvestflow.marketplace.direct-max-kg} skip the internal
 * workflow (FulfilmentMode.DIRECT).
 */
@Service
@RequiredArgsConstructor
public class OfferService {

    private static final Logger log = LoggerFactory.getLogger(OfferService.class);

    private final OfferRepository offerRepository;
    private final MarketOrderRepository orderRepository;
    private final MarketplaceService marketplaceService;
    private final NotificationService notificationService;
    private final MarketplaceWorkflowService marketplaceWorkflowService;

    @Value("${harvestflow.marketplace.offer-validity-hours:72}")
    private long offerValidityHours = 72;

    /** 0 disables the direct lane: every order goes through the internal workflow (old behaviour). */
    @Value("${harvestflow.marketplace.direct-max-kg:0}")
    private double directMaxKg = 0;

    private static final double EPSILON = 1e-6;

    // ---------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------

    public Offer getById(UUID id) {
        return offerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + id));
    }

    public List<Offer> forListing(UUID listingId, User requester) {
        ProduceListing listing = marketplaceService.getById(listingId);
        requireListingOwner(listing, requester);
        return offerRepository.findByListing_IdOrderByCreatedAtDesc(listingId);
    }

    public List<Offer> myOffers(UUID buyerId) {
        return offerRepository.findByBuyer_IdOrderByCreatedAtDesc(buyerId);
    }

    /** Every offer across every listing this seller owns — feeds the farmer's offer-management screen. */
    public List<Offer> offersOnMyListings(UUID sellerId) {
        return offerRepository.findByListing_Seller_IdOrderByCreatedAtDesc(sellerId);
    }

    // ---------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------

    @Transactional
    public Offer submit(User buyer, UUID listingId, SubmitOfferRequest req) {
        if (buyer.getRole() != UserRole.CONSUMER && buyer.getRole() != UserRole.BULK_BUYER) {
            throw new ForbiddenRoleException("Only a Consumer or Bulk Buyer account can submit an offer.");
        }
        ProduceListing listing = marketplaceService.getById(listingId);
        if (listing.getStatus() != ListingStatus.PUBLISHED) {
            throw new InvalidMarketplaceStateException("This listing is no longer accepting offers.");
        }
        double available = available(listing);
        if (req.quantity() > available + EPSILON) {
            throw new InvalidMarketplaceStateException(
                    "Offer quantity cannot exceed the available quantity of " + available + " " + listing.getUnit() + ".");
        }

        Offer offer = new Offer();
        offer.setListing(listing);
        offer.setBuyer(buyer);
        offer.setBuyerRole(buyer.getRole());
        offer.setBuyerName(buyer.getName());
        offer.setQuantity(req.quantity());
        offer.setPricePerUnit(req.pricePerUnit());
        offer.setMessage(req.message());
        offer.setExpiresAt(Instant.now().plus(Duration.ofHours(Math.max(1, offerValidityHours))));
        Offer saved = offerRepository.save(offer);

        notificationService.notify(listing.getSeller().getUsername(), NotificationCategory.OFFER,
                "New offer received",
                buyer.getName() + " offered " + req.pricePerUnit() + "/unit for " + req.quantity()
                        + " " + listing.getUnit() + " of " + listing.getCropName() + ".",
                "OFFER", saved.getId());
        return saved;
    }

    @Transactional
    public Offer withdraw(UUID offerId, User buyer) {
        Offer offer = getById(offerId);
        if (!offer.getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenRoleException("You do not own this offer.");
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new InvalidMarketplaceStateException("Only a pending offer can be withdrawn.");
        }
        offer.setStatus(OfferStatus.WITHDRAWN);
        offer.setDecidedAt(Instant.now());
        Offer saved = offerRepository.save(offer);
        notificationService.notify(offer.getListing().getSeller().getUsername(), NotificationCategory.OFFER,
                "Offer withdrawn",
                buyer.getName() + " withdrew their offer on your " + offer.getListing().getCropName() + " listing.",
                "OFFER", saved.getId());
        return saved;
    }

    @Transactional
    public MarketOrder accept(UUID offerId, User seller) {
        Offer offer = offerRepository.findLockedById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + offerId));
        ProduceListing listing = marketplaceService.getByIdForUpdate(offer.getListing().getId());
        requireListingOwner(listing, seller);
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new InvalidMarketplaceStateException("Only a pending offer can be accepted.");
        }
        if (listing.getStatus() != ListingStatus.PUBLISHED) {
            throw new InvalidMarketplaceStateException("This listing is no longer open.");
        }
        if (offer.getExpiresAt() != null && Instant.now().isAfter(offer.getExpiresAt())) {
            throw new InvalidMarketplaceStateException("This offer has expired. Ask the buyer to send a new offer.");
        }
        double available = available(listing);
        if (offer.getQuantity() > available + EPSILON) {
            throw new InvalidMarketplaceStateException(
                    "Only " + available + " " + listing.getUnit() + " is left, less than this offer's quantity.");
        }

        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setDecidedAt(Instant.now());
        offerRepository.save(offer);

        double remaining = Math.max(0.0, available - offer.getQuantity());
        listing.setAvailableQuantity(remaining);
        boolean soldOut = remaining <= EPSILON;
        if (soldOut) {
            listing.setStatus(ListingStatus.CLOSED);
            listing.setClosedAt(Instant.now());
        }

        // Pending offers that cannot be met from what is left are declined now,
        // so buyers are not left waiting. Offers that still fit stay open.
        for (Offer other : offerRepository.findByListing_IdOrderByCreatedAtDesc(listing.getId())) {
            if (!other.getId().equals(offer.getId()) && other.getStatus() == OfferStatus.PENDING
                    && (soldOut || other.getQuantity() > remaining + EPSILON)) {
                other.setStatus(OfferStatus.REJECTED);
                other.setDecidedAt(Instant.now());
                offerRepository.save(other);
                notificationService.notify(other.getBuyer().getUsername(), NotificationCategory.OFFER,
                        "Offer not accepted",
                        "The " + listing.getCropName() + " you offered on is no longer available in that quantity.",
                        "OFFER", other.getId());
            }
        }

        MarketOrder order = new MarketOrder();
        order.setOffer(offer);
        order.setListing(listing);
        order.setSeller(listing.getSeller());
        order.setBuyer(offer.getBuyer());
        order.setCropName(listing.getCropName());
        order.setQuantity(offer.getQuantity());
        order.setAgreedPricePerUnit(offer.getPricePerUnit());
        order.setTotalAmount(offer.getQuantity() * offer.getPricePerUnit());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setFulfilmentMode(isDirect(offer.getQuantity(), listing.getUnit()) ? FulfilmentMode.DIRECT : FulfilmentMode.ASSISTED);
        MarketOrder saved = orderRepository.save(order);

        // Bulk (assisted) trades go to the internal Plot Selection queue, and the
        // internal state machine then owns every operational stage. Direct orders
        // skip it: only a delivery person is assigned.
        if (saved.getFulfilmentMode() == FulfilmentMode.ASSISTED) {
            marketplaceWorkflowService.createInternalCase(saved);
        }

        notificationService.notify(saved.getBuyer().getUsername(), NotificationCategory.OFFER,
                "Offer accepted",
                "Your offer of " + saved.getAgreedPricePerUnit() + "/unit on " + saved.getCropName()
                        + " was accepted. Order #" + saved.getId() + " is confirmed.",
                "MARKET_ORDER", saved.getId());
        notificationService.notify(saved.getSeller().getUsername(), NotificationCategory.OFFER,
                "Order confirmed",
                "You accepted an offer on " + saved.getCropName() + ". Order #" + saved.getId()
                        + " (" + saved.getQuantity() + " units at " + saved.getAgreedPricePerUnit() + "/unit) is confirmed.",
                "MARKET_ORDER", saved.getId());

        return saved;
    }

    @Transactional
    public Offer reject(UUID offerId, User seller) {
        Offer offer = offerRepository.findLockedById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer not found: " + offerId));
        ProduceListing listing = marketplaceService.getByIdForUpdate(offer.getListing().getId());
        requireListingOwner(listing, seller);
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new InvalidMarketplaceStateException("Only a pending offer can be rejected.");
        }
        offer.setStatus(OfferStatus.REJECTED);
        offer.setDecidedAt(Instant.now());
        Offer saved = offerRepository.save(offer);
        notificationService.notify(saved.getBuyer().getUsername(), NotificationCategory.OFFER,
                "Offer rejected",
                "Your offer of " + saved.getPricePerUnit() + "/unit on " + saved.getListing().getCropName()
                        + " was declined by the seller.",
                "OFFER", saved.getId());
        return saved;
    }

    /** Pending offers past their expiry time become EXPIRED. Called by OfferExpiryJob. */
    @Transactional
    public int expireStaleOffers() {
        int count = 0;
        for (Offer offer : offerRepository.findByStatusAndExpiresAtBefore(OfferStatus.PENDING, Instant.now())) {
            offer.setStatus(OfferStatus.EXPIRED);
            offer.setDecidedAt(Instant.now());
            offerRepository.save(offer);
            notificationService.notify(offer.getBuyer().getUsername(), NotificationCategory.OFFER, "Offer expired",
                    "Your offer on " + offer.getListing().getCropName() + " expired without an answer.", "OFFER", offer.getId());
            count++;
        }
        return count;
    }

    private static double available(ProduceListing listing) {
        Double a = listing.getAvailableQuantity();
        return a != null ? a : listing.getQuantity();
    }

    /** Converts the order to kg; unknown units are never treated as direct. */
    boolean isDirect(double quantity, String unit) {
        if (directMaxKg <= 0 || unit == null) return false;
        String u = unit.trim().toLowerCase(Locale.ROOT);
        double factor;
        if (u.equals("kg") || u.startsWith("kilo")) factor = 1;
        else if (u.startsWith("quintal") || u.equals("q") || u.equals("qtl")) factor = 100;
        else if (u.startsWith("ton") || u.equals("t")) factor = 1000;
        else return false;
        return quantity * factor <= directMaxKg + EPSILON;
    }

    private void requireListingOwner(Offer offer, User seller) {
        requireListingOwner(offer.getListing(), seller);
    }

    private void requireListingOwner(ProduceListing listing, User seller) {
        if (!listing.getSeller().getId().equals(seller.getId())) {
            throw new ForbiddenRoleException("You do not own the listing this offer was made on.");
        }
    }
}
