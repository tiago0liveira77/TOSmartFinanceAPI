package com.smartfinance.finance.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private final RabbitMQProperties props;

    @Bean
    public TopicExchange smartFinanceExchange() {
        return new TopicExchange(props.exchange(), true, false);
    }

    @Bean
    public Queue transactionsImportedQueue() {
        return new Queue(props.queues().get("transactions-imported"), true);
    }

    @Bean
    public Queue transactionCreatedQueue() {
        return new Queue(props.queues().get("transaction-created"), true);
    }

    @Bean
    public Queue budgetThresholdQueue() {
        return new Queue(props.queues().get("budget-threshold"), true);
    }

    @Bean
    public Queue aiCategorizationCompletedQueue() {
        return new Queue(props.queues().get("ai-categorization-completed"), true);
    }

    @Bean
    public Binding transactionsImportedBinding() {
        return BindingBuilder.bind(transactionsImportedQueue())
                .to(smartFinanceExchange()).with("transactions.imported");
    }

    @Bean
    public Binding transactionCreatedBinding() {
        return BindingBuilder.bind(transactionCreatedQueue())
                .to(smartFinanceExchange()).with("transaction.created");
    }

    @Bean
    public Binding budgetThresholdBinding() {
        return BindingBuilder.bind(budgetThresholdQueue())
                .to(smartFinanceExchange()).with("budget.threshold.reached");
    }

    @Bean
    public Binding aiCategorizationCompletedBinding() {
        return BindingBuilder.bind(aiCategorizationCompletedQueue())
                .to(smartFinanceExchange()).with("ai.categorization.completed");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}