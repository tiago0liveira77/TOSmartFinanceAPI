package com.smartfinance.finance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountSummaryResponse(
        UUID accountId,
        BigDecimal balance,
        BigDecimal monthIncome,
        BigDecimal monthExpenses,
        BigDecimal monthNet
) {}