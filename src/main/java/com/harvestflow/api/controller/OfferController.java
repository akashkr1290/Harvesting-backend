package com.harvestflow.api.controller;

import com.harvestflow.api.dto.marketplace.OfferResponse;
import com.harvestflow.api.dto.marketplace.OrderResponse;
import com.harvestflow.api.dto.marketplace.SubmitOfferRequest;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.OfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    // ---------------------------------------------------------------
    // Buyer
    // ---------------------------------------------------------------

    @PostMapping("/listings/{listingId}/offers")
    @PreAuthorize("hasAnyRole('CONSUMER','BULK_BUYER')")
    public OfferResponse submit(@AuthenticationPrincipal SecurityUser principal,
                                 @PathVariable UUID listingId,
                                 @Valid @RequestBody SubmitOfferRequest request) {
        return OfferResponse.from(offerService.submit(principal.getUser(), listingId, request));
    }

    @GetMapping("/offers/mine")
    @PreAuthorize("hasAnyRole('CONSUMER','BULK_BUYER')")
    public List<OfferResponse> myOffers(@AuthenticationPrincipal SecurityUser principal) {
        return offerService.myOffers(principal.getUser().getId()).stream().map(OfferResponse::from).toList();
    }

    @PostMapping("/offers/{offerId}/withdraw")
    @PreAuthorize("hasAnyRole('CONSUMER','BULK_BUYER')")
    public OfferResponse withdraw(@AuthenticationPrincipal SecurityUser principal, @PathVariable UUID offerId) {
        return OfferResponse.from(offerService.withdraw(offerId, principal.getUser()));
    }

    // ---------------------------------------------------------------
    // Farmer/FPO
    // ---------------------------------------------------------------

    @GetMapping("/listings/{listingId}/offers")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public List<OfferResponse> forListing(@PathVariable UUID listingId) {
        return offerService.forListing(listingId).stream().map(OfferResponse::from).toList();
    }

    /** Every offer across every listing this farmer/FPO owns — feeds the offer-management screen. */
    @GetMapping("/offers/received")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public List<OfferResponse> received(@AuthenticationPrincipal SecurityUser principal) {
        return offerService.offersOnMyListings(principal.getUser().getId()).stream()
                .map(OfferResponse::from).toList();
    }

    @PostMapping("/offers/{offerId}/accept")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public OrderResponse accept(@AuthenticationPrincipal SecurityUser principal, @PathVariable UUID offerId) {
        return OrderResponse.from(offerService.accept(offerId, principal.getUser()));
    }

    @PostMapping("/offers/{offerId}/reject")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public OfferResponse reject(@AuthenticationPrincipal SecurityUser principal, @PathVariable UUID offerId) {
        return OfferResponse.from(offerService.reject(offerId, principal.getUser()));
    }
}
