package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.ProduceListing;
import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.ListingStatus;
import com.harvestflow.api.domain.enums.UserRole;
import com.harvestflow.api.dto.marketplace.AddListingImagesRequest;
import com.harvestflow.api.dto.marketplace.CreateListingRequest;
import com.harvestflow.api.dto.marketplace.UpdateListingRequest;
import com.harvestflow.api.exception.ForbiddenRoleException;
import com.harvestflow.api.exception.InvalidMarketplaceStateException;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.domain.entity.Offer;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.domain.enums.OfferStatus;
import com.harvestflow.api.repository.OfferRepository;
import com.harvestflow.api.repository.ProduceListingRepository;
import com.harvestflow.api.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Farmer/FPO listing lifecycle (Phase 2): create as DRAFT, edit while
 * DRAFT, publish, close. Buyers only ever see PUBLISHED listings —
 * enforced in {@link com.harvestflow.api.repository.ProduceListingRepository#searchPublished}.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final ProduceListingRepository listingRepository;
    private final ProduceImageService produceImageService;
    private final OfferRepository offerRepository;
    private final NotificationService notificationService;

    // ---------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------

    public ProduceListing getById(UUID id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found: " + id));
    }

    @Transactional
    public ProduceListing getByIdForUpdate(UUID id) {
        return listingRepository.findLockedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found: " + id));
    }

    public List<ProduceListing> myListings(UUID sellerId) {
        return listingRepository.findBySeller_IdOrderByCreatedAtDesc(sellerId);
    }

    public List<ProduceListing> searchMarketplace(String query, String location, String cropName,
                                                    Double minPrice, Double maxPrice) {
        return listingRepository.searchPublished(query, location, cropName, minPrice, maxPrice);
    }

    // ---------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------

    @Transactional
    public ProduceListing create(User seller, CreateListingRequest req) {
        requireSellerRole(seller);

        ProduceListing listing = new ProduceListing();
        listing.setSeller(seller);
        listing.setSellerRole(seller.getRole());
        listing.setSellerName(seller.getName());
        applyFields(listing, req.cropName(), req.quantity(), req.unit(), req.quality(),
                req.harvestDate(), req.location(), req.expectedPricePerUnit(), req.description());
        listing.setStatus(ListingStatus.DRAFT);
        return listingRepository.save(listing);
    }

    @Transactional
    public ProduceListing update(UUID listingId, User requester, UpdateListingRequest req) {
        ProduceListing listing = getById(listingId);
        requireOwner(listing, requester);
        if (listing.getStatus() != ListingStatus.DRAFT) {
            throw new InvalidMarketplaceStateException("Only a draft listing can be edited — close it and create a new one instead.");
        }
        applyFields(listing, req.cropName(), req.quantity(), req.unit(), req.quality(),
                req.harvestDate(), req.location(), req.expectedPricePerUnit(), req.description());
        return listingRepository.save(listing);
    }

    @Transactional
    public ProduceListing publish(UUID listingId, User requester) {
        ProduceListing listing = getById(listingId);
        requireOwner(listing, requester);
        if (listing.getStatus() != ListingStatus.DRAFT) {
            throw new InvalidMarketplaceStateException("Only a draft listing can be published.");
        }
        // Phase 5: enforce Part 9/Step 9 of the master spec — a listing whose
        // AI-assisted image review reached a REJECT decision must never be
        // silently published as "verified". A listing with no
        // Phase-4-verified photos yet (none uploaded via the produce-images
        // API — only legacy imageUrls) is NOT blocked here: requiring at
        // least one verified photo before publish is a product decision for
        // a future phase, not something this integration invents.
        if (!produceImageService.hasPublishableVerification(listingId)) {
            throw new InvalidMarketplaceStateException(
                    "Upload at least one crop photo and wait for an ACCEPT verification decision before publishing.");
        }
        listing.setStatus(ListingStatus.PUBLISHED);
        listing.setPublishedAt(Instant.now());
        return listingRepository.save(listing);
    }

    @Transactional
    public ProduceListing close(UUID listingId, User requester) {
        ProduceListing listing = getById(listingId);
        requireOwner(listing, requester);
        if (listing.getStatus() == ListingStatus.CLOSED) {
            throw new InvalidMarketplaceStateException("Listing is already closed.");
        }
        listing.setStatus(ListingStatus.CLOSED);
        listing.setClosedAt(Instant.now());
        listing.setAvailableQuantity(0.0);
        // Open offers can no longer be accepted: tell those buyers instead of leaving them PENDING forever.
        for (Offer offer : offerRepository.findByListing_IdOrderByCreatedAtDesc(listingId)) {
            if (offer.getStatus() == OfferStatus.PENDING) {
                offer.setStatus(OfferStatus.REJECTED);
                offer.setDecidedAt(Instant.now());
                offerRepository.save(offer);
                notificationService.notify(offer.getBuyer().getUsername(), NotificationCategory.OFFER,
                        "Listing closed",
                        "The seller closed the " + listing.getCropName() + " listing, so your offer was not accepted.",
                        "OFFER", offer.getId());
            }
        }
        return listingRepository.save(listing);
    }

    /**
     * Attaches already-uploaded image URLs (via the existing
     * {@code /api/uploads} flow) to a listing. Phase 2 owns display only —
     * {@code verificationStatus} stays at its default until Phase 4 wires
     * in the real authenticity model.
     */
    @Transactional
    public ProduceListing addImages(UUID listingId, User requester, AddListingImagesRequest req) {
        ProduceListing listing = getById(listingId);
        requireOwner(listing, requester);
        listing.getImageUrls().addAll(req.imageUrls());
        if (listing.getPrimaryImageIndex() == null && !listing.getImageUrls().isEmpty()) {
            listing.setPrimaryImageIndex(0);
        }
        return listingRepository.save(listing);
    }

    // ---------------------------------------------------------------
    // Guards
    // ---------------------------------------------------------------

    private void requireSellerRole(User user) {
        if (user.getRole() != UserRole.FARMER && user.getRole() != UserRole.FPO) {
            throw new ForbiddenRoleException("Only a Farmer or FPO account can create a listing.");
        }
    }

    private void requireOwner(ProduceListing listing, User requester) {
        if (!listing.getSeller().getId().equals(requester.getId())) {
            throw new ForbiddenRoleException("You do not own this listing.");
        }
    }

    private void applyFields(ProduceListing listing, String cropName, Double quantity, String unit,
                              String quality, java.time.LocalDate harvestDate, String location,
                              Double expectedPricePerUnit, String description) {
        listing.setCropName(cropName);
        listing.setQuantity(quantity);
        listing.setAvailableQuantity(quantity);
        listing.setUnit(unit);
        listing.setQuality(quality);
        listing.setHarvestDate(harvestDate);
        listing.setLocation(location);
        listing.setExpectedPricePerUnit(expectedPricePerUnit);
        listing.setDescription(description);
    }
}
