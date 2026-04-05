package com.smartfinance.finance.dto.request;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record UpdateTransactionRequest(
        UUID categoryId,
        @Size(max = 500) String description,
        String notes,
        LocalDate date
) {}