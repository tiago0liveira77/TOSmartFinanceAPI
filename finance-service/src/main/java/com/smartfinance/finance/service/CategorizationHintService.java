package com.smartfinance.finance.service;

import com.smartfinance.finance.entity.Category;
import com.smartfinance.finance.entity.CategorizationHint;
import com.smartfinance.finance.repository.CategorizationHintRepository;
import com.smartfinance.shared.util.DescriptionNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategorizationHintService {

    private final CategorizationHintRepository hintRepository;

    /**
     * Grava ou atualiza um hint de categorização para o utilizador.
     *
     * Se já existir um hint para este (userId, descriptionPattern):
     *   - Se a categoria é igual → incrementa occurrence_count
     *   - Se a categoria mudou → atualiza a categoria e reinicia occurrence_count para 1
     *
     * A descrição é normalizada antes de guardar para que o lookup seja consistente.
     *
     * @param userId      utilizador que fez a atribuição
     * @param rawDescription descrição original da transação
     * @param category    categoria atribuída manualmente
     */
    @Transactional
    public void saveOrUpdate(UUID userId, String rawDescription, Category category) {
        if (rawDescription == null || rawDescription.isBlank()) return;

        String normalized = DescriptionNormalizer.normalize(rawDescription);
        if (normalized == null || normalized.isBlank()) return;

        Optional<CategorizationHint> existing =
                hintRepository.findByUserIdAndDescriptionPattern(userId, normalized);

        if (existing.isPresent()) {
            CategorizationHint hint = existing.get();
            if (hint.getCategory().getId().equals(category.getId())) {
                hint.setOccurrenceCount(hint.getOccurrenceCount() + 1);
                log.debug("[HINTS] Incremented hint: userId={} pattern='{}' count={}",
                        userId, normalized, hint.getOccurrenceCount());
            } else {
                // Utilizador corrigiu a categoria — atualiza e reinicia contador
                hint.setCategory(category);
                hint.setOccurrenceCount(1);
                log.debug("[HINTS] Updated hint category: userId={} pattern='{}' newCategory={}",
                        userId, normalized, category.getName());
            }
            hintRepository.save(hint);
        } else {
            hintRepository.save(new CategorizationHint(userId, normalized, category));
            log.debug("[HINTS] Created hint: userId={} pattern='{}' category={}",
                    userId, normalized, category.getName());
        }
    }

    /**
     * Procura o melhor hint para uma descrição.
     * Usado pelo ai-service via endpoint interno antes de chamar a API de AI.
     *
     * @return categoryId do melhor match, ou empty se não houver hint suficientemente bom
     */
    public Optional<UUID> findBestMatch(UUID userId, String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) return Optional.empty();

        String normalized = DescriptionNormalizer.normalize(rawDescription);
        return hintRepository.findBestMatch(userId, normalized)
                .map(hint -> hint.getCategory().getId());
    }
}
