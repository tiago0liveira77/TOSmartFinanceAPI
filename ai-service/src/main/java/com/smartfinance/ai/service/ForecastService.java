package com.smartfinance.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfinance.ai.config.AiProperties;
import com.smartfinance.ai.dto.ForecastResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastService {

    private final AiClientService aiClient;
    private final AiProperties aiProperties;
    private final FinanceDataService financeDataService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.cache.forecast-ttl-hours}")
    private long forecastTtlHours;

    public ForecastResponse getForecast(int months, UUID userId, String authHeader) {
        String cacheKey = "forecast:" + userId;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ForecastResponse.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached forecast: {}", e.getMessage());
            }
        }

        List<FinanceDataService.TrendInfo> trend = financeDataService.getMonthlyTrend(authHeader);

        String trendText = trend.stream()
                .map(t -> String.format("  %s: receitas=%.2f€, despesas=%.2f€", t.month(), t.income(), t.expenses()))
                .collect(Collectors.joining("\n"));

        String userPrompt = String.format("""
                Com base nestes dados históricos de 6 meses:
                %s

                Prevê os gastos totais para os próximos %d meses.
                Responde APENAS com JSON:
                {
                  "predictions": [
                    {
                      "month": "YYYY-MM",
                      "totalPredicted": 1200.00,
                      "categories": [
                        {"name": "Alimentação", "predicted": 350.00, "confidence": "ALTA"}
                      ]
                    }
                  ]
                }
                Usa português de Portugal. Confidence pode ser: ALTA, MÉDIA, BAIXA.
                """, trendText.isEmpty() ? "  (sem dados históricos disponíveis)" : trendText, months);

        ForecastResponse result = callAiForForecast(userPrompt);

        try {
            redisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(result),
                    forecastTtlHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache forecast: {}", e.getMessage());
        }

        return result;
    }

    public void invalidateCache(UUID userId) {
        redisTemplate.delete("forecast:" + userId);
    }

    private ForecastResponse callAiForForecast(String userPrompt) {
        try {
            List<Map<String, String>> messages = List.of(
                    AiClientService.system("És um assistente financeiro que gera previsões em português de Portugal. Responde apenas com JSON válido."),
                    AiClientService.user(userPrompt)
            );
            String content = InsightService.stripMarkdownFences(aiClient.completeLong(messages));
            log.info("Forecast AI raw response: {}", content);

            JsonNode root = objectMapper.readTree(content);
            JsonNode predictionsNode = root.get("predictions");
            List<ForecastResponse.MonthForecast> predictions = new ArrayList<>();

            if (predictionsNode != null && predictionsNode.isArray()) {
                for (JsonNode pred : predictionsNode) {
                    try {
                        List<ForecastResponse.CategoryPrediction> cats = new ArrayList<>();
                        if (pred.has("categories")) {
                            for (JsonNode cat : pred.get("categories")) {
                                try {
                                    cats.add(new ForecastResponse.CategoryPrediction(
                                            cat.get("name").asText(),
                                            new BigDecimal(cat.get("predicted").asText()),
                                            cat.path("confidence").asText("MÉDIA")));
                                } catch (Exception catEx) {
                                    log.warn("Skipping category in forecast: {} — {}", cat, catEx.getMessage());
                                }
                            }
                        }
                        predictions.add(new ForecastResponse.MonthForecast(
                                pred.get("month").asText(),
                                new BigDecimal(pred.get("totalPredicted").asText()),
                                cats));
                    } catch (Exception predEx) {
                        log.warn("Skipping prediction entry in forecast: {} — {}", pred, predEx.getMessage());
                    }
                }
            } else {
                log.warn("Forecast: 'predictions' node missing or not an array. Root keys: {}", root.fieldNames());
            }
            return new ForecastResponse(predictions);
        } catch (Exception e) {
            log.error("AI forecast call failed: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            return new ForecastResponse(List.of());
        }
    }
}
