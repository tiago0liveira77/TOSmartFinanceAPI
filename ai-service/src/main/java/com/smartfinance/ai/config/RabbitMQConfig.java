package com.smartfinance.ai.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.queues.transactions-imported}")
    private String transactionsImportedQueue;

    @Value("${rabbitmq.queues.ai-categorization-completed}")
    private String categorizationCompletedQueue;

    @Bean
    public TopicExchange smartfinanceExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue transactionsImportedQueue() {
        return QueueBuilder.durable(transactionsImportedQueue).build();
    }

    @Bean
    public Queue categorizationCompletedQueue() {
        return QueueBuilder.durable(categorizationCompletedQueue).build();
    }

    @Bean
    public Binding transactionsImportedBinding() {
        return BindingBuilder
                .bind(transactionsImportedQueue())
                .to(smartfinanceExchange())
                .with("transactions.imported");
    }

    @Bean
    public Binding categorizationCompletedBinding() {
        return BindingBuilder
                .bind(categorizationCompletedQueue())
                .to(smartfinanceExchange())
                .with("ai.categorization.completed");
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("com.smartfinance.shared.event");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                          Jackson2JsonMessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
