package com.smartfinance.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfinance.ai.config.AiProperties;
import com.smartfinance.ai.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class ChatService {

    private static final int MAX_HISTORY = 20;
    private static final long CHAT_TTL_DAYS = 7;

    private final AiClientService aiClient;
    private final AiProperties aiProperties;
    private final FinanceDataService financeDataService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatResponse chat(String message, String conversationId, UUID userId, String authHeader) {
        String convId = (conversationId != null && !conversationId.isBlank())
                ? conversationId : UUID.randomUUID().toString();
        String cacheKey = "chat:" + userId + ":" + convId;

        List<Map<String, String>> history = loadHistory(cacheKey);

        // Constrói o system prompt com dados financeiros em tempo real (3 HTTP calls)
        log.debug("[AI] Building system prompt for chat: conversationId={}, historySize={}", convId, history.size());
        String systemPrompt = buildSystemPrompt(authHeader);

        // Build messages: system + history + new user message
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(AiClientService.system(systemPrompt));
        messages.addAll(history);
        messages.add(AiClientService.user(message));

        // Call AI
        String aiResponse;
        try {
            aiResponse = aiClient.complete(messages);
        } catch (Exception e) {
            log.error("AI chat call failed: {}", e.getMessage());
            aiResponse = "Erro ao comunicar com o assistente. Tenta novamente.";
        }

        log.debug("[AI] Chat response received: conversationId={}, responseLength={}", convId, aiResponse.length());

        // Atualiza histórico e trunca se necessário
        history.add(AiClientService.user(message));
        history.add(AiClientService.assistant(aiResponse));
        if (history.size() > MAX_HISTORY) {
            int removed = history.size() - MAX_HISTORY;
            history = history.subList(history.size() - MAX_HISTORY, history.size());
            log.info("[AI][REDIS] Chat history truncated: conversationId={}, removedMessages={}", convId, removed);
        }
        saveHistory(cacheKey, history);

        return new ChatResponse(
                aiResponse, convId,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    public void clearConversation(String conversationId, UUID userId) {
        redisTemplate.delete("chat:" + userId + ":" + conversationId);
    }

    // -----------------------------------------------------------------------

    /**
     * Constrói o system prompt para cada mensagem de chat com dados financeiros em tempo real.
     *
     * NOTA DE PERFORMANCE: Este método faz 3 chamadas HTTP síncronas ao finance-service por mensagem:
     *   1. getSummary(mês atual)   → receitas, despesas, saldo, top categorias
     *   2. getSummary(mês anterior) → idem para comparação
     *   3. getMonthlyTrend()        → últimos 6 meses de histórico
     *
     * Estas chamadas não são cacheadas (o chat precisa de dados atuais).
     * Se a latência do chat se tornar um problema, considerar cachear o prompt
     * no Redis com TTL de 1 hora (aceitando dados com até 1h de atraso).
     *
     * O contexto histórico de 6 meses permite ao AI responder a perguntas sobre
     * meses anteriores sem chamadas adicionais durante a conversa.
     */
    private String buildSystemPrompt(String authHeader) {
        YearMonth now = YearMonth.now();
        YearMonth prev = now.minusMonths(1);

        // Current and previous month summaries (with category breakdown)
        FinanceDataService.SummaryInfo current = financeDataService.getSummary(
                now.getYear(), now.getMonthValue(), authHeader);
        FinanceDataService.SummaryInfo previous = financeDataService.getSummary(
                prev.getYear(), prev.getMonthValue(), authHeader);

        // Last 6 months trend
        List<FinanceDataService.TrendInfo> trend = financeDataService.getMonthlyTrend(authHeader);

        // Format trend table
        String trendText = trend.stream()
                .map(t -> String.format("  %s: receitas=%.2f€, despesas=%.2f€, saldo=%.2f€",
                        t.month(), t.income(), t.expenses(), t.income().subtract(t.expenses())))
                .collect(Collectors.joining("\n"));

        // Format current month categories
        String currentCats = current.topCategories().stream()
                .map(c -> String.format("  - %s: %.2f€ (%.1f%%)", c.name(), c.amount(), c.percentage()))
                .collect(Collectors.joining("\n"));

        // Format previous month categories
        String prevCats = previous.topCategories().stream()
                .map(c -> String.format("  - %s: %.2f€ (%.1f%%)", c.name(), c.amount(), c.percentage()))
                .collect(Collectors.joining("\n"));

        return String.format("""
                És o assistente financeiro pessoal do utilizador. Responde SEMPRE em português de Portugal.
                Tens acesso completo aos dados financeiros abaixo. Usa-os para responder com precisão.
                Nunca inventes valores — usa apenas os dados fornecidos. Se não tiveres dados de um período específico, diz isso claramente.
                Nunca dês conselhos de investimento específicos.

                === HISTÓRICO DOS ÚLTIMOS 6 MESES ===
                %s

                === MÊS ATUAL (%s) ===
                Receitas: %.2f€ | Despesas: %.2f€ | Saldo: %.2f€ | Taxa de poupança: %.1f%%
                Top categorias de despesa:
                %s

                === MÊS ANTERIOR (%s) ===
                Receitas: %.2f€ | Despesas: %.2f€ | Saldo: %.2f€ | Taxa de poupança: %.1f%%
                Top categorias de despesa:
                %s
                """,
                trendText.isEmpty() ? "  (sem dados históricos disponíveis)" : trendText,
                now,
                current.totalIncome(), current.totalExpenses(), current.balance(), current.savingsRate(),
                currentCats.isEmpty() ? "  (sem dados)" : currentCats,
                prev,
                previous.totalIncome(), previous.totalExpenses(), previous.balance(), previous.savingsRate(),
                prevCats.isEmpty() ? "  (sem dados)" : prevCats);
    }

    private List<Map<String, String>> loadHistory(String key) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveHistory(String key, List<Map<String, String>> history) {
        try {
            redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(history),
                    CHAT_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Failed to save chat history: {}", e.getMessage());
        }
    }
}
