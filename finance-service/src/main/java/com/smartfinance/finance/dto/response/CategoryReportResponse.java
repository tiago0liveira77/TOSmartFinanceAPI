package com.smartfinance.finance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryReportResponse(
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        BigDecimal percentage,
        int transactionCount
) {}