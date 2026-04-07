package com.smartfinance.ai.dto;

import java.math.BigDecimal;
import java.util.List;

public record ForecastResponse(List<MonthForecast> predictions) {

    public record MonthForecast(
            String month,
            BigDecimal totalPredicted,
            List<CategoryPrediction> categories
    ) {}

    public record CategoryPrediction(
            String name,
            BigDecimal predicted,
            String confidence
    ) {}
}
