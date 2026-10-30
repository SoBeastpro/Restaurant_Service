package com.restaurant.audit.messaging;

import com.restaurant.foundation.messaging.DomainEventTopology;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuditMessagingConfiguration {

    static final String QUEUE = "audit.domain-events.v1";

    @Bean
    TopicExchange auditDomainEventsExchange() {
        return new TopicExchange(DomainEventTopology.EXCHANGE, true, false);
    }

    @Bean
    Queue auditDomainEventsQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    Binding auditDomainEventsBinding(Queue auditDomainEventsQueue, TopicExchange auditDomainEventsExchange) {
        return BindingBuilder.bind(auditDomainEventsQueue).to(auditDomainEventsExchange).with("#");
    }
}
