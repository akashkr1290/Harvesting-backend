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
import com.harvestflow.api.repository.HarvestCaseRepository;
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
    private final HarvestCaseRepository harvestCaseRepository;

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

    public OrderResponse responseFor(MarketOrder order) {
        var harvestCase = harvestCaseRepository.findByMarketOrderId(order.getId()).orElse(null);
        return OrderResponse.from(order, harvestCase);
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
        List<OrderResponse> recent = orders.stream().limit(10).map(this::responseFor).toList();
        return new EarningsSummaryResponse(total, completed, confirmed, recent);
    }

    /**
     * Legacy settlement endpoint retained for compatibility, but it can no
     * longer bypass the internal workflow. Only an internal account may use
     * it, and the linked case must already be at the terminal SALES_COMPLETED
     * stage. New UI flow settles automatically when that stage is completed.
     */
    @Transactional
    public OrderResponse completeDeliveryAndSettle(UUID orderId, User requester) {
        if (requester.getRole() != com.harvestflow.api.domain.enums.UserRole.ADMIN
                && requester.getRole() != com.harvestflow.api.domain.enums.UserRole.PURCHASE_ACCOUNT) {
            throw new ForbiddenRoleException("Delivery settlement is an internal operation.");
        }
        MarketOrder order = getById(orderId);
        var harvestCase = harvestCaseRepository.findByMarketOrderId(orderId).orElseThrow(
                () -> new ResourceNotFoundException("No internal harvest case is linked to this order."));
        if (harvestCase.getStatus() != com.harvestflow.api.domain.enums.CaseStatus.SALES_COMPLETED) {
            throw new InvalidMarketplaceStateException("Delivery/payment cannot be completed until the internal HarvestFlow workflow is finished.");
        }
        if (order.getStatus() == OrderStatus.COMPLETED) {
            return OrderResponse.from(order, harvestCase);
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setDeliveredAt(java.time.Instant.now());
        MarketOrder saved = orderRepository.save(order);
        paymentService.initiate(PaymentReferenceType.MARKET_ORDER, saved.getId(), saved.getTotalAmount(),
                "INR", "Marketplace order settlement", requester.getUsername());
        return OrderResponse.from(saved, harvestCase);
    }
}
