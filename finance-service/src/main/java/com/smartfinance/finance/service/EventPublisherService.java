package com.smartfinance.finance.service;

import com.smartfinance.finance.config.RabbitMQProperties;
import com.smartfinance.shared.event.BudgetThresholdEvent;
import com.smartfinance.shared.event.TransactionCreatedEvent;
import com.smartfinance.shared.event.TransactionImportedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties props;

    public void publishTransactionImported(TransactionImportedEvent event) {
        rabbitTemplate.convertAndSend(props.exchange(),
                TransactionImportedEvent.ROUTING_KEY, event);
        log.debug("Published TransactionImportedEvent: importId={}", event.importId());
    }

    public void publishTransactionCreated(TransactionCreatedEvent event) {
        rabbitTemplate.convertAndSend(props.exchange(),
                TransactionCreatedEvent.ROUTING_KEY, event);
        log.debug("Published TransactionCreatedEvent: transactionId={}", event.transactionId());
    }

    public void publishBudgetThreshold(BudgetThresholdEvent event) {
        rabbitTemplate.convertAndSend(props.exchange(),
                BudgetThresholdEvent.ROUTING_KEY, event);
        log.debug("Published BudgetThresholdEvent: budgetId={}, percentage={}",
                event.budgetId(), event.percentage());
    }
}
