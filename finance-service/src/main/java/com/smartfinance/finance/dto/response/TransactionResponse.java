package com.smartfinance.finance.dto.response;

import com.smartfinance.finance.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        String accountName,
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        String type,
        String description,
        String notes,
        LocalDate date,
        boolean isRecurring,
        String recurrenceRule,
        UUID recurrenceGroupId,
        boolean settled,
        boolean aiCategorized,
        BigDecimal aiConfidence
) {
    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getAccount().getId(),
                t.getAccount().getName(),
                t.getCategory() != null ? t.getCategory().getId() : null,
                t.getCategory() != null ? t.getCategory().getName() : null,
                t.getAmount(),
                t.getType().name(),
                t.getDescription(),
                t.getNotes(),
                t.getDate(),
                t.isRecurring(),
                t.getRecurrenceRule() != null ? t.getRecurrenceRule().name() : null,
                t.getRecurrenceGroupId(),
                t.isSettled(),
                t.isAiCategorized(),
                t.getAiConfidence()
        );
    }
}
