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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Buyer offer lifecycle and the accept-creates-an-order step (Phase 2).
 * Accepting an offer also rejects every other pending offer on the same
 * listing and closes it — a listing represents one lot of produce, so it
 * can only ever be sold once.
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
        if (req.quantity() > listing.getQuantity()) {
            throw new InvalidMarketplaceStateException(
                    "Offer quantity cannot exceed the listed quantity of " + listing.getQuantity() + " " + listing.getUnit() + ".");
        }

        Offer offer = new Offer();
        offer.setListing(listing);
        offer.setBuyer(buyer);
        offer.setBuyerRole(buyer.getRole());
        offer.setBuyerName(buyer.getName());
        offer.setQuantity(req.quantity());
        offer.setPricePerUnit(req.pricePerUnit());
        offer.setMessage(req.message());
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

        offer.setStatus(OfferStatus.ACCEPTED);
        offer.setDecidedAt(Instant.now());
        offerRepository.save(offer);

        // Reject every other still-pending offer on the same listing — a
        // listing is one lot of produce and can only be sold once.
        for (Offer other : offerRepository.findByListing_IdOrderByCreatedAtDesc(listing.getId())) {
            if (!other.getId().equals(offer.getId()) && other.getStatus() == OfferStatus.PENDING) {
                other.setStatus(OfferStatus.REJECTED);
                other.setDecidedAt(Instant.now());
                offerRepository.save(other);
            }
        }

        listing.setStatus(ListingStatus.CLOSED);
        listing.setClosedAt(Instant.now());

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
        MarketOrder saved = orderRepository.save(order);

        // Immediately hand the confirmed trade to the existing internal
        // Plot Selection queue. The internal state machine then owns every
        // operational stage from Plot through delivery/invoicing.
        marketplaceWorkflowService.createInternalCase(saved);

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

    private void requireListingOwner(Offer offer, User seller) {
        requireListingOwner(offer.getListing(), seller);
    }

    private void requireListingOwner(ProduceListing listing, User seller) {
        if (!listing.getSeller().getId().equals(seller.getId())) {
            throw new ForbiddenRoleException("You do not own the listing this offer was made on.");
        }
    }
}
