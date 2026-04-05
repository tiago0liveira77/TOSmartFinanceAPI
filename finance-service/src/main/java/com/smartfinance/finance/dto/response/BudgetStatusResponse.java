package com.smartfinance.finance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetStatusResponse(
        UUID budgetId,
        String categoryName,
        BigDecimal budgetAmount,
        BigDecimal spent,
        BigDecimal percentage,
        BigDecimal remaining,
        String status   // OK, WARNING, EXCEEDED
) {}