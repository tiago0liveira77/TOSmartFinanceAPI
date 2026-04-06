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
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month) {
        LocalDate now = LocalDate.now();
        return ApiResponse.ok(reportService.getSummary(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue()));
    }

    @GetMapping("/by-category")
    public ApiResponse<List<CategoryReportResponse>> getByCategory(
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            @RequestParam(name = "type", defaultValue = "EXPENSE") TransactionType type) {
        LocalDate now = LocalDate.now();
        return ApiResponse.ok(reportService.getByCategory(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue(),
                type));
    }

    @GetMapping("/monthly-trend")
    public ApiResponse<List<MonthlyTrendResponse>> getMonthlyTrend(
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "months", defaultValue = "12") int months) {
        return ApiResponse.ok(reportService.getMonthlyTrend(
                year != null ? year : LocalDate.now().getYear(),
                months));
    }

    @GetMapping("/budget-status")
    public ApiResponse<List<BudgetStatusResponse>> getBudgetStatus() {
        return ApiResponse.ok(reportService.getBudgetStatus());
    }
}
