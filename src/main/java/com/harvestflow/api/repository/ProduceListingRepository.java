package com.harvestflow.api.repository;

import com.harvestflow.api.domain.entity.ProduceListing;
import com.harvestflow.api.domain.enums.ListingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProduceListingRepository extends JpaRepository<ProduceListing, UUID> {

    List<ProduceListing> findBySeller_IdOrderByCreatedAtDesc(UUID sellerId);

    /**
     * Buyer marketplace search/filter (SOW Phase 2) — only ever returns
     * PUBLISHED listings; a buyer never sees another seller's drafts.
     */
    @Query("""
            SELECT l FROM ProduceListing l
            WHERE l.status = com.harvestflow.api.domain.enums.ListingStatus.PUBLISHED
              AND (:query IS NULL OR :query = '' OR
                   LOWER(l.cropName) LIKE LOWER(CONCAT('%', :query, '%')) OR
                   LOWER(l.location) LIKE LOWER(CONCAT('%', :query, '%')) OR
                   LOWER(l.sellerName) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:location IS NULL OR :location = '' OR LOWER(l.location) = LOWER(:location))
              AND (:cropName IS NULL OR :cropName = '' OR LOWER(l.cropName) = LOWER(:cropName))
              AND (:minPrice IS NULL OR l.expectedPricePerUnit >= :minPrice)
              AND (:maxPrice IS NULL OR l.expectedPricePerUnit <= :maxPrice)
            ORDER BY l.createdAt DESC
            """)
    List<ProduceListing> searchPublished(
            @Param("query") String query,
            @Param("location") String location,
            @Param("cropName") String cropName,
            @Param("minPrice") Double minPrice,
            @Param("maxPrice") Double maxPrice
    );

    List<ProduceListing> findByStatus(ListingStatus status);
}
