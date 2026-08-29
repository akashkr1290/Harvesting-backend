package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.PaymentRecord;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.domain.enums.PaymentReferenceType;
import com.harvestflow.api.domain.enums.PaymentStatus;
import com.harvestflow.api.dto.payment.PaymentResponse;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.MarketOrderRepository;
import com.harvestflow.api.repository.PaymentRecordRepository;
import com.harvestflow.api.repository.PurchaseInvoiceRepository;
import com.harvestflow.api.repository.SalesInvoiceRepository;
import com.harvestflow.api.service.notification.NotificationService;
import com.harvestflow.api.service.payment.PaymentGatewayResult;
import com.harvestflow.api.service.payment.PaymentGatewayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Orchestrates a simulated payment attempt against an existing
 * PurchaseInvoice (farmer payout), SalesInvoice (buyer-side sale), or —
 * since Phase 5 — a marketplace {@code MarketOrder} settlement (see
 * {@code OrderService.completeDeliveryAndSettle}). Never touches real
 * money: {@link PaymentGatewayService} is always a mock/simulated
 * provider (see {@code MockPaymentGatewayService}) unless a real gateway
 * is explicitly configured and its credentials supplied out of band.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRecordRepository paymentRecordRepository;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final MarketOrderRepository marketOrderRepository;
    private final PaymentGatewayService paymentGatewayService;
    private final NotificationService notificationService;

    @Transactional
    public PaymentResponse initiate(PaymentReferenceType referenceType, UUID referenceId, double amount,
                                     String currency, String note, String initiatedByUsername) {
        assertReferenceExists(referenceType, referenceId);

        PaymentRecord payment = new PaymentRecord();
        payment.setReferenceType(referenceType);
        payment.setReferenceId(referenceId);
        payment.setAmount(amount);
        payment.setCurrency(currency == null || currency.isBlank() ? "INR" : currency.toUpperCase(Locale.ROOT));
        payment.setNote(note);
        payment.setInitiatedByUsername(initiatedByUsername);
        payment.setProvider(paymentGatewayService.providerId());
        payment.setStatus(PaymentStatus.PENDING);

        PaymentGatewayResult result = paymentGatewayService.charge(amount, payment.getCurrency(), referenceId);
        payment.setStatus(result.status());
        payment.setGatewayReference(result.gatewayReference());
        payment.setFailureReason(result.failureReason());
        payment.setCompletedAt(Instant.now());

        PaymentRecord saved = paymentRecordRepository.save(payment);

        String outcome = saved.getStatus() == PaymentStatus.SUCCEEDED ? "succeeded" : "failed";
        notificationService.notify(
                initiatedByUsername,
                NotificationCategory.PAYMENT,
                "Payment " + outcome,
                String.format(Locale.ROOT, "%s payment of %.2f %s for %s %s.",
                        saved.getStatus() == PaymentStatus.SUCCEEDED ? "Payment" : "Payment attempt",
                        amount, payment.getCurrency(), referenceType, referenceId)
                        + (saved.getFailureReason() != null ? " Reason: " + saved.getFailureReason() : ""),
                "PAYMENT_RECORD",
                saved.getId()
        );

        return PaymentResponse.from(saved);
    }

    public PaymentResponse getById(UUID id) {
        return PaymentResponse.from(paymentRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id)));
    }

    public List<PaymentResponse> getForReference(PaymentReferenceType referenceType, UUID referenceId) {
        return paymentRecordRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(referenceType, referenceId)
                .stream().map(PaymentResponse::from).toList();
    }

    private void assertReferenceExists(PaymentReferenceType referenceType, UUID referenceId) {
        boolean exists = switch (referenceType) {
            case PURCHASE_INVOICE -> purchaseInvoiceRepository.existsById(referenceId);
            case SALES_INVOICE -> salesInvoiceRepository.existsById(referenceId);
            // Phase 5: MARKET_ORDER added once MarketOrder (Phase 2) existed —
            // see PaymentReferenceType's javadoc.
            case MARKET_ORDER -> marketOrderRepository.existsById(referenceId);
        };
        if (!exists) {
            throw new ResourceNotFoundException(referenceType + " not found: " + referenceId);
        }
    }
}
