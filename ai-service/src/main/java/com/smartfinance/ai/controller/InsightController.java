package com.smartfinance.ai.controller;

import com.smartfinance.ai.dto.ForecastResponse;
import com.smartfinance.ai.dto.InsightResponse;
import com.smartfinance.ai.service.ForecastService;
import com.smartfinance.ai.service.InsightService;
import com.smartfinance.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;
    private final ForecastService forecastService;

    @GetMapping("/insights")
    public ApiResponse<InsightResponse> getInsights(
            @RequestParam(name = "month", required = false) String month,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("Authorization") String authHeader) {

        String targetMonth = (month != null && !month.isBlank())
                ? month
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        return ApiResponse.ok(insightService.getInsights(targetMonth, userId, authHeader));
    }

    @PostMapping("/insights/refresh")
    public ApiResponse<InsightResponse> refreshInsights(
            @RequestParam(name = "month", required = false) String month,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("Authorization") String authHeader) {

        String targetMonth = (month != null && !month.isBlank())
                ? month
                : YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        insightService.invalidateCache(userId, targetMonth);
        return ApiResponse.ok(insightService.getInsights(targetMonth, userId, authHeader));
    }

    @GetMapping("/forecast")
    public ApiResponse<ForecastResponse> getForecast(
            @RequestParam(name = "months", defaultValue = "3") int months,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("Authorization") String authHeader) {

        return ApiResponse.ok(forecastService.getForecast(months, userId, authHeader));
    }
}
