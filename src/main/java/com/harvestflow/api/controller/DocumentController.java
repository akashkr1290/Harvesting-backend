package com.harvestflow.api.controller;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.service.CaseService;
import com.harvestflow.api.service.pdf.PdfDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.function.Function;

/**
 * Serves the four printable documents named in the SOW, generated on
 * demand from the case's current data (see PdfDocumentService — nothing
 * is persisted, so these always reflect the latest state).
 *
 * Access follows the same model as case reads generally (GET /api/cases/{id}):
 * open to any authenticated user rather than locked to a specific role,
 * since several roles legitimately need to view/download these (e.g. a
 * Supervisor might want to see the packing issue slip a Godown team member
 * created). If a document isn't ready yet — e.g. the purchase invoice
 * hasn't been generated — this returns 409, not a broken/empty PDF.
 */
@RestController
@RequestMapping("/api/cases/{id}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final CaseService caseService;
    private final PdfDocumentService pdfDocumentService;

    @GetMapping(value = "/selection-report", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> selectionReport(@PathVariable UUID id) {
        return pdf(id, "selection-report.pdf", pdfDocumentService::generateSelectionReport);
    }

    @GetMapping(value = "/packing-issue-slip", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> packingIssueSlip(@PathVariable UUID id) {
        return pdf(id, "packing-issue-slip.pdf", pdfDocumentService::generatePackingIssueSlip);
    }

    @GetMapping(value = "/purchase-invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> purchaseInvoice(@PathVariable UUID id) {
        return pdf(id, "purchase-invoice.pdf", pdfDocumentService::generatePurchaseInvoice);
    }

    @GetMapping(value = "/sales-invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> salesInvoice(@PathVariable UUID id) {
        return pdf(id, "sales-invoice.pdf", pdfDocumentService::generateSalesInvoice);
    }

    private ResponseEntity<byte[]> pdf(UUID id, String filename, Function<HarvestCase, byte[]> generator) {
        HarvestCase harvestCase = caseService.getById(id);
        byte[] bytes = generator.apply(harvestCase);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_PDF).body(bytes);
    }
}
