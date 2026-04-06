package com.smartfinance.finance.dto.request;

import com.smartfinance.finance.entity.RecurrenceRule;
import com.smartfinance.finance.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(
        @NotNull UUID accountId,
        UUID categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull TransactionType type,
        @Size(max = 500) String description,
        String notes,
        @NotNull LocalDate date,
        boolean isRecurring,
        RecurrenceRule recurrenceRule,
        @Min(2) @Max(24) Integer occurrences   // quantas vezes repetir (inclui a primeira)
) {}