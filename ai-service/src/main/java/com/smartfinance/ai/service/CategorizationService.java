package com.smartfinance.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfinance.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategorizationService {

    private final AiClientService aiClient;
    private final AiProperties aiProperties;
    private final FinanceDataService financeDataService;
    private final ObjectMapper objectMapper;

    public record CategoryResult(UUID categoryId, BigDecimal confidence) {}

    public Optional<CategoryResult> categorize(
            UUID transactionId, UUID userId,
            List<FinanceDataService.SimpleCategoryInfo> categories) {

        FinanceDataService.TransactionInfo tx = financeDataService.getTransactionInternal(transactionId, userId);
        if (tx == null || tx.description() == null || tx.description().isBlank()) {
            return Optional.empty();
        }

        String categoryNames = categories.stream()
                .filter(c -> c.type().equals(tx.type()))
                .map(FinanceDataService.SimpleCategoryInfo::name)
                .collect(Collectors.joining(", "));

        if (categoryNames.isBlank()) {
            return Optional.empty();
        }

        String prompt = String.format("""
                Categoriza esta transação bancária.
                Descrição: '%s'
                Valor: %.2f€
                Tipo: %s
                Categorias disponíveis: %s

                Responde APENAS com JSON: {"category": "NomeDaCategoria", "confidence": 0.95}
                Escolhe a categoria mais adequada da lista. Se não tiver correspondência clara, usa "Outros".
                """, tx.description(), tx.amount(), tx.type(), categoryNames);

        try {
            List<Map<String, String>> messages = List.of(
                    AiClientService.system("És um categorizador de transações bancárias. Responde apenas com JSON válido."),
                    AiClientService.user(prompt)
            );
            String content = InsightService.stripMarkdownFences(aiClient.completeFast(messages));

            JsonNode root = objectMapper.readTree(content);
            String categoryName = root.get("category").asText();
            double confidenceVal = root.path("confidence").asDouble(0.7);

            Optional<FinanceDataService.SimpleCategoryInfo> matched = categories.stream()
                    .filter(c -> c.name().equalsIgnoreCase(categoryName) ||
                            c.name().toLowerCase().contains(categoryName.toLowerCase()) ||
                            categoryName.toLowerCase().contains(c.name().toLowerCase()))
                    .findFirst();

            return matched.map(c -> new CategoryResult(
                    c.id(),
                    BigDecimal.valueOf(confidenceVal).setScale(2, RoundingMode.HALF_UP)));

        } catch (Exception e) {
            log.warn("Categorization failed for transaction {}: {}", transactionId, e.getMessage());
            return Optional.empty();
        }
    }
}
