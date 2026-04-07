package com.smartfinance.ai.consumer;

import com.smartfinance.ai.service.CategorizationService;
import com.smartfinance.ai.service.FinanceDataService;
import com.smartfinance.shared.event.CategorizationCompletedEvent;
import com.smartfinance.shared.event.TransactionImportedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionImportedConsumer {

    private final CategorizationService categorizationService;
    private final FinanceDataService financeDataService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @RabbitListener(queues = "${rabbitmq.queues.transactions-imported}")
    public void handle(TransactionImportedEvent event) {
        log.info("Received TransactionImportedEvent: importId={}, {} transactions",
                event.importId(), event.transactionIds().size());

        List<FinanceDataService.SimpleCategoryInfo> categories =
                financeDataService.getCategoriesInternal(event.userId());

        if (categories.isEmpty()) {
            log.warn("No categories found for user {}, skipping categorization", event.userId());
            return;
        }

        for (var txId : event.transactionIds()) {
            try {
                categorizationService.categorize(txId, event.userId(), categories)
                        .ifPresent(result -> {
                            CategorizationCompletedEvent completedEvent = new CategorizationCompletedEvent(
                                    txId, result.categoryId(), result.confidence());
                            rabbitTemplate.convertAndSend(
                                    exchange, CategorizationCompletedEvent.ROUTING_KEY, completedEvent);
                            log.debug("Categorized transaction {} → category {}", txId, result.categoryId());
                        });

                // Small delay to avoid hitting Groq rate limits
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("Failed to categorize transaction {}: {}", txId, e.getMessage());
            }
        }

        log.info("Categorization complete for importId={}", event.importId());
    }
}
