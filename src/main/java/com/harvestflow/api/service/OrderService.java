package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.MarketOrder;
import com.harvestflow.api.domain.entity.User;
import com.harvestflow.api.domain.enums.NotificationCategory;
import com.harvestflow.api.domain.enums.OrderStatus;
import com.harvestflow.api.domain.enums.PaymentReferenceType;
import com.harvestflow.api.dto.marketplace.EarningsSummaryResponse;
import com.harvestflow.api.dto.marketplace.OrderResponse;
import com.harvestflow.api.exception.ForbiddenRoleException;
import com.harvestflow.api.exception.InvalidMarketplaceStateException;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.MarketOrderRepository;
import com.harvestflow.api.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final MarketOrderRepository orderRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;

    public MarketOrder getById(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
    }

    /** Orders visible to this user, whichever side of the trade they were on. */
    public List<MarketOrder> ordersFor(User user) {
        List<MarketOrder> asSeller = orderRepository.findBySeller_IdOrderByCreatedAtDesc(user.getId());
        List<MarketOrder> asBuyer = orderRepository.findByBuyer_IdOrderByCreatedAtDesc(user.getId());
        if (asSeller.isEmpty()) return asBuyer;
        if (asBuyer.isEmpty()) return asSeller;
        return java.util.stream.Stream.concat(asSeller.stream(), asBuyer.stream())
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    public MarketOrder getVisibleTo(UUID orderId, User user) {
        MarketOrder order = getById(orderId);
        boolean isParty = order.getSeller().getId().equals(user.getId())
                || order.getBuyer().getId().equals(user.getId());
        if (!isParty) {
            throw new ForbiddenRoleException("You are not a party to this order.");
        }
        return order;
    }

    /**
     * Farmer/FPO earnings — computed directly from this seller's own
     * MarketOrder rows. Never estimates or invents figures; a CONFIRMED
     * order's amount counts toward totalEarnings the same as a COMPLETED
     * one, since the price was already agreed at acceptance.
     */
    public EarningsSummaryResponse earningsFor(UUID sellerId) {
        List<MarketOrder> orders = orderRepository.findBySeller_IdOrderByCreatedAtDesc(sellerId);
        double total = orders.stream()
                .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
                .mapToDouble(MarketOrder::getTotalAmount)
                .sum();
        long completed = orders.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();
        long confirmed = orders.stream().filter(o -> o.getStatus() == OrderStatus.CONFIRMED).count();
        List<OrderResponse> recent = orders.stream().limit(10).map(OrderResponse::from).toList();
        return new EarningsSummaryResponse(total, completed, confirmed, recent);
    }

    /**
     * Phase 5: completes Part 39 (demo story) steps 17-20 — "Delivery can be
     * completed" / "payment is simulated/completed" / "farmer earnings are
     * calculated" — which none of Phases 1-4 wired end-to-end on their own
     * (Phase 2 never had a delivery/payment step; Phase 4's payment module
     * only knew about invoices, not orders — see PaymentReferenceType).
     * Either party to the order may confirm delivery; a real deployment
     * would gate this on Phase 3's logistics/route-completion event instead
     * of a bare status flip, but no such "delivery completed" webhook or
     * endpoint exists in any of the four phases, so this is the honest,
     * minimal wiring — documented as a known limitation in the final report.
     */
    @Transactional
    public OrderResponse completeDeliveryAndSettle(UUID orderId, User requester) {
        MarketOrder order = getVisibleTo(orderId, requester);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new InvalidMarketplaceStateException("Only a confirmed order can be marked delivered.");
        }
        order.setStatus(OrderStatus.COMPLETED);
        MarketOrder saved = orderRepository.save(order);

        // Simulated (never real-money) settlement — see PaymentGatewayService/MockPaymentGatewayService.
        paymentService.initiate(PaymentReferenceType.MARKET_ORDER, saved.getId(), saved.getTotalAmount(),
                "INR", "Order settlement for " + saved.getCropName() + " (" + saved.getQuantity() + " units)",
                requester.getUsername());

        notificationService.notify(saved.getSeller().getUsername(), NotificationCategory.PAYMENT,
                "Order delivered and paid",
                "Your order for " + saved.getQuantity() + " " + saved.getCropName()
                        + " was delivered and payment was released (simulated).",
                "MARKET_ORDER", saved.getId());
        notificationService.notify(saved.getBuyer().getUsername(), NotificationCategory.PAYMENT,
                "Delivery confirmed",
                "Delivery for your order of " + saved.getQuantity() + " " + saved.getCropName()
                        + " was confirmed and payment was released (simulated).",
                "MARKET_ORDER", saved.getId());

        return OrderResponse.from(saved);
    }
}
