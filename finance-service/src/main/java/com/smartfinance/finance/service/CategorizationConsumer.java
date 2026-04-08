package com.smartfinance.finance.service;

import com.smartfinance.finance.entity.Category;
import com.smartfinance.finance.repository.CategoryRepository;
import com.smartfinance.finance.repository.TransactionRepository;
import com.smartfinance.shared.event.CategorizationCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategorizationConsumer {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Consome eventos "ai.categorization.completed" publicados pelo ai-service.
     *
     * O ai-service categoriza cada transação individualmente e publica um evento
     * com o UUID da categoria sugerida e um score de confiança (0.0 a 1.0).
     * Este consumer atualiza a transação na base de dados e marca ai_categorized=true.
     *
     * COMPORTAMENTO EM CASO DE ENTIDADE NÃO ENCONTRADA:
     *   Se a transação ou a categoria não existirem na DB, o evento é descartado
     *   silenciosamente (sem retry, sem DLQ). Isto pode acontecer se:
     *     1. A transação foi apagada (soft delete) entre o import e a categorização
     *     2. O categoryId devolvido pelo ai-service não existe (ex: categoria entretanto apagada)
     *   Nestas situações a transação fica sem categoria — limitação aceite para a fase atual.
     */
    @RabbitListener(queues = "#{@aiCategorizationCompletedQueue.name}")
    @Transactional
    public void handleCategorizationCompleted(CategorizationCompletedEvent event) {
        log.info("[FINANCE][RABBIT] Categorization event received: txId={}, categoryId={}, confidence={}",
                event.transactionId(), event.categoryId(), event.confidence());

        // Duplo ifPresent: se transação ou categoria não existirem, descarta silenciosamente
        transactionRepository.findById(event.transactionId()).ifPresentOrElse(
                transaction -> categoryRepository.findById(event.categoryId()).ifPresentOrElse(
                        category -> {
                            transaction.setCategory(category);
                            transaction.setAiCategorized(true);
                            transaction.setAiConfidence(event.confidence());
                            transactionRepository.save(transaction);
                            log.debug("[FINANCE][RABBIT] Transaction categorized: txId={}, category={}",
                                    event.transactionId(), category.getName());
                        },
                        () -> log.warn("[FINANCE][RABBIT] Category not found for categorization: categoryId={}, txId={}",
                                event.categoryId(), event.transactionId())
                ),
                () -> log.warn("[FINANCE][RABBIT] Transaction not found for categorization: txId={}",
                        event.transactionId())
        );
    }
}
