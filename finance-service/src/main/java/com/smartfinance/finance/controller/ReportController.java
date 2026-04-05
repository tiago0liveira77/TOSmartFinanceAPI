package com.smartfinance.finance.controller;

import com.smartfinance.finance.dto.response.BudgetStatusResponse;
import com.smartfinance.finance.dto.response.CategoryReportResponse;
import com.smartfinance.finance.dto.response.MonthlyTrendResponse;
import com.smartfinance.finance.dto.response.ReportSummaryResponse;
import com.smartfinance.finance.entity.TransactionType;
import com.smartfinance.finance.service.ReportService;
import com.smartfinance.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    public ApiResponse<ReportSummaryResponse> getSummary(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getMonthValue()}") int month) {
        return ApiResponse.ok(reportService.getSummary(year, month));
    }

    @GetMapping("/by-category")
    public ApiResponse<List<CategoryReportResponse>> getByCategory(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getMonthValue()}") int month,
            @RequestParam(defaultValue = "EXPENSE") TransactionType type) {
        return ApiResponse.ok(reportService.getByCategory(year, month, type));
    }

    @GetMapping("/monthly-trend")
    public ApiResponse<List<MonthlyTrendResponse>> getMonthlyTrend(
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year,
            @RequestParam(defaultValue = "12") int months) {
        return ApiResponse.ok(reportService.getMonthlyTrend(year, months));
    }

    @GetMapping("/budget-status")
    public ApiResponse<List<BudgetStatusResponse>> getBudgetStatus() {
        return ApiResponse.ok(reportService.getBudgetStatus());
    }
}
