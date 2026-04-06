package com.smartfinance.finance.controller;

import com.smartfinance.finance.dto.response.BudgetStatusResponse;
import com.smartfinance.finance.dto.response.CategoryReportResponse;
import com.smartfinance.finance.dto.response.DailyBreakdownResponse;
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
            @RequestParam(name = "fromMonth", required = false) String fromMonth,
            @RequestParam(name = "months", defaultValue = "6") int months) {
        LocalDate start = fromMonth != null
                ? LocalDate.parse(fromMonth + "-01")
                : LocalDate.now().minusMonths(months - 1).withDayOfMonth(1);
        return ApiResponse.ok(reportService.getMonthlyTrend(start, months));
    }

    @GetMapping("/daily-breakdown")
    public ApiResponse<List<DailyBreakdownResponse>> getDailyBreakdown(
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month) {
        LocalDate now = LocalDate.now();
        return ApiResponse.ok(reportService.getDailyBreakdown(
                year != null ? year : now.getYear(),
                month != null ? month : now.getMonthValue()));
    }

    @GetMapping("/budget-status")
    public ApiResponse<List<BudgetStatusResponse>> getBudgetStatus() {
        return ApiResponse.ok(reportService.getBudgetStatus());
    }
}
