package com.smartfinance.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfinance.ai.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP direto para qualquer API OpenAI-compatible (Groq, OpenAI, etc.).
 *
 * Usa RestTemplate em vez do SDK openai-java para compatibilidade com providers alternativos
 * (Groq, Azure OpenAI, Ollama, etc.) sem depender de tipos específicos do SDK.
 *
 * PROVIDER ATUAL: Groq (https://api.groq.com/openai/v1)
 *   - Modelo qualidade: llama-3.3-70b-versatile (insights, chat, forecast)
 *   - Modelo velocidade: llama-3.1-8b-instant (categorização, onde latência importa)
 *
 * SEGURANÇA: A API key é enviada via Authorization: Bearer, mas NUNCA deve ser logada.
 *   Ver comentário explícito no método complete().
 */
@Service
@Slf4j
public class AiClientService {

    private final RestTemplate aiRestTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiClientService(@Qualifier("aiRestTemplate") RestTemplate aiRestTemplate,
                           AiProperties aiProperties,
                           ObjectMapper objectMapper) {
        this.aiRestTemplate = aiRestTemplate;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    /** Constrói uma mensagem de sistema para o chat completions. */
    public static Map<String, String> system(String content) {
        return Map.of("role", "system", "content", content);
    }

    /** Constrói uma mensagem de utilizador para o chat completions. */
    public static Map<String, String> user(String content) {
        return Map.of("role", "user", "content", content);
    }

    /** Constrói uma mensagem de assistente (para histórico de conversa). */
    public static Map<String, String> assistant(String content) {
        return Map.of("role", "assistant", "content", content);
    }

    /**
     * Chama a API de chat completions e devolve o conteúdo da resposta.
     *
     * @param model       nome do modelo (ex: "llama-3.3-70b-versatile")
     * @param messages    lista de mensagens [{role, content}, ...]
     * @param maxTokens   limite de tokens na resposta
     * @param temperature 0.0 (determinístico) a 1.0 (criativo)
     * @return conteúdo textual da resposta do modelo
     */
    public String complete(String model, List<Map<String, String>> messages,
                           int maxTokens, double temperature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // NUNCA logar aiProperties.apiKey() — exporia a chave de API nos logs
        headers.setBearerAuth(aiProperties.apiKey());

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", maxTokens,
                "temperature", temperature
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = aiProperties.baseUrl() + "/chat/completions";

        log.info("[AI][GROQ] Calling model={}, messages={}, maxTokens={}, temperature={}",
                model, messages.size(), maxTokens, temperature);

        try {
            ResponseEntity<String> response = aiRestTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").get(0).path("message").path("content").asText("");
            log.debug("[AI][GROQ] Response received: model={}, length={} chars", model, content.length());
            return content;
        } catch (Exception e) {
            log.error("[AI][GROQ] API call failed: model={}, error={}", model, e.getMessage());
            throw new RuntimeException("AI API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Atalho usando o modelo padrão (llama-3.3-70b-versatile) com os parâmetros globais.
     * Usar para: insights, forecast, chat.
     */
    public String complete(List<Map<String, String>> messages) {
        return complete(aiProperties.model(), messages, aiProperties.maxTokens(), aiProperties.temperature());
    }

    /**
     * Atalho usando o modelo rápido (llama-3.1-8b-instant).
     * Usar para: categorização de transações, onde velocidade > qualidade e maxTokens=200 é suficiente.
     */
    public String completeFast(List<Map<String, String>> messages) {
        return complete(aiProperties.modelFast(), messages, 200, 0.1);
    }

    /**
     * Atalho para respostas longas (ex: forecast com 3 meses × múltiplas categorias).
     * maxTokens=2000 evita truncagem do JSON de resposta que causaria falha de parsing.
     */
    public String completeLong(List<Map<String, String>> messages) {
        return complete(aiProperties.model(), messages, 2000, aiProperties.temperature());
    }
}
