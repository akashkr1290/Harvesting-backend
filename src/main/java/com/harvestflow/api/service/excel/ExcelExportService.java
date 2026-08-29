package com.harvestflow.api.service.excel;

import com.harvestflow.api.domain.entity.HarvestCase;
import com.harvestflow.api.domain.enums.CaseStatus;
import com.harvestflow.api.dto.reports.ReportsSummaryResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {

    public byte[] generateReportsWorkbook(ReportsSummaryResponse summary) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);

            Sheet snapshot = workbook.createSheet("Daily Snapshot");
            writeKeyValueSheet(snapshot, headerStyle, Map.of(
                    "Total Cases", String.valueOf(summary.totalCases()),
                    "Selections Today", String.valueOf(summary.selectionsToday()),
                    "Planned", String.valueOf(summary.plannedCount()),
                    "Completed", String.valueOf(summary.completedCount()),
                    "Average Recovery", String.format("%.2f", summary.averageRecovery()),
                    "Average Pulp", String.format("%.2f", summary.averagePulp())
            ));

            Sheet stageWise = workbook.createSheet("Stage-Wise Pending");
            writeHeaderRow(stageWise, headerStyle, "Status", "Count");
            int rowIdx = 1;
            for (CaseStatus status : CaseStatus.values()) {
                Row row = stageWise.createRow(rowIdx++);
                row.createCell(0).setCellValue(status.name());
                row.createCell(1).setCellValue(summary.stageWisePending().getOrDefault(status, 0L));
            }
            autoSize(stageWise, 2);

            Sheet purchaseByFarmer = workbook.createSheet("Purchase by Farmer");
            writeHeaderRow(purchaseByFarmer, headerStyle, "Farmer", "Net Payable");
            writeMapRows(purchaseByFarmer, summary.purchaseSummaryByFarmer());

            Sheet salesByCompany = workbook.createSheet("Sales by Company");
            writeHeaderRow(salesByCompany, headerStyle, "Company", "Total");
            writeMapRows(salesByCompany, summary.salesSummaryByCompany());

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate reports workbook", e);
        }
    }

    public byte[] generateCaseListWorkbook(List<HarvestCase> cases) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(workbook);
            Sheet sheet = workbook.createSheet("Cases");

            writeHeaderRow(sheet, headerStyle,
                    "Case ID", "Status", "Location", "Village", "Farmer", "Farmer Phone",
                    "Visit Date", "Harvesting Date", "Volume", "Recovery", "Pulp",
                    "Selected Company", "Rate/Unit", "Purchase Net Payable", "Sales Total");

            int rowIdx = 1;
            for (HarvestCase c : cases) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(c.getId().toString());
                row.createCell(1).setCellValue(c.getStatus().name());
                row.createCell(2).setCellValue(c.getLocation());
                row.createCell(3).setCellValue(c.getVillage());
                row.createCell(4).setCellValue(c.getFarmerName());
                row.createCell(5).setCellValue(nvl(c.getFarmerPhone()));
                row.createCell(6).setCellValue(c.getVisitDate() != null ? c.getVisitDate().toString() : "");
                row.createCell(7).setCellValue(c.getHarvestingDate() != null ? c.getHarvestingDate().toString() : "");
                row.createCell(8).setCellValue(nz(c.getVolume()));
                row.createCell(9).setCellValue(nz(c.getRecovery()));
                row.createCell(10).setCellValue(nz(c.getPulp()));
                row.createCell(11).setCellValue(c.getSelectedCompany());
                row.createCell(12).setCellValue(nz(c.getRatePerUnit()));
                row.createCell(13).setCellValue(c.getPurchaseInvoice() != null ? c.getPurchaseInvoice().getNetPayable() : 0.0);
                row.createCell(14).setCellValue(c.getSalesInvoice() != null ? c.getSalesInvoice().getTotal() : 0.0);
            }
            autoSize(sheet, 15);

            return toBytes(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate case list workbook", e);
        }
    }

    private void writeKeyValueSheet(Sheet sheet, CellStyle headerStyle, Map<String, String> values) {
        writeHeaderRow(sheet, headerStyle, "Metric", "Value");
        int rowIdx = 1;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        autoSize(sheet, 2);
    }

    private void writeMapRows(Sheet sheet, Map<String, Double> data) {
        int rowIdx = 1;
        for (Map.Entry<String, Double> entry : data.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(entry.getValue());
        }
        autoSize(sheet, 2);
    }

    private void writeHeaderRow(Sheet sheet, CellStyle headerStyle, String... headers) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }

    private void autoSize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private byte[] toBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
