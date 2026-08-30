package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.entity.MarketOrder;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.repository.HarvestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Bridges a confirmed marketplace trade into the existing internal
 * HarvestFlow case/state-machine. It does not replace or duplicate any
 * internal stage; it creates the case at the existing Plot Selection queue.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceWorkflowService {

    private final HarvestCaseRepository harvestCaseRepository;

    @Transactional
    public HarvestCase createInternalCase(MarketOrder order) {
        return harvestCaseRepository.findByMarketOrderId(order.getId()).orElseGet(() -> {
            HarvestCase c = new HarvestCase();
            c.setMarketOrderId(order.getId());
            c.setCreatedByUsername("marketplace");
            c.setLocation(order.getListing().getLocation());
            c.setVisitDate(LocalDate.now());
            c.setCommissionAgentName("Direct Marketplace");
            c.setFarmerName(order.getSeller().getName());
            c.setFarmerPhone(order.getSeller().getMobile());
            c.setVillage(order.getListing().getLocation());
            c.setNumberOfPlants(null);
            c.setHarvestingDate(order.getListing().getHarvestDate());
            c.setRemark("Created automatically from marketplace order " + order.getId());
            c.setVolume(order.getQuantity());
            c.setRecovery(null);
            c.setPulp(null);
            // The existing internal schema requires this field. For a direct
            // marketplace sale the buyer is the destination, not a commission
            // company, so use a neutral value and keep the actual buyer on the
            // MarketOrder itself.
            c.setSelectedCompany("Direct Marketplace");
            c.setCropType(order.getCropName());
            c.setPriority("medium");
            c.setStatus(CaseStatus.AWAITING_PLOT_SELECTION);
            c.addTimelineEntry("marketplace", "Created from accepted marketplace order " + order.getId());
            return harvestCaseRepository.save(c);
        });
    }
}
