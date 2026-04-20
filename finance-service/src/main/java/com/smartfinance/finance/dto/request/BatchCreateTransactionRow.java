package com.smartfinance.finance.dto.request;

import com.smartfinance.finance.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Uma linha de transação no pedido de criação em batch.
 * Sem suporte a recorrência — para isso usar o endpoint individual.
 */
public record BatchCreateTransactionRow(
        @NotNull UUID accountId,
        UUID categoryId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull TransactionType type,
        @Size(max = 500) String description,
        @NotNull LocalDate date
) {}
