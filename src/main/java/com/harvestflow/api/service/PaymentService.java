package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.PaymentRecord;
import com.harvestflow.api.domain.entity.MarketOrder;
import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.domain.enums.PaymentReferenceType;
import com.harvestflow.api.domain.enums.PaymentStatus;
import com.harvestflow.api.dto.payment.PaymentResponse;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.MarketOrderRepository;
import com.harvestflow.api.repository.DeliveryAssignmentRepository;
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
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;
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


    /** A paid or cancelled order must never be charged again or switched back to PENDING. */
    private void assertPayable(MarketOrder order) {
        if (order.getPaymentStatus() == PaymentStatus.SUCCEEDED) {
            throw new com.harvestflow.api.exception.InvalidMarketplaceStateException("This order is already paid.");
        }
        if (order.getStatus() == com.harvestflow.api.domain.enums.OrderStatus.CANCELLED) {
            throw new com.harvestflow.api.exception.InvalidMarketplaceStateException("This order was cancelled.");
        }
    }

    @Transactional
    public PaymentResponse initiateDigitalMarketOrder(UUID orderId, User buyer) {
        MarketOrder order = marketOrderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getBuyer().getId().equals(buyer.getId())) throw new com.harvestflow.api.exception.ForbiddenRoleException("Only the buyer can initiate payment.");
        assertPayable(order);
        PaymentRecord payment = new PaymentRecord();
        payment.setReferenceType(PaymentReferenceType.MARKET_ORDER);
        payment.setReferenceId(orderId);
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency("INR");
        payment.setPaymentMethod(com.harvestflow.api.domain.enums.PaymentMethod.DIGITAL);
        payment.setInitiatedByUsername(buyer.getUsername());
        payment.setProvider(paymentGatewayService.providerId());
        PaymentGatewayResult result = paymentGatewayService.createPaymentIntent(order.getTotalAmount(), "INR", orderId);
        payment.setStatus(result.status());
        payment.setGatewayReference(result.gatewayReference());
        payment.setPaymentUrl(result.paymentUrl());
        payment.setFailureReason(result.failureReason());
        if (result.status() != PaymentStatus.PENDING) payment.setCompletedAt(Instant.now());
        PaymentRecord saved = paymentRecordRepository.save(payment);
        order.setPaymentMethod(com.harvestflow.api.domain.enums.PaymentMethod.DIGITAL);
        order.setPaymentStatus(saved.getStatus());
        marketOrderRepository.save(order);
        if (saved.getStatus() == PaymentStatus.SUCCEEDED) {
            notificationService.notify(order.getBuyer().getUsername(), NotificationCategory.PAYMENT, "Payment confirmed",
                    "Your UPI payment for " + order.getCropName() + " was received.", "MARKET_ORDER", orderId);
            notificationService.notify(order.getSeller().getUsername(), NotificationCategory.PAYMENT, "Buyer has paid",
                    "The buyer paid for your " + order.getCropName() + ". Money is released to you after delivery.", "MARKET_ORDER", orderId);
        }
        return PaymentResponse.from(saved);
    }

    @Transactional
    public PaymentResponse selectCashMarketOrder(UUID orderId, User buyer) {
        MarketOrder order = marketOrderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!order.getBuyer().getId().equals(buyer.getId())) throw new com.harvestflow.api.exception.ForbiddenRoleException("Only the buyer can select cash payment.");
        assertPayable(order);
        PaymentRecord payment = new PaymentRecord(); payment.setReferenceType(PaymentReferenceType.MARKET_ORDER); payment.setReferenceId(orderId); payment.setAmount(order.getTotalAmount()); payment.setCurrency("INR"); payment.setPaymentMethod(com.harvestflow.api.domain.enums.PaymentMethod.CASH); payment.setInitiatedByUsername(buyer.getUsername()); payment.setProvider("CASH"); payment.setStatus(PaymentStatus.PENDING); payment.setNote("Cash selected; payment is confirmed only when received by the delivery person.");
        PaymentRecord saved=paymentRecordRepository.save(payment); order.setPaymentMethod(com.harvestflow.api.domain.enums.PaymentMethod.CASH); order.setPaymentStatus(PaymentStatus.PENDING); marketOrderRepository.save(order); return PaymentResponse.from(saved);
    }

    @Transactional
    public PaymentResponse recordCashPayment(UUID orderId, User deliveryBoy) {
        MarketOrder order = marketOrderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        var assignment = deliveryAssignmentRepository.findByMarketOrderId(orderId).orElseThrow(() -> new ResourceNotFoundException("Delivery assignment not found."));
        if (!deliveryBoy.getUsername().equals(assignment.getDeliveryBoyUsername())) throw new com.harvestflow.api.exception.ForbiddenRoleException("This delivery is assigned to another delivery person.");
        if (!assignment.isPickupConfirmed() || !assignment.isBuyerReached()) throw new com.harvestflow.api.exception.InvalidTransitionException("Cash can be confirmed only after pickup and reaching the buyer.");
        assertPayable(order);
        PaymentRecord payment = new PaymentRecord();
        payment.setReferenceType(PaymentReferenceType.MARKET_ORDER);
        payment.setReferenceId(orderId);
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency("INR");
        payment.setPaymentMethod(com.harvestflow.api.domain.enums.PaymentMethod.CASH);
        payment.setInitiatedByUsername(deliveryBoy.getUsername());
        payment.setProvider("CASH");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setCompletedAt(Instant.now());
        payment.setNote("Cash received by assigned delivery person.");
        PaymentRecord saved = paymentRecordRepository.save(payment);
        order.setPaymentMethod(com.harvestflow.api.domain.enums.PaymentMethod.CASH);
        order.setPaymentStatus(PaymentStatus.SUCCEEDED);
        marketOrderRepository.save(order);
        notificationService.notify(order.getBuyer().getUsername(), NotificationCategory.PAYMENT, "Payment confirmed", "Cash payment was recorded for your order.", "MARKET_ORDER", orderId);
        notificationService.notify(order.getSeller().getUsername(), NotificationCategory.PAYMENT, "Payment confirmed", "Payment was recorded for your marketplace order.", "MARKET_ORDER", orderId);
        return PaymentResponse.from(saved);
    }

    @Transactional
    public PaymentResponse confirmDigitalWebhook(UUID orderId, String gatewayReference, String payload, String signature) {
        MarketOrder order = marketOrderRepository.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!paymentGatewayService.verifyWebhook(gatewayReference, orderId.toString(), payload, signature)) {
            throw new IllegalArgumentException("Payment gateway verification failed.");
        }
        if (order.getPaymentStatus() == PaymentStatus.SUCCEEDED) {
            return paymentRecordRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(PaymentReferenceType.MARKET_ORDER, orderId)
                    .stream().filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED).findFirst()
                    .map(PaymentResponse::from)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment record not found."));
        }
        PaymentRecord payment = paymentRecordRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(PaymentReferenceType.MARKET_ORDER, orderId)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Payment intent not found."));
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setGatewayReference(gatewayReference);
        payment.setCompletedAt(Instant.now());
        PaymentRecord saved = paymentRecordRepository.save(payment);
        order.setPaymentStatus(PaymentStatus.SUCCEEDED);
        marketOrderRepository.save(order);
        notificationService.notify(order.getBuyer().getUsername(), NotificationCategory.PAYMENT, "Payment confirmed", "Digital payment was verified by the gateway.", "MARKET_ORDER", orderId);
        notificationService.notify(order.getSeller().getUsername(), NotificationCategory.PAYMENT, "Payment confirmed", "Digital payment was verified for your marketplace order.", "MARKET_ORDER", orderId);
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
