package com.harvestflow.api.controller;

import com.harvestflow.api.dto.analytics.OperationalStatsResponse;
import com.harvestflow.api.dto.analytics.SihImpactResponse;
import com.harvestflow.api.dto.analytics.VerificationStatsResponse;
import com.harvestflow.api.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SOW Phase 4, Part C — "Admin/SIH dashboard". Restricted to ADMIN, matching
 * the SOW's own framing of this as an admin/SIH-facing view rather than a
 * farmer/buyer one. (FARMER/FPO/CONSUMER/BULK_BUYER roles exist in this
 * codebase since Phase 2's marketplace — restricting to ADMIN here is a
 * deliberate scope choice, not a limitation of the role model.)
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/verification")
    public VerificationStatsResponse verificationStats() {
        return analyticsService.getVerificationStats();
    }

    @GetMapping("/operational")
    public OperationalStatsResponse operationalStats() {
        return analyticsService.getOperationalStats();
    }

    @GetMapping("/sih-impact")
    public SihImpactResponse sihImpact() {
        return analyticsService.getSihImpact();
    }
}
