package com.smartfinance.finance;

import com.smartfinance.finance.config.JwtConfig;
import com.smartfinance.finance.config.RabbitMQProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtConfig.class, RabbitMQProperties.class})
public class FinanceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceServiceApplication.class, args);
    }
}