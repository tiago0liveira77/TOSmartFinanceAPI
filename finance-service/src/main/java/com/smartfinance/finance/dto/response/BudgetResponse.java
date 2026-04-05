package com.smartfinance.finance.dto.response;

import com.smartfinance.finance.entity.Budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        String period,
        LocalDate startDate,
        LocalDate endDate,
        int alertThreshold
) {
    public static BudgetResponse from(Budget b) {
        return new BudgetResponse(b.getId(), b.getCategory().getId(),
                b.getCategory().getName(), b.getAmount(), b.getPeriod().name(),
                b.getStartDate(), b.getEndDate(), b.getAlertThreshold());
    }
}