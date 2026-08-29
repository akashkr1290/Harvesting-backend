package com.harvestflow.api.controller;

import com.harvestflow.api.dto.reports.ReportsSummaryResponse;
import com.harvestflow.api.service.ReportsService;
import com.harvestflow.api.service.excel.ExcelExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportsController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportsService reportsService;
    private final ExcelExportService excelExportService;

    @GetMapping("/summary")
    public ReportsSummaryResponse summary() {
        return reportsService.summary();
    }

    @GetMapping("/summary/export")
    public ResponseEntity<byte[]> exportSummary() {
        byte[] bytes = excelExportService.generateReportsWorkbook(reportsService.summary());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename("harvestflow-reports.xlsx").build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .body(bytes);
    }
}
