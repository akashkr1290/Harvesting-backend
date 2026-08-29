package com.harvestflow.api.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseInvoiceTest {

    private PurchaseInvoice baseInvoice(double rate, double weight) {
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setRate(rate);
        invoice.setWeight(weight);
        invoice.setIncludeCommission(false);
        invoice.setIncludePackingCost(false);
        invoice.setIncludeTransportCost(false);
        return invoice;
    }

    @Test
    void baseAmount_isRateTimesWeight() {
        PurchaseInvoice invoice = baseInvoice(10, 100);
        assertThat(invoice.getBaseAmount()).isEqualTo(1000);
        assertThat(invoice.getNetPayable()).isEqualTo(1000);
    }

    @Test
    void subtractsCommissionPackingAndTransport_whenAllIncluded() {
        PurchaseInvoice invoice = baseInvoice(10, 100);
        invoice.setIncludeCommission(true);
        invoice.setCommissionAmount(50.0);
        invoice.setIncludePackingCost(true);
        invoice.setPackingCost(30.0);
        invoice.setIncludeTransportCost(true);
        invoice.setTransportCost(20.0);

        assertThat(invoice.getNetPayable()).isEqualTo(900);
    }

    @Test
    void aToggledOffComponent_isNotSubtractedEvenWithAnAmountSet() {
        PurchaseInvoice invoice = baseInvoice(10, 100);
        // Amounts are set but every include flag stays false via baseInvoice()
        invoice.setCommissionAmount(999.0);
        invoice.setPackingCost(999.0);
        invoice.setTransportCost(999.0);

        assertThat(invoice.getNetPayable()).isEqualTo(1000);
    }

    @Test
    void deductionsSubtractAndAdditionsAdd_regardlessOfToggles() {
        PurchaseInvoice invoice = baseInvoice(10, 100);
        invoice.setOtherCharges(15.0);
        invoice.setDeductions(25.0);
        invoice.setAdditions(40.0);

        // 1000 - 15 - 25 + 40 = 1000
        assertThat(invoice.getNetPayable()).isEqualTo(1000);
    }

    @Test
    void nullComponentAmounts_treatedAsZeroNotNullPointerException() {
        // Fields are boxed Double and nullable at the DB level — a saved
        // invoice with an untouched optional field must not blow up here.
        PurchaseInvoice invoice = baseInvoice(10, 100);
        invoice.setIncludeCommission(true);
        invoice.setCommissionAmount(null);

        assertThat(invoice.getNetPayable()).isEqualTo(1000);
    }
}
