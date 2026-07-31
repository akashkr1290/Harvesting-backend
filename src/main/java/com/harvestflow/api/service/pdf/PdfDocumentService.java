package com.harvestflow.api.service.pdf;

import com.harvestflow.api.domain.entity.*;
import com.harvestflow.api.domain.enums.PackingStage;
import com.harvestflow.api.exception.DocumentNotReadyException;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Generates the four printable documents named in the SOW (section 2 and
 * 5.9): selection report, packing material issue slip, purchase invoice,
 * sales invoice. Every document is rendered on demand from the case's
 * current data — nothing is persisted, so there's no storage decision to
 * make here (unlike file uploads) and the PDF always reflects the latest
 * state rather than a stale snapshot from whenever it was first generated.
 */
@Service
public class PdfDocumentService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final Color BRAND_GREEN = new Color(0x2E, 0x7D, 0x32);
    private static final Font TITLE_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND_GREEN);
    private static final Font HEADING_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_GREEN);
    private static final Font LABEL_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
    private static final Font VALUE_FONT = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font VALUE_BOLD_FONT = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    private static final Font SMALL_GRAY_FONT = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

    // -----------------------------------------------------------------
    // 1. Plot Selection report
    // -----------------------------------------------------------------
    public byte[] generateSelectionReport(HarvestCase c) {
        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            addLetterhead(doc, "Plot Selection Report", c);

            addSectionHeading(doc, "Plot & Farmer Details");
            PdfPTable table = newKeyValueTable();
            addRow(table, "Farmer Name", c.getFarmerName());
            addRow(table, "Farmer Phone", nvl(c.getFarmerPhone()));
            addRow(table, "Village", c.getVillage());
            addRow(table, "Location", c.getLocation());
            addRow(table, "Plot Code", nvl(c.getPlotCode()));
            addRow(table, "GPS / Landmark", nvl(c.getGpsNote()));
            addRow(table, "Commission Agent", nvl(c.getCommissionAgentName()));
            addRow(table, "Selected Company", c.getSelectedCompany());
            addRow(table, "Priority", nvl(c.getPriority()));
            doc.add(table);

            addSectionHeading(doc, "Harvesting Details");
            PdfPTable table2 = newKeyValueTable();
            addRow(table2, "Date of Visit", formatDate(c.getVisitDate()));
            addRow(table2, "Harvesting Date", formatDate(c.getHarvestingDate()));
            addRow(table2, "Number of Plants", String.valueOf(c.getNumberOfPlants()));
            addRow(table2, "Volume", String.valueOf(c.getVolume()));
            addRow(table2, "Recovery", String.valueOf(c.getRecovery()));
            addRow(table2, "Pulp", String.valueOf(c.getPulp()));
            doc.add(table2);

            if (c.getRemark() != null && !c.getRemark().isBlank()) {
                addSectionHeading(doc, "Remarks");
                doc.add(new Paragraph(c.getRemark(), VALUE_FONT));
            }

            addFooter(doc, c);
            doc.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate selection report", e);
        }
        return out.toByteArray();
    }

    // -----------------------------------------------------------------
    // 2. Packing material issue slip
    // -----------------------------------------------------------------
    public byte[] generatePackingIssueSlip(HarvestCase c) {
        PackingMaterialRecord record = findPackingRecord(c, PackingStage.ISSUE)
                .orElseThrow(() -> new DocumentNotReadyException(
                        "Packing material hasn't been issued for this case yet."));

        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            addLetterhead(doc, "Packing Material Issue Slip", c);

            addSectionHeading(doc, "Issue Details");
            PdfPTable header = newKeyValueTable();
            addRow(header, "Issued To", record.getHandledBy());
            addRow(header, "Issue Date", formatInstant(record.getRecordedAt()));
            if (record.getRemarks() != null && !record.getRemarks().isBlank()) {
                addRow(header, "Remarks", record.getRemarks());
            }
            doc.add(header);

            addSectionHeading(doc, "Items");
            doc.add(itemsTable(record.getLines()));

            addFooter(doc, c);
            doc.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate packing issue slip", e);
        }
        return out.toByteArray();
    }

    // -----------------------------------------------------------------
    // 3. Purchase invoice / receipt
    // -----------------------------------------------------------------
    public byte[] generatePurchaseInvoice(HarvestCase c) {
        PurchaseInvoice invoice = c.getPurchaseInvoice();
        if (invoice == null) {
            throw new DocumentNotReadyException("Purchase invoice hasn't been generated for this case yet.");
        }

        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            addLetterhead(doc, "Purchase Invoice", c);

            PdfPTable meta = newKeyValueTable();
            addRow(meta, "Invoice Number", invoice.getInvoiceNumber());
            addRow(meta, "Date", formatInstant(invoice.getCreatedAt()));
            doc.add(meta);

            addSectionHeading(doc, "Farmer Details");
            PdfPTable farmer = newKeyValueTable();
            addRow(farmer, "Farmer Name", c.getFarmerName());
            addRow(farmer, "Village", c.getVillage());
            addRow(farmer, "Commission Agent", nvl(c.getCommissionAgentName()));
            doc.add(farmer);

            addSectionHeading(doc, "Cost Calculation");
            PdfPTable cost = newKeyValueTable();
            addRow(cost, "Rate per Unit", currency(invoice.getRate()));
            addRow(cost, "Weight / Quantity", String.valueOf(invoice.getWeight()));
            addRow(cost, "Base Amount", currency(invoice.getBaseAmount()));
            if (invoice.isIncludeCommission()) {
                addRow(cost, "Less: Commission", "- " + currency(nz(invoice.getCommissionAmount())));
            }
            if (invoice.isIncludePackingCost()) {
                addRow(cost, "Less: Packing Material Cost", "- " + currency(nz(invoice.getPackingCost())));
            }
            if (invoice.isIncludeTransportCost()) {
                addRow(cost, "Less: Transport Cost", "- " + currency(nz(invoice.getTransportCost())));
            }
            if (nz(invoice.getOtherCharges()) != 0) {
                addRow(cost, "Less: Other Charges", "- " + currency(nz(invoice.getOtherCharges())));
            }
            if (nz(invoice.getDeductions()) != 0) {
                addRow(cost, "Less: Deductions", "- " + currency(nz(invoice.getDeductions())));
            }
            if (nz(invoice.getAdditions()) != 0) {
                addRow(cost, "Add: Additions", "+ " + currency(nz(invoice.getAdditions())));
            }
            doc.add(cost);

            doc.add(Chunk.NEWLINE);
            doc.add(netPayableBanner("Net Payable to Farmer", invoice.getNetPayable()));

            addFooter(doc, c);
            doc.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate purchase invoice", e);
        }
        return out.toByteArray();
    }

    // -----------------------------------------------------------------
    // 4. Sales invoice
    // -----------------------------------------------------------------
    public byte[] generateSalesInvoice(HarvestCase c) {
        SalesInvoice invoice = c.getSalesInvoice();
        if (invoice == null) {
            throw new DocumentNotReadyException("Sales invoice hasn't been generated for this case yet.");
        }

        Document doc = newDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            addLetterhead(doc, "Sales Invoice", c);

            PdfPTable meta = newKeyValueTable();
            addRow(meta, "Invoice Number", invoice.getInvoiceNumber());
            addRow(meta, "Date", formatInstant(invoice.getCreatedAt()));
            addRow(meta, "Buyer", invoice.getBuyerCompany());
            doc.add(meta);

            addSectionHeading(doc, "Amount");
            PdfPTable amount = newKeyValueTable();
            addRow(amount, "Sales Rate", currency(invoice.getSalesRate()));
            addRow(amount, "Weight / Quantity", String.valueOf(invoice.getWeight()));
            addRow(amount, "Subtotal", currency(invoice.getSubtotal()));
            addRow(amount, "Tax (" + invoice.getTaxPercent() + "%)", currency(invoice.getTaxAmount()));
            doc.add(amount);

            doc.add(Chunk.NEWLINE);
            doc.add(netPayableBanner("Total", invoice.getTotal()));

            addFooter(doc, c);
            doc.close();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to generate sales invoice", e);
        }
        return out.toByteArray();
    }

    // -----------------------------------------------------------------
    // Shared layout helpers
    // -----------------------------------------------------------------

    private Document newDocument() {
        return new Document(PageSize.A4, 40, 40, 50, 40);
    }

    private void addLetterhead(Document doc, String title, HarvestCase c) throws DocumentException {
        Paragraph brand = new Paragraph("HarvestFlow", TITLE_FONT);
        brand.setAlignment(Element.ALIGN_LEFT);
        doc.add(brand);

        Paragraph docTitle = new Paragraph(title, HEADING_FONT);
        docTitle.setSpacingBefore(4);
        docTitle.setSpacingAfter(2);
        doc.add(docTitle);

        Paragraph ref = new Paragraph("Case reference: " + c.getId(), SMALL_GRAY_FONT);
        ref.setSpacingAfter(12);
        doc.add(ref);

        doc.add(new Paragraph(" "));
    }

    private void addSectionHeading(Document doc, String text) throws DocumentException {
        Paragraph p = new Paragraph(text, HEADING_FONT);
        p.setSpacingBefore(10);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private PdfPTable newKeyValueTable() {
        PdfPTable table = new PdfPTable(2);
        try {
            table.setWidths(new float[]{1.3f, 2f});
        } catch (DocumentException e) {
            // setWidths only throws if the array length doesn't match
            // column count, which can't happen here — safe to ignore.
        }
        table.setWidthPercentage(100);
        table.setSpacingAfter(4);
        return table;
    }

    private void addRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, LABEL_FONT));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingBottom(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value == null ? "-" : value, VALUE_FONT));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingBottom(4);
        table.addCell(valueCell);
    }

    private PdfPTable itemsTable(List<PackingMaterialLine> lines) {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setSpacingAfter(8);

        for (String header : new String[]{"Item", "Quantity", "Unit"}) {
            PdfPCell cell = new PdfPCell(new Phrase(header, LABEL_FONT));
            cell.setBackgroundColor(new Color(0xF0, 0xF0, 0xF0));
            cell.setPadding(6);
            table.addCell(cell);
        }

        for (PackingMaterialLine line : lines) {
            table.addCell(cellNoBorder(line.getItemName()));
            table.addCell(cellNoBorder(String.valueOf(line.getQuantity())));
            table.addCell(cellNoBorder(line.getUnit()));
        }
        return table;
    }

    private PdfPCell cellNoBorder(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, VALUE_FONT));
        cell.setPadding(6);
        return cell;
    }

    private Paragraph netPayableBanner(String label, double amount) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + ":  ", HEADING_FONT));
        p.add(new Chunk(currency(amount), VALUE_BOLD_FONT));
        return p;
    }

    private void addFooter(Document doc, HarvestCase c) throws DocumentException {
        doc.add(Chunk.NEWLINE);
        Paragraph footer = new Paragraph(
                "Generated by HarvestFlow — Harvesting Planning, Purchase, Sales & Logistics Workflow System",
                SMALL_GRAY_FONT);
        footer.setSpacingBefore(20);
        doc.add(footer);
    }

    private Optional<PackingMaterialRecord> findPackingRecord(HarvestCase c, PackingStage stage) {
        return c.getPackingRecords().stream().filter(r -> r.getStage() == stage).findFirst();
    }

    private String formatDate(java.time.LocalDate date) {
        return date == null ? "-" : date.format(DATE_FMT);
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) return "-";
        return java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'"));
    }

    private String currency(double value) {
        return String.format("Rs. %,.2f", value);
    }

    private double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private String nvl(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
