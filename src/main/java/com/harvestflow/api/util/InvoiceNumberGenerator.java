package com.harvestflow.api.util;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-memory sequential counter per prefix. Good enough while the
 * system is single-instance; if the app is ever scaled horizontally this
 * needs to move to a DB sequence so numbers stay unique and gapless-ish
 * across instances (SOW 5.2 C: "Document numbering series").
 */
@Component
public class InvoiceNumberGenerator {

    private final AtomicInteger purchaseCounter = new AtomicInteger(1000);
    private final AtomicInteger salesCounter = new AtomicInteger(5000);

    public String nextPurchaseInvoiceNumber() {
        return "PUR-" + purchaseCounter.incrementAndGet();
    }

    public String nextSalesInvoiceNumber() {
        return "SAL-" + salesCounter.incrementAndGet();
    }
}
