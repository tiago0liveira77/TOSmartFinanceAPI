package com.smartfinance.finance.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ReportSummaryResponse(
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal balance,
        BigDecimal savingsRate,
        List<CategoryReportResponse> topCategories
) {}