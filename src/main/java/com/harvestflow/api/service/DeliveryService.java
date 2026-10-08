package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.*;
import com.harvestflow.api.domain.enums.*;
import com.harvestflow.api.dto.delivery.*;
import com.harvestflow.api.exception.*;
import com.harvestflow.api.repository.*;
import com.harvestflow.api.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryAssignmentRepository assignments;
    private final DeliveryOtpRepository otps;
    private final MarketOrderRepository orders;
    private final HarvestCaseRepository cases;
    private final PaymentService payments;
    private final PaymentRecordRepository paymentRecords;
    private final NotificationService notifications;
    private final PasswordEncoder encoder;
    private final DeliveryOtpVerifier otpVerifier;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public DeliveryAssignmentResponse assign(UUID orderId, String deliveryBoyUsername, User actor) {
        if (actor.getRole() != UserRole.PICKUP_PERSON && actor.getRole() != UserRole.ADMIN) {
            throw new ForbiddenRoleException("Only delivery operations staff can assign delivery.");
        }
        MarketOrder o = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (o.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidTransitionException("Only a confirmed order can be assigned for delivery.");
        }
        DeliveryAssignment d = assignments.findByMarketOrderId(orderId).orElseGet(DeliveryAssignment::new);
        d.setMarketOrder(o);
        d.setDeliveryBoyUsername(deliveryBoyUsername);
        d.setStatus(DeliveryStatus.ASSIGNED);
        DeliveryAssignment saved = assignments.save(d);
        notifications.notify(o.getSeller().getUsername(), NotificationCategory.GENERAL, "Pickup scheduled",
                "A vehicle is assigned to collect your " + o.getCropName() + ". Keep it packed and ready.", "MARKET_ORDER", orderId);
        return DeliveryAssignmentResponse.from(saved);
    }

    public List<DeliveryAssignmentResponse> assignedTo(User actor) {
        if (actor.getRole() != UserRole.PICKUP_PERSON && actor.getRole() != UserRole.ADMIN) {
            throw new ForbiddenRoleException("Delivery access denied.");
        }
        return assignments.findByDeliveryBoyUsernameOrderByAssignedAtDesc(actor.getUsername()).stream()
                .map(DeliveryAssignmentResponse::from).toList();
    }

    @Transactional
    public DeliveryAssignmentResponse confirmPickup(UUID orderId, User actor) {
        DeliveryAssignment d = forBoy(orderId, actor);
        d.setPickupConfirmed(true);
        d.setStatus(DeliveryStatus.PICKED_UP);
        d.setPickedUpAt(Instant.now());
        MarketOrder o = d.getMarketOrder();
        notifications.notify(o.getSeller().getUsername(), NotificationCategory.GENERAL, "Crop picked up",
                "Your " + o.getCropName() + " has been picked up and is on the way to the buyer.", "MARKET_ORDER", orderId);
        notifications.notify(o.getBuyer().getUsername(), NotificationCategory.GENERAL, "Order on the way",
                "Your " + o.getCropName() + " has been picked up from the farm.", "MARKET_ORDER", orderId);
        return DeliveryAssignmentResponse.from(assignments.save(d));
    }

    @Transactional
    public DeliveryAssignmentResponse reachBuyer(UUID orderId, User actor) {
        DeliveryAssignment d = forBoy(orderId, actor);
        if (!d.isPickupConfirmed()) throw new InvalidTransitionException("Pickup must be confirmed first.");
        d.setBuyerReached(true);
        d.setStatus(DeliveryStatus.AT_BUYER);
        d.setBuyerReachedAt(Instant.now());
        return DeliveryAssignmentResponse.from(assignments.save(d));
    }

    @Transactional
    public String generateOtp(UUID orderId, User buyer) {
        MarketOrder o = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!o.getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenRoleException("Only the buyer can generate delivery OTP.");
        }
        DeliveryOtp x = otps.findByMarketOrderId(orderId).orElseGet(DeliveryOtp::new);
        String otp = String.format("%06d", random.nextInt(1_000_000));
        x.setMarketOrder(o);
        x.setOtpHash(encoder.encode(otp));
        x.setExpiresAt(Instant.now().plusSeconds(600));
        x.setConsumedAt(null);
        x.setAttemptCount(0);
        otps.save(x);
        notifications.notify(buyer.getUsername(), NotificationCategory.PAYMENT, "Delivery OTP generated",
                "Use the delivery OTP at handover. It expires in 10 minutes.", "MARKET_ORDER", orderId);
        return otp;
    }

    @Transactional
    public DeliveryAssignmentResponse complete(UUID orderId, String otp, User actor) {
        DeliveryAssignment d = forBoy(orderId, actor);
        if (!d.isBuyerReached()) throw new InvalidTransitionException("Delivery can be completed only after reaching the buyer.");
        MarketOrder o = d.getMarketOrder();
        if (o.getPaymentStatus() != PaymentStatus.SUCCEEDED) {
            throw new InvalidTransitionException("Payment must be PAID before delivery completion.");
        }
        assertInternalWorkflowAllowsCompletion(o);
        // Checked last: it commits the attempt counter in its own transaction.
        otpVerifier.verifyAndConsume(orderId, otp);
        d.setStatus(DeliveryStatus.COMPLETED);
        d.setCompletedAt(Instant.now());
        o.setStatus(OrderStatus.COMPLETED);
        o.setDeliveredAt(Instant.now());
        orders.save(o);
        notifications.notify(o.getBuyer().getUsername(), NotificationCategory.PAYMENT, "Delivery completed",
                "Your order has been delivered and payment is confirmed.", "MARKET_ORDER", orderId);
        notifications.notify(o.getSeller().getUsername(), NotificationCategory.PAYMENT, "Delivery completed",
                "Your order has been delivered and payment is confirmed.", "MARKET_ORDER", orderId);
        return DeliveryAssignmentResponse.from(assignments.save(d));
    }

    /** Orders that run through the internal HarvestFlow case must finish it before handover. */
    private void assertInternalWorkflowAllowsCompletion(MarketOrder o) {
        if (o.getFulfilmentMode() == FulfilmentMode.DIRECT) {
            return; // direct orders never had an internal case
        }
        boolean blocked = cases.findByMarketOrderId(o.getId())
                .map(c -> c.getStatus() != CaseStatus.SALES_COMPLETED)
                .orElse(true);
        if (blocked) {
            throw new InvalidMarketplaceStateException(
                    "Existing HarvestFlow internal workflow must reach SALES_COMPLETED before final delivery completion.");
        }
    }

    public ReceiptResponse receipt(UUID orderId, User actor) {
        MarketOrder o = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        if (!o.getBuyer().getId().equals(actor.getId()) && !o.getSeller().getId().equals(actor.getId())) {
            throw new ForbiddenRoleException("Receipt access denied.");
        }
        if (o.getStatus() != OrderStatus.COMPLETED) {
            throw new InvalidTransitionException("Receipt is available after delivery completion.");
        }
        String ref = paymentRecords.findByReferenceTypeAndReferenceIdOrderByCreatedAtDesc(PaymentReferenceType.MARKET_ORDER, orderId)
                .stream().filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .findFirst().map(PaymentRecord::getGatewayReference).orElse(null);
        return ReceiptResponse.from(o, ref);
    }

    private DeliveryAssignment forBoy(UUID id, User actor) {
        DeliveryAssignment d = assignments.findByMarketOrderId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery assignment not found."));
        if (actor.getRole() != UserRole.ADMIN && !actor.getUsername().equals(d.getDeliveryBoyUsername())) {
            throw new ForbiddenRoleException("This delivery is assigned to another delivery person.");
        }
        return d;
    }
}
