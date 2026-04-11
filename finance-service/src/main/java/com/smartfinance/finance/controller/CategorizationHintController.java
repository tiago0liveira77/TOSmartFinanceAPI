package com.smartfinance.finance.controller;

import com.smartfinance.finance.service.CategorizationHintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoint interno — acessível apenas via X-Internal-Service header (ai-service).
 * Não exposto ao exterior pelo api-gateway.
 *
 * Permite ao ai-service verificar se existe um hint de categorização
 * antes de chamar a API de AI, evitando chamadas redundantes.
 */
@RestController
@RequestMapping("/api/v1/categorization-hints")
@RequiredArgsConstructor
public class CategorizationHintController {

    private final CategorizationHintService hintService;

    /**
     * Retorna o categoryId do melhor hint para a descrição fornecida.
     *
     * GET /api/v1/categorization-hints/match?description=Débito+Direto+PayPal+Europe
     * Headers: X-Internal-Service: ai-service, X-User-Id: {uuid}
     *
     * Response 200: { "categoryId": "uuid" }   — hint encontrado
     * Response 204: (sem body)                  — sem hint, chamar AI
     */
    @GetMapping("/match")
    public ResponseEntity<Map<String, String>> findMatch(
            @RequestParam("userId") UUID userId,
            @RequestParam("description") String description) {

        return hintService.findBestMatch(userId, description)
                .map(categoryId -> ResponseEntity.ok(Map.of("categoryId", categoryId.toString())))
                .orElse(ResponseEntity.noContent().build());
    }
}
