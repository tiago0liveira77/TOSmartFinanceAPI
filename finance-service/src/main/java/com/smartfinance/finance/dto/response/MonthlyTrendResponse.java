package com.smartfinance.finance.dto.response;

import java.math.BigDecimal;

public record MonthlyTrendResponse(
        String month,        // formato: "YYYY-MM"
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal balance
) {}