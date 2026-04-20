package com.smartfinance.finance.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchCreateTransactionRequest(
        @NotEmpty @Size(max = 50) List<@Valid BatchCreateTransactionRow> transactions
) {}
