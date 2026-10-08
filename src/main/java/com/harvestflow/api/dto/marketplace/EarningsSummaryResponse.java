package com.harvestflow.api.dto.marketplace;

import java.util.List;

/**
 * Basic farmer/FPO earnings view (Phase 2) — computed directly from this
 * seller's own MarketOrder rows, never invented or estimated.
 */
public record EarningsSummaryResponse(
        Double totalEarnings,
        Long completedOrders,
        Long confirmedOrders,
        List<OrderResponse> recentOrders,
        /** Delivered (COMPLETED) orders: money the farmer has actually earned. */
        Double receivedAmount,
        /** Accepted but not yet delivered: money still on its way. */
        Double expectedAmount
) {}
