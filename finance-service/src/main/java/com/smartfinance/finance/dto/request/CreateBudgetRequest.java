package com.smartfinance.finance.dto.request;

import com.smartfinance.finance.entity.BudgetPeriod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBudgetRequest(
        @NotNull UUID categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull BudgetPeriod period,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        @Min(1) @Max(100) Integer alertThreshold
) {}