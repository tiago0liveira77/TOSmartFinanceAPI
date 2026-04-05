package com.smartfinance.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "rabbitmq")
public record RabbitMQProperties(
        String exchange,
        Map<String, String> queues
) {}