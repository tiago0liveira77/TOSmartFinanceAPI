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

    @RabbitListener(queues = "#{@aiCategorizationCompletedQueue.name}")
    @Transactional
    public void handleCategorizationCompleted(CategorizationCompletedEvent event) {
        log.debug("Received categorization: transactionId={}, categoryId={}",
                event.transactionId(), event.categoryId());

        transactionRepository.findById(event.transactionId()).ifPresent(transaction -> {
            categoryRepository.findById(event.categoryId()).ifPresent(category -> {
                transaction.setCategory(category);
                transaction.setAiCategorized(true);
                transaction.setAiConfidence(event.confidence());
                transactionRepository.save(transaction);
            });
        });
    }
}