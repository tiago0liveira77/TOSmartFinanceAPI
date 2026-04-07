package com.smartfinance.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfinance.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP direto para qualquer API OpenAI-compatible (Groq, OpenAI, etc.).
 * Evita dependência na SDK openai-java e funciona com qualquer provider.
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

    /**
     * Constrói uma mensagem de sistema.
     */
    public static Map<String, String> system(String content) {
        return Map.of("role", "system", "content", content);
    }

    /**
     * Constrói uma mensagem de utilizador.
     */
    public static Map<String, String> user(String content) {
        return Map.of("role", "user", "content", content);
    }

    /**
     * Constrói uma mensagem de assistente.
     */
    public static Map<String, String> assistant(String content) {
        return Map.of("role", "assistant", "content", content);
    }

    /**
     * Chama a API de chat completions e devolve o conteúdo da resposta.
     *
     * @param model    nome do modelo (ex: "llama-3.3-70b-versatile")
     * @param messages lista de mensagens [{role, content}, ...]
     * @param maxTokens limite de tokens na resposta
     * @param temperature 0.0 (determinístico) a 1.0 (criativo)
     * @return conteúdo da resposta do modelo
     */
    public String complete(String model, List<Map<String, String>> messages,
                           int maxTokens, double temperature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(aiProperties.apiKey());

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "max_tokens", maxTokens,
                "temperature", temperature
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        String url = aiProperties.baseUrl() + "/chat/completions";

        try {
            ResponseEntity<String> response = aiRestTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("choices").get(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.error("AI API call failed [model={}]: {}", model, e.getMessage());
            throw new RuntimeException("AI API call failed: " + e.getMessage(), e);
        }
    }

    /** Atalho usando o modelo padrão (llama-3.3-70b-versatile). */
    public String complete(List<Map<String, String>> messages) {
        return complete(aiProperties.model(), messages, aiProperties.maxTokens(), aiProperties.temperature());
    }

    /** Atalho usando o modelo rápido (llama-3.1-8b-instant). */
    public String completeFast(List<Map<String, String>> messages) {
        return complete(aiProperties.modelFast(), messages, 200, 0.1);
    }

    /** Atalho para respostas longas (ex: previsão com múltiplos meses e categorias). */
    public String completeLong(List<Map<String, String>> messages) {
        return complete(aiProperties.model(), messages, 2000, aiProperties.temperature());
    }
}
