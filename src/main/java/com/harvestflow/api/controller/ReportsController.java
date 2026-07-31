package com.harvestflow.api.controller;

import com.harvestflow.api.dto.reports.ReportsSummaryResponse;
import com.harvestflow.api.service.ReportsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final ReportsService reportsService;

    @GetMapping("/summary")
    public ReportsSummaryResponse summary() {
        return reportsService.summary();
    }
}
