package com.smartfinance.ai.dto;

public record ChatResponse(
        String message,
        String conversationId,
        String timestamp
) {}
