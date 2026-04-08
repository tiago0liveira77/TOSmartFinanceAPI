package com.smartfinance.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfinance.ai.config.AiProperties;
import com.smartfinance.ai.dto.InsightResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InsightService {

    private final AiClientService aiClient;
    private final AiProperties aiProperties;
    private final FinanceDataService financeDataService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.cache.insights-ttl-hours}")
    private long insightsTtlHours;

    public InsightResponse getInsights(String month, UUID userId, String authHeader) {
        String cacheKey = "insights:" + userId + ":" + month;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                log.debug("[AI][REDIS] Cache hit: key={}", cacheKey);
                return objectMapper.readValue(cached, InsightResponse.class);
            } catch (Exception e) {
                log.warn("[AI][REDIS] Cache deserialization failed, regenerating: key={}, error={}", cacheKey, e.getMessage());
            }
        } else {
            log.info("[AI][REDIS] Cache miss — generating insights: userId={}, month={}", userId, month);
        }

        YearMonth ym = YearMonth.parse(month);
        FinanceDataService.SummaryInfo current = financeDataService.getSummary(
                ym.getYear(), ym.getMonthValue(), authHeader);

        YearMonth prevYm = ym.minusMonths(1);
        FinanceDataService.SummaryInfo previous = financeDataService.getSummary(
                prevYm.getYear(), prevYm.getMonthValue(), authHeader);

        String categoriesText = current.topCategories().stream()
                .map(c -> String.format("  - %s: %.2f€ (%.1f%%)", c.name(), c.amount(), c.percentage()))
                .collect(Collectors.joining("\n"));

        String userPrompt = String.format("""
                Analisa os dados financeiros de %s:
                Receitas: %.2f€
                Despesas: %.2f€
                Saldo: %.2f€
                Taxa de poupança: %.1f%%
                Top categorias de despesa:
                %s

                Mês anterior (%s):
                Receitas: %.2f€ | Despesas: %.2f€

                Responde APENAS com JSON no formato: {"insights": ["insight1", "insight2", "insight3"]}
                Gera 3 a 5 insights concisos, úteis e acionáveis em português de Portugal.
                Foca em tendências, comparações com o mês anterior e sugestões concretas de poupança.
                """,
                month,
                current.totalIncome(), current.totalExpenses(), current.balance(),
                current.savingsRate(),
                categoriesText.isEmpty() ? "  (sem dados)" : categoriesText,
                prevYm, previous.totalIncome(), previous.totalExpenses());

        List<String> insights = callAiForInsights(userPrompt);

        InsightResponse result = new InsightResponse(
                month, insights,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        try {
            redisTemplate.opsForValue().set(
                    cacheKey, objectMapper.writeValueAsString(result),
                    insightsTtlHours, TimeUnit.HOURS);
            log.info("[AI][REDIS] Insights cached: key={}, count={}, ttl={}h", cacheKey, insights.size(), insightsTtlHours);
        } catch (Exception e) {
            log.warn("[AI][REDIS] Failed to cache insights: key={}, error={}", cacheKey, e.getMessage());
        }

        return result;
    }

    public void invalidateCache(UUID userId, String month) {
        redisTemplate.delete("insights:" + userId + ":" + month);
    }

    private List<String> callAiForInsights(String userPrompt) {
        try {
            List<Map<String, String>> messages = List.of(
                    AiClientService.system("És um assistente financeiro especialista que analisa dados em português de Portugal. Sê conciso e direto."),
                    AiClientService.user(userPrompt)
            );
            String content = aiClient.complete(messages);
            content = stripMarkdownFences(content);

            JsonNode root = objectMapper.readTree(content);
            JsonNode insightsNode = root.get("insights");
            List<String> result = new ArrayList<>();
            if (insightsNode != null && insightsNode.isArray()) {
                insightsNode.forEach(n -> result.add(n.asText()));
            }
            return result.isEmpty()
                    ? List.of("Dados insuficientes para gerar insights este mês.")
                    : result;
        } catch (Exception e) {
            log.error("AI insights call failed: {}", e.getMessage());
            return List.of("Não foi possível gerar insights de momento. Tenta novamente mais tarde.");
        }
    }

    /**
     * Remove delimitadores de código Markdown que alguns LLMs incluem na resposta
     * mesmo quando instruídos a devolver apenas JSON puro.
     *
     * Exemplos de respostas normalizadas por este método:
     *   ```json\n{"insights": [...]}\n```  →  {"insights": [...]}
     *   ```\n{"insights": [...]}\n```      →  {"insights": [...]}
     *
     * Modelos Groq (llama-3.x) incluem estes delimitadores ocasionalmente,
     * especialmente com temperature > 0.3 ou quando o prompt não é suficientemente explícito.
     */
    static String stripMarkdownFences(String content) {
        content = content.trim();
        if (content.startsWith("```")) {
            content = content.replaceAll("```(?:json)?\\n?", "").replaceAll("```", "").trim();
        }
        return content;
    }
}
