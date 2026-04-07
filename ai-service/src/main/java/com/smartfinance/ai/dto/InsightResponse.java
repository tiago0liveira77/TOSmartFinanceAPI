package com.smartfinance.ai.dto;

import java.util.List;

public record InsightResponse(
        String month,
        List<String> insights,
        String generatedAt
) {}
