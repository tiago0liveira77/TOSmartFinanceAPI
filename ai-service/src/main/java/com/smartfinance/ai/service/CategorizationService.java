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
    private final DescriptionNormalizer descriptionNormalizer;
    private final ObjectMapper objectMapper;

    public record CategoryResult(UUID categoryId, BigDecimal confidence) {}

    // -----------------------------------------------------------------------
    // Few-shot examples por tipo de transação
    // Usam descrições já normalizadas (pós-DescriptionNormalizer) para que o
    // modelo veja exemplos no mesmo formato que a transação a categorizar.
    // -----------------------------------------------------------------------

    private static final String FEW_SHOT_EXPENSE = """
            Exemplos de DESPESAS categorizadas:
            "Compra em CONTINENTE MODELO LOJA" → Alimentação
            "Compra em PINGO DOCE SUPERMERCADO" → Alimentação
            "Compra em MCDONALDS" → Alimentação
            "Compra em Estação de Serviço GALP Combustível" → Transporte
            "Compra em Estação de Serviço BP" → Transporte
            "Débito Direto SEGURO AUTOMOVEL FIDELIDADE" → Transporte
            "Débito Direto NOS COMUNICACOES" → Habitação
            "Débito Direto EDP COMERCIAL ENERGIA" → Habitação
            "Débito Direto VODAFONE PORTUGAL" → Habitação
            "Débito Direto Agua e Saneamento MUNICIPIO" → Habitação
            "MB Way" → Transferências
            "Transferência para" → Transferências
            "Compra em FARMACIA NOVA" → Saúde
            "Compra em WELLS SAUDE E BELEZA" → Saúde
            "Débito Direto PayPal Europe" → Tecnologia
            "Compra em APPLE COM BILL" → Tecnologia
            "Compra em NETFLIX" → Entretenimento
            "Compra em SPOTIFY" → Entretenimento
            "Compra em CINEMA NOS" → Entretenimento
            "Compra em ZARA" → Vestuário
            "Compra em H&M" → Vestuário
            "Compra em DECATHLON" → Desporto
            "Débito Direto GINASIO HOLMES PLACE" → Desporto
            "Compra em FNAC" → Tecnologia
            "Compra em AMAZON" → Tecnologia
            "Compra em WORTEN" → Tecnologia
            "Pagamento de Serviços CGD CARTAO CREDITO" → Outros
            "Comissão de Manutenção CONTA" → Outros
            """;

    private static final String FEW_SHOT_INCOME = """
            Exemplos de RECEITAS categorizadas:
            "Transferência de EMPRESA EMPREGADORA ORDENADO" → Salário
            "Transferência de PROCESSAMETO SALARIOS" → Salário
            "Transferência de VENCIMENTO" → Salário
            "Transferência de SUBSIDO FERIAS" → Salário
            "Transferência de SUBSIDIO NATAL" → Salário
            "Transferência de HONORARIOS SERVICOS" → Freelance
            "Transferência de RECIBO VERDE" → Freelance
            "Transferência de REEMBOLSO IRS" → Impostos
            "Transferência de SEGURANCA SOCIAL PRESTACAO" → Outros Rendimentos
            "Transferência de RENDIMENTO DEPOSITO PRAZO" → Investimentos
            "Transferência de JUROS Credores" → Investimentos
            """;

    // -----------------------------------------------------------------------
    // Categorização
    // -----------------------------------------------------------------------

    public Optional<CategoryResult> categorize(
            UUID transactionId, UUID userId,
            List<FinanceDataService.SimpleCategoryInfo> categories) {

        FinanceDataService.TransactionInfo tx = financeDataService.getTransactionInternal(transactionId, userId);
        if (tx == null || tx.description() == null || tx.description().isBlank()) {
            return Optional.empty();
        }

        List<FinanceDataService.SimpleCategoryInfo> filteredCategories = categories.stream()
                .filter(c -> c.type().equals(tx.type()))
                .toList();

        String categoryNames = filteredCategories.stream()
                .map(FinanceDataService.SimpleCategoryInfo::name)
                .collect(Collectors.joining(", "));

        if (categoryNames.isBlank()) {
            return Optional.empty();
        }

        String normalizedDescription = descriptionNormalizer.normalize(tx.description());
        log.debug("[CATEGORIZATION] txId={} original='{}' normalized='{}'",
                transactionId, tx.description(), normalizedDescription);

        String fewShot = "INCOME".equals(tx.type()) ? FEW_SHOT_INCOME : FEW_SHOT_EXPENSE;

        String prompt = String.format("""
                Categoriza transações bancárias portuguesas.

                %s
                Categorias disponíveis: %s

                Transação a categorizar:
                Descrição: '%s'
                Valor: %.2f€

                Responde APENAS com JSON: {"category": "NomeDaCategoria", "confidence": 0.95}
                Escolhe a categoria mais adequada da lista. Se não houver correspondência clara, usa "Outros".
                """, fewShot, categoryNames, normalizedDescription, tx.amount());

        log.debug("[CATEGORIZATION] txId={} prompt=\n{}", transactionId, prompt);

        try {
            List<Map<String, String>> messages = List.of(
                    AiClientService.system("És um categorizador de transações bancárias portuguesas. Responde apenas com JSON válido, sem texto adicional."),
                    AiClientService.user(prompt)
            );
            String content = InsightService.stripMarkdownFences(aiClient.completeFast(messages));
            log.debug("[CATEGORIZATION] txId={} response='{}'", transactionId, content);

            JsonNode root = objectMapper.readTree(content);
            String categoryName = root.get("category").asText();
            double confidenceVal = root.path("confidence").asDouble(0.7);

            // Matching por prioridade: exato > categoria contém > retorno contém (só se > 4 chars)
            Optional<FinanceDataService.SimpleCategoryInfo> matched = filteredCategories.stream()
                    .filter(c -> c.name().equalsIgnoreCase(categoryName))
                    .findFirst()
                    .or(() -> filteredCategories.stream()
                            .filter(c -> c.name().toLowerCase().contains(categoryName.toLowerCase()))
                            .findFirst())
                    .or(() -> filteredCategories.stream()
                            .filter(c -> categoryName.toLowerCase().contains(c.name().toLowerCase())
                                    && c.name().length() > 4)
                            .findFirst());

            return matched.map(c -> new CategoryResult(
                    c.id(),
                    BigDecimal.valueOf(confidenceVal).setScale(2, RoundingMode.HALF_UP)));

        } catch (Exception e) {
            log.warn("Categorization failed for transaction {}: {}", transactionId, e.getMessage());
            return Optional.empty();
        }
    }
}
