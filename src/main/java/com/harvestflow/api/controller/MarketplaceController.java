package com.harvestflow.api.controller;

import com.harvestflow.api.dto.marketplace.*;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.MarketplaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace/listings")
@RequiredArgsConstructor
public class MarketplaceController {

    private final MarketplaceService marketplaceService;

    // ---------------------------------------------------------------
    // Buyer marketplace — published listings only
    // ---------------------------------------------------------------

    /** Search/filter across published listings (SOW Phase 2 — Buyer marketplace). */
    @GetMapping
    @PreAuthorize("hasAnyRole('CONSUMER','BULK_BUYER','FARMER','FPO','ADMIN')")
    public List<ListingSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String cropName,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice
    ) {
        return marketplaceService.searchMarketplace(q, location, cropName, minPrice, maxPrice).stream()
                .map(ListingSummaryResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ListingDetailResponse detail(@PathVariable UUID id) {
        return ListingDetailResponse.from(marketplaceService.getById(id));
    }

    // ---------------------------------------------------------------
    // Farmer/FPO — own listings
    // ---------------------------------------------------------------

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public List<ListingSummaryResponse> mine(@AuthenticationPrincipal SecurityUser principal) {
        return marketplaceService.myListings(principal.getUser().getId()).stream()
                .map(ListingSummaryResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public ListingDetailResponse create(@AuthenticationPrincipal SecurityUser principal,
                                         @Valid @RequestBody CreateListingRequest request) {
        return ListingDetailResponse.from(marketplaceService.create(principal.getUser(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public ListingDetailResponse update(@AuthenticationPrincipal SecurityUser principal,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody UpdateListingRequest request) {
        return ListingDetailResponse.from(marketplaceService.update(id, principal.getUser(), request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public ListingDetailResponse publish(@AuthenticationPrincipal SecurityUser principal, @PathVariable UUID id) {
        return ListingDetailResponse.from(marketplaceService.publish(id, principal.getUser()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public ListingDetailResponse close(@AuthenticationPrincipal SecurityUser principal, @PathVariable UUID id) {
        return ListingDetailResponse.from(marketplaceService.close(id, principal.getUser()));
    }

    /** Attaches image URLs already uploaded via the existing /api/uploads flow. */
    @PostMapping("/{id}/images")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public ListingDetailResponse addImages(@AuthenticationPrincipal SecurityUser principal,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody AddListingImagesRequest request) {
        return ListingDetailResponse.from(marketplaceService.addImages(id, principal.getUser(), request));
    }
}
