package com.harvestflow.api.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Marks pending offers past their expiry time as EXPIRED every 15 minutes. */
@Component
@RequiredArgsConstructor
public class OfferExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OfferExpiryJob.class);

    private final OfferService offerService;

    @Scheduled(fixedDelayString = "${harvestflow.marketplace.offer-expiry-check-ms:900000}",
            initialDelayString = "${harvestflow.marketplace.offer-expiry-check-ms:900000}")
    public void run() {
        try {
            int n = offerService.expireStaleOffers();
            if (n > 0) log.info("Expired {} stale offer(s)", n);
        } catch (Exception e) {
            log.warn("Offer expiry job failed: {}", e.getMessage());
        }
    }
}
