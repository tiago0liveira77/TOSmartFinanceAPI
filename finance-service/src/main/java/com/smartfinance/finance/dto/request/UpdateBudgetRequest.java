package com.smartfinance.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateBudgetRequest(
        @DecimalMin("0.01") BigDecimal amount,
        @Min(1) @Max(100) Integer alertThreshold,
        LocalDate endDate
) {}