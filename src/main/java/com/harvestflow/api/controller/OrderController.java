package com.harvestflow.api.controller;

import com.harvestflow.api.dto.marketplace.EarningsSummaryResponse;
import com.harvestflow.api.dto.marketplace.OrderResponse;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('FARMER','FPO','CONSUMER','BULK_BUYER')")
    public List<OrderResponse> myOrders(@AuthenticationPrincipal SecurityUser principal) {
        return orderService.ordersFor(principal.getUser()).stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('FARMER','FPO','CONSUMER','BULK_BUYER')")
    public OrderResponse detail(@AuthenticationPrincipal SecurityUser principal, @PathVariable UUID id) {
        return OrderResponse.from(orderService.getVisibleTo(id, principal.getUser()));
    }

    /** Farmer/FPO earnings summary (SOW Phase 2 — Farmer earnings summary). */
    @GetMapping("/earnings")
    @PreAuthorize("hasAnyRole('FARMER','FPO')")
    public EarningsSummaryResponse earnings(@AuthenticationPrincipal SecurityUser principal) {
        return orderService.earningsFor(principal.getUser().getId());
    }

    /**
     * Phase 5: confirms delivery on a confirmed order and releases the
     * simulated payment — Part 39 (demo story) steps 17-20. Either party to
     * the order may confirm; see {@code OrderService.completeDeliveryAndSettle}
     * javadoc for why this isn't gated on a real logistics event.
     */
    @PostMapping("/orders/{id}/complete-delivery")
    @PreAuthorize("hasAnyRole('FARMER','FPO','CONSUMER','BULK_BUYER')")
    public com.harvestflow.api.dto.marketplace.OrderResponse completeDelivery(
            @AuthenticationPrincipal SecurityUser principal, @PathVariable UUID id) {
        return orderService.completeDeliveryAndSettle(id, principal.getUser());
    }
}
