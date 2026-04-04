package com.smartfinance.shared.event;

import java.util.List;
import java.util.UUID;

/**
 * Published by finance-service → routing key: "transactions.imported"
 * Consumed by ai-service (categorization) and notification-service (import confirmation email)
 */
public record TransactionImportedEvent(
        UUID userId,
        UUID importId,
        List<UUID> transactionIds
) {
    public static final String ROUTING_KEY = "transactions.imported";
}
