package com.smartfinance.finance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma transação já validada e opcionalmente editada pelo utilizador,
 * proveniente do fluxo de pré-visualização de CSV.
 *
 * @param categoryId categoria selecionada manualmente pelo utilizador (null = deixar para AI)
 */
public record CsvConfirmRow(
        @NotBlank String date,
        @NotBlank String description,
        @NotNull @Positive BigDecimal amount,
        @NotBlank String type,
        UUID categoryId
) {}
