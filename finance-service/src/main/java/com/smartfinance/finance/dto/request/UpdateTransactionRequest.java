package com.smartfinance.finance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTransactionRequest(
        UUID categoryId,
        @Size(max = 500) String description,
        String notes,
        LocalDate date,
        @DecimalMin("0.01") BigDecimal amount
) {}