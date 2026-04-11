package com.smartfinance.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acede aos dados financeiros do finance-service via HTTP REST.
 *
 * DOIS MODOS DE AUTENTICAÇÃO:
 *
 * 1. userEntity(authHeader) — para chamadas originadas de controllers REST:
 *    O JWT do utilizador é propagado via "Authorization: Bearer {token}".
 *    O finance-service valida o JWT via JwtAuthenticationFilter e extrai o userId.
 *    Usar quando: InsightService, ForecastService, ChatService (têm o JWT do utilizador).
 *
 * 2. internalEntity(userId) — para chamadas do RabbitMQ consumer:
 *    O consumer assíncrono não tem JWT do utilizador — fluxo desacoplado do request HTTP.
 *    Usa o header proprietário "X-Internal-Service: ai-service" + "X-User-Id: {uuid}".
 *    O finance-service (InternalServiceFilter) reconhece este header e define autenticação
 *    de serviço sem validar JWT.
 *    SEGURANÇA: Este mecanismo assume rede interna segura (Docker). O api-gateway nunca
 *    deve fazer pass-through do header X-Internal-Service ao exterior.
 *    Usar quando: CategorizationService, TransactionImportedConsumer (sem JWT disponível).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceDataService {

    private final RestTemplate financeRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.finance-service.url}")
    private String financeServiceUrl;

    // -----------------------------------------------------------------------
    // Construção de headers de autenticação
    // -----------------------------------------------------------------------

    /** Headers para chamadas autenticadas com JWT do utilizador (controllers REST). */
    private HttpEntity<Void> userEntity(String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        return new HttpEntity<>(headers);
    }

    /** Headers para chamadas inter-serviço sem JWT (RabbitMQ consumers). */
    private HttpEntity<Void> internalEntity(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "ai-service");
        headers.set("X-User-Id", userId.toString());
        return new HttpEntity<>(headers);
    }

    // -----------------------------------------------------------------------
    // Monthly Summary
    // -----------------------------------------------------------------------

    public record SummaryInfo(
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal balance,
            BigDecimal savingsRate,
            List<CategoryInfo> topCategories
    ) {}

    public record CategoryInfo(UUID id, String name, BigDecimal amount, double percentage) {}

    public SummaryInfo getSummary(int year, int month, String authHeader) {
        String url = financeServiceUrl + "/api/v1/reports/summary?year={y}&month={m}";
        log.debug("[AI][HTTP] GET finance-service/reports/summary: year={}, month={}", year, month);
        try {
            ResponseEntity<String> response = financeRestTemplate.exchange(
                    url, HttpMethod.GET, userEntity(authHeader), String.class, year, month);
            JsonNode data = objectMapper.readTree(response.getBody()).get("data");
            List<CategoryInfo> cats = new ArrayList<>();
            if (data.has("topCategories")) {
                for (JsonNode cat : data.get("topCategories")) {
                    cats.add(new CategoryInfo(
                            UUID.fromString(cat.get("categoryId").asText()),
                            cat.get("categoryName").asText(),
                            new BigDecimal(cat.get("amount").asText()),
                            cat.get("percentage").asDouble()));
                }
            }
            return new SummaryInfo(
                    new BigDecimal(data.get("totalIncome").asText()),
                    new BigDecimal(data.get("totalExpenses").asText()),
                    new BigDecimal(data.get("balance").asText()),
                    new BigDecimal(data.get("savingsRate").asText()),
                    cats);
        } catch (Exception e) {
            log.warn("[AI][HTTP] Failed to get summary from finance-service: year={}, month={}, error={}", year, month, e.getMessage());
            return new SummaryInfo(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
    }

    // -----------------------------------------------------------------------
    // Monthly Trend
    // -----------------------------------------------------------------------

    public record TrendInfo(String month, BigDecimal income, BigDecimal expenses) {}

    public List<TrendInfo> getMonthlyTrend(String authHeader) {
        String url = financeServiceUrl + "/api/v1/reports/monthly-trend?months=6";
        log.debug("[AI][HTTP] GET finance-service/reports/monthly-trend: months=6");
        try {
            ResponseEntity<String> response = financeRestTemplate.exchange(
                    url, HttpMethod.GET, userEntity(authHeader), String.class);
            JsonNode data = objectMapper.readTree(response.getBody()).get("data");
            List<TrendInfo> result = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    result.add(new TrendInfo(
                            item.get("month").asText(),
                            new BigDecimal(item.get("income").asText()),
                            new BigDecimal(item.get("expenses").asText())));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[AI][HTTP] Failed to get monthly trend from finance-service: error={}", e.getMessage());
            return List.of();
        }
    }

    // -----------------------------------------------------------------------
    // Categories (internal — for RabbitMQ consumer)
    // -----------------------------------------------------------------------

    public record SimpleCategoryInfo(UUID id, String name, String type) {}

    public List<SimpleCategoryInfo> getCategoriesInternal(UUID userId) {
        String url = financeServiceUrl + "/api/v1/categories";
        try {
            ResponseEntity<String> response = financeRestTemplate.exchange(
                    url, HttpMethod.GET, internalEntity(userId), String.class);
            JsonNode data = objectMapper.readTree(response.getBody()).get("data");
            List<SimpleCategoryInfo> result = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode cat : data) {
                    result.add(new SimpleCategoryInfo(
                            UUID.fromString(cat.get("id").asText()),
                            cat.get("name").asText(),
                            cat.get("type").asText()));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[AI][HTTP] Failed to get categories from finance-service: userId={}, error={}", userId, e.getMessage());
            return List.of();
        }
    }

    // -----------------------------------------------------------------------
    // Categorization hints (internal — for RabbitMQ consumer)
    // -----------------------------------------------------------------------

    /**
     * Consulta o finance-service por um hint de categorização para a descrição dada.
     * Devolve o categoryId se existir match, ou empty se a AI deve ser chamada.
     */
    public Optional<UUID> findCategorizationHint(UUID userId, String description) {
        String url = financeServiceUrl + "/api/v1/categorization-hints/match?userId={userId}&description={description}";
        try {
            ResponseEntity<String> response = financeRestTemplate.exchange(
                    url, HttpMethod.GET, internalEntity(userId), String.class, userId, description);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode body = objectMapper.readTree(response.getBody());
                return Optional.of(UUID.fromString(body.get("categoryId").asText()));
            }
        } catch (Exception e) {
            // 204 No Content ou erro de rede → deixar a AI decidir
            log.debug("[AI][HINTS] No hint found for userId={} description='{}': {}", userId, description, e.getMessage());
        }
        return Optional.empty();
    }

    // -----------------------------------------------------------------------
    // Transaction detail (internal — for RabbitMQ consumer)
    // -----------------------------------------------------------------------

    public record TransactionInfo(String description, BigDecimal amount, String type) {}

    public TransactionInfo getTransactionInternal(UUID transactionId, UUID userId) {
        String url = financeServiceUrl + "/api/v1/transactions/{id}";
        try {
            ResponseEntity<String> response = financeRestTemplate.exchange(
                    url, HttpMethod.GET, internalEntity(userId), String.class, transactionId);
            JsonNode data = objectMapper.readTree(response.getBody()).get("data");
            return new TransactionInfo(
                    data.get("description").asText(""),
                    new BigDecimal(data.get("amount").asText()),
                    data.get("type").asText());
        } catch (Exception e) {
            log.warn("[AI][HTTP] Failed to get transaction from finance-service: txId={}, error={}", transactionId, e.getMessage());
            return null;
        }
    }
}
