package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.Offer;
import com.harvestflow.api.domain.enums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findByListing_IdOrderByCreatedAtDesc(UUID listingId);

    List<Offer> findByBuyer_IdOrderByCreatedAtDesc(UUID buyerId);

    /** All pending offers on any listing owned by this seller — feeds the farmer's offer-management screen. */
    List<Offer> findByListing_Seller_IdAndStatusOrderByCreatedAtDesc(UUID sellerId, OfferStatus status);

    List<Offer> findByListing_Seller_IdOrderByCreatedAtDesc(UUID sellerId);
}
