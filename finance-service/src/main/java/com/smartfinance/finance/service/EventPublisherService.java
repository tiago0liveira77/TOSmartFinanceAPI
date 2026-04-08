package com.smartfinance.finance.service;

import com.smartfinance.finance.config.RabbitMQProperties;
import com.smartfinance.shared.event.BudgetThresholdEvent;
import com.smartfinance.shared.event.TransactionCreatedEvent;
import com.smartfinance.shared.event.TransactionImportedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publica eventos de domínio para o exchange RabbitMQ "smartfinance.events" (TopicExchange).
 *
 * FLUXO DE MENSAGENS:
 *   TransactionImportedEvent  → routing key "transactions.imported"
 *       └─ Consumido por: ai-service (categorização automática de transações)
 *
 *   TransactionCreatedEvent   → routing key "transaction.created"
 *       └─ Consumido por: notification-service (verificação de alertas do utilizador)
 *
 *   BudgetThresholdEvent      → routing key "budget.threshold.reached"
 *       └─ Consumido por: notification-service (envio de alerta de orçamento)
 *
 * SERIALIZAÇÃO: JSON via Jackson2JsonMessageConverter (configurado em RabbitMQConfig).
 * ERRO: Se o RabbitMQ não estiver disponível, a exceção propaga-se para o caller.
 *       Operações de negócio não devem continuar silenciosamente sem publicar eventos críticos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties props;

    public void publishTransactionImported(TransactionImportedEvent event) {
        try {
            rabbitTemplate.convertAndSend(props.exchange(), TransactionImportedEvent.ROUTING_KEY, event);
            log.info("[FINANCE][RABBIT] Published TransactionImportedEvent: importId={}, txCount={}",
                    event.importId(), event.transactionIds().size());
        } catch (Exception e) {
            log.error("[FINANCE][RABBIT] Failed to publish TransactionImportedEvent: importId={}, error={}",
                    event.importId(), e.getMessage());
            throw e;
        }
    }

    public void publishTransactionCreated(TransactionCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(props.exchange(), TransactionCreatedEvent.ROUTING_KEY, event);
            log.info("[FINANCE][RABBIT] Published TransactionCreatedEvent: txId={}", event.transactionId());
        } catch (Exception e) {
            log.error("[FINANCE][RABBIT] Failed to publish TransactionCreatedEvent: txId={}, error={}",
                    event.transactionId(), e.getMessage());
            throw e;
        }
    }

    public void publishBudgetThreshold(BudgetThresholdEvent event) {
        try {
            rabbitTemplate.convertAndSend(props.exchange(), BudgetThresholdEvent.ROUTING_KEY, event);
            log.info("[FINANCE][RABBIT] Published BudgetThresholdEvent: budgetId={}, pct={}%",
                    event.budgetId(), event.percentage());
        } catch (Exception e) {
            log.error("[FINANCE][RABBIT] Failed to publish BudgetThresholdEvent: budgetId={}, error={}",
                    event.budgetId(), e.getMessage());
            throw e;
        }
    }
}
