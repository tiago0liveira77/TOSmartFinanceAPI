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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceDataService {

    private final RestTemplate financeRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${services.finance-service.url}")
    private String financeServiceUrl;

    // -----------------------------------------------------------------------
    // Headers
    // -----------------------------------------------------------------------

    private HttpEntity<Void> userEntity(String authHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        return new HttpEntity<>(headers);
    }

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
            log.warn("Failed to get summary from finance-service: {}", e.getMessage());
            return new SummaryInfo(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
    }

    // -----------------------------------------------------------------------
    // Monthly Trend
    // -----------------------------------------------------------------------

    public record TrendInfo(String month, BigDecimal income, BigDecimal expenses) {}

    public List<TrendInfo> getMonthlyTrend(String authHeader) {
        String url = financeServiceUrl + "/api/v1/reports/monthly-trend?months=6";
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
            log.warn("Failed to get trend from finance-service: {}", e.getMessage());
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
            log.warn("Failed to get categories from finance-service: {}", e.getMessage());
            return List.of();
        }
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
            log.warn("Failed to get transaction {} from finance-service: {}", transactionId, e.getMessage());
            return null;
        }
    }
}
