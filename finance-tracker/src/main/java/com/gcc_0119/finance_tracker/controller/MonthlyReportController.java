package com.gcc_0119.finance_tracker.controller;

import com.gcc_0119.finance_tracker.common.ApiResponse;
import com.gcc_0119.finance_tracker.common.SecurityUtils;
import com.gcc_0119.finance_tracker.model.MonthlyReport;
import com.gcc_0119.finance_tracker.service.MonthlyReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class MonthlyReportController {

    @Autowired
    private MonthlyReportService monthlyReportService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping("/monthly")
    public ApiResponse<List<MonthlyReport>> getMonthlyReports() {
        String userId = securityUtils.getCurrentUserId();
        List<MonthlyReport> reports = monthlyReportService.listReports(userId);
        return ApiResponse.success(reports);
    }

    @GetMapping("/monthly/{month}")
    public ApiResponse<MonthlyReport> getMonthlyReport(@PathVariable String month) {
        String userId = securityUtils.getCurrentUserId();
        MonthlyReport report = monthlyReportService.getReport(userId, month);
        return ApiResponse.success(report);
    }
}
