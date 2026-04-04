package com.smartfinance.shared.event;

import java.util.UUID;

/**
 * Published by finance-service → routing key: "budget.threshold.reached"
 * Consumed by notification-service (email + in-app budget alert)
 */
public record BudgetThresholdEvent(
        UUID userId,
        UUID budgetId,
        int percentage
) {
    public static final String ROUTING_KEY = "budget.threshold.reached";
}
