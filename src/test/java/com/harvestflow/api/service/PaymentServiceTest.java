package com.harvestflow.api.service;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.entity.PurchaseInvoice;
import com.harvestflow.api.domain.entity.SalesInvoice;
import com.harvestflow.api.domain.enums.PaymentReferenceType;
import com.harvestflow.api.domain.enums.PaymentStatus;
import com.harvestflow.api.dto.payment.PaymentResponse;
import com.harvestflow.api.exception.ResourceNotFoundException;
import com.harvestflow.api.repository.HarvestCaseRepository;
import com.harvestflow.api.repository.PurchaseInvoiceRepository;
import com.harvestflow.api.repository.SalesInvoiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises PaymentService against the real (mock) gateway provider wired
 * by default — {@code harvestflow.payment.provider} is left at its
 * application.yml default ("mock") for this test profile.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentServiceTest {

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private HarvestCaseRepository harvestCaseRepository;
    @Autowired
    private PurchaseInvoiceRepository purchaseInvoiceRepository;
    @Autowired
    private SalesInvoiceRepository salesInvoiceRepository;

    @Test
    void initiatingAPaymentAgainstARealPurchaseInvoiceSucceeds() {
        PurchaseInvoice invoice = persistPurchaseInvoice();

        PaymentResponse response = paymentService.initiate(
                PaymentReferenceType.PURCHASE_INVOICE, invoice.getId(), 5000.0, "INR", "farmer payout", "purchase.tester");

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.provider()).isEqualTo("mock-payment-v1");
        assertThat(response.gatewayReference()).startsWith("MOCK-");
        assertThat(response.referenceId()).isEqualTo(invoice.getId());
    }

    @Test
    void initiatingAPaymentAgainstARealSalesInvoiceSucceeds() {
        SalesInvoice invoice = persistSalesInvoice();

        PaymentResponse response = paymentService.initiate(
                PaymentReferenceType.SALES_INVOICE, invoice.getId(), 8000.0, null, null, "accounts.tester");

        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.currency()).isEqualTo("INR"); // defaulted when null
    }

    @Test
    void nonPositiveAmountFailsViaTheMockGateway() {
        PurchaseInvoice invoice = persistPurchaseInvoice();

        PaymentResponse response = paymentService.initiate(
                PaymentReferenceType.PURCHASE_INVOICE, invoice.getId(), 0.0, "INR", null, "purchase.tester");

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isNotBlank();
        assertThat(response.gatewayReference()).isNull();
    }

    @Test
    void paymentAgainstAnUnknownInvoiceIsRejected() {
        assertThatThrownBy(() -> paymentService.initiate(
                PaymentReferenceType.PURCHASE_INVOICE, UUID.randomUUID(), 100.0, "INR", null, "tester"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void paymentsForAReferenceCanBeListed() {
        PurchaseInvoice invoice = persistPurchaseInvoice();
        paymentService.initiate(PaymentReferenceType.PURCHASE_INVOICE, invoice.getId(), 100.0, "INR", null, "tester");
        paymentService.initiate(PaymentReferenceType.PURCHASE_INVOICE, invoice.getId(), 200.0, "INR", null, "tester");

        assertThat(paymentService.getForReference(PaymentReferenceType.PURCHASE_INVOICE, invoice.getId())).hasSize(2);
    }

    private PurchaseInvoice persistPurchaseInvoice() {
        HarvestCase harvestCase = persistHarvestCase();
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setHarvestCase(harvestCase);
        invoice.setInvoiceNumber("PI-" + UUID.randomUUID());
        invoice.setRate(10.0);
        invoice.setWeight(500.0);
        return purchaseInvoiceRepository.save(invoice);
    }

    private SalesInvoice persistSalesInvoice() {
        HarvestCase harvestCase = persistHarvestCase();
        SalesInvoice invoice = new SalesInvoice();
        invoice.setHarvestCase(harvestCase);
        invoice.setInvoiceNumber("SI-" + UUID.randomUUID());
        invoice.setBuyerCompany("Test Buyer Co");
        invoice.setSalesRate(16.0);
        invoice.setWeight(500.0);
        return salesInvoiceRepository.save(invoice);
    }

    private HarvestCase persistHarvestCase() {
        HarvestCase harvestCase = new HarvestCase();
        harvestCase.setCreatedByUsername("plot.tester");
        harvestCase.setLocation("Test Farm");
        harvestCase.setVisitDate(LocalDate.now());
        harvestCase.setFarmerName("Test Farmer " + UUID.randomUUID());
        harvestCase.setVillage("Test Village");
        harvestCase.setHarvestingDate(LocalDate.now().plusDays(3));
        harvestCase.setSelectedCompany("Test Company");
        return harvestCaseRepository.save(harvestCase);
    }
}
