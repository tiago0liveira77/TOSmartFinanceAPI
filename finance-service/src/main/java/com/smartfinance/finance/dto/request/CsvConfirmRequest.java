package com.smartfinance.finance.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Corpo do endpoint POST /api/v1/transactions/import/confirm.
 * Contém as transações já validadas e editadas pelo utilizador para importação final.
 */
public record CsvConfirmRequest(
        @NotNull UUID accountId,
        @NotEmpty List<@Valid CsvConfirmRow> transactions
) {}
