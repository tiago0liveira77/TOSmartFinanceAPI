package com.smartfinance.finance.dto.response;

import java.math.BigDecimal;

public record DailyBreakdownResponse(
        int day,
        BigDecimal income,
        BigDecimal expenses
) {}
