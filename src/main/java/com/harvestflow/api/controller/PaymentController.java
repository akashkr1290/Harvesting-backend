package com.harvestflow.api.controller;

import com.harvestflow.api.domain.enums.PaymentReferenceType;
import com.harvestflow.api.dto.payment.InitiatePaymentRequest;
import com.harvestflow.api.dto.payment.PaymentResponse;
import com.harvestflow.api.security.SecurityUser;
import com.harvestflow.api.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Mock payment recording against an existing PurchaseInvoice (farmer
 * payout) or SalesInvoice (buyer-side sale). No real payment gateway is
 * integrated — see {@code MockPaymentGatewayService} — every result here
 * is simulated. Restricted to {@code PURCHASE_ACCOUNT}/{@code ADMIN}, the
 * roles that already handle invoices/money in the existing workflow.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PURCHASE_ACCOUNT', 'ADMIN')")
    public PaymentResponse initiate(@Valid @RequestBody InitiatePaymentRequest request,
                                     @AuthenticationPrincipal SecurityUser principal) {
        return paymentService.initiate(request.referenceType(), request.referenceId(), request.amount(),
                request.currency(), request.note(), principal.getUsername());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('PURCHASE_ACCOUNT', 'ADMIN')")
    public PaymentResponse getById(@PathVariable UUID id) {
        return paymentService.getById(id);
    }

    @GetMapping("/reference/{referenceType}/{referenceId}")
    @PreAuthorize("hasAnyRole('PURCHASE_ACCOUNT', 'ADMIN')")
    public List<PaymentResponse> getForReference(@PathVariable PaymentReferenceType referenceType,
                                                  @PathVariable UUID referenceId) {
        return paymentService.getForReference(referenceType, referenceId);
    }

    @PostMapping("/market-orders/{orderId}/digital")
    @PreAuthorize("hasAnyRole('CONSUMER','BULK_BUYER')")
    public PaymentResponse digital(@PathVariable UUID orderId, @AuthenticationPrincipal SecurityUser principal) { return paymentService.initiateDigitalMarketOrder(orderId, principal.getUser()); }

    @PostMapping("/market-orders/{orderId}/cash")
    @PreAuthorize("hasRole('PICKUP_PERSON')")
    public PaymentResponse cash(@PathVariable UUID orderId, @AuthenticationPrincipal SecurityUser principal) { return paymentService.recordCashPayment(orderId, principal.getUser()); }

    @PostMapping("/webhook/{orderId}")
    public PaymentResponse webhook(@PathVariable UUID orderId, @RequestHeader(value="X-Payment-Signature", required=false) String signature,
                                   @RequestHeader(value="X-Gateway-Reference", required=false) String gatewayReference,
                                   @RequestBody(required=false) String payload) { return paymentService.confirmDigitalWebhook(orderId, gatewayReference, payload, signature); }

    @PostMapping("/market-orders/{orderId}/cash/select")
    @PreAuthorize("hasAnyRole('CONSUMER','BULK_BUYER')")
    public PaymentResponse selectCash(@PathVariable UUID orderId, @AuthenticationPrincipal SecurityUser principal) { return paymentService.selectCashMarketOrder(orderId, principal.getUser()); }
}
