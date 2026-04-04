package com.smartfinance.shared.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published by ai-service → routing key: "ai.categorization.completed"
 * Consumed by finance-service to update transaction.category_id and ai_categorized=true
 */
public record CategorizationCompletedEvent(
        UUID transactionId,
        UUID categoryId,
        BigDecimal confidence
) {
    public static final String ROUTING_KEY = "ai.categorization.completed";
}
