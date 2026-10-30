package com.restaurant.foundation.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import tools.jackson.databind.ObjectMapper;

@AutoConfiguration
@EnableScheduling
@ConditionalOnProperty(name = "restaurant.outbox.enabled", havingValue = "true")
public class OutboxAutoConfiguration {

    @Bean
    TopicExchange restaurantDomainEventsExchange() {
        return new TopicExchange(DomainEventTopology.EXCHANGE, true, false);
    }

    @Bean
    OutboxPublisher outboxPublisher(
            JdbcClient jdbc,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${spring.application.name}") String producer,
            @Value("${restaurant.outbox.batch-size:50}") int batchSize) {
        return new OutboxPublisher(jdbc, rabbitTemplate, objectMapper, producer, batchSize);
    }
}
