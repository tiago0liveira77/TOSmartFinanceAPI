package com.smartfinance.shared.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published by finance-service → routing key: "transaction.created"
 * Consumed by notification-service (alert checks)
 */
public record TransactionCreatedEvent(
        UUID userId,
        UUID transactionId,
        BigDecimal amount,
        String description
) {
    public static final String ROUTING_KEY = "transaction.created";
}
