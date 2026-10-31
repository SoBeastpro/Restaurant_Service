package com.restaurant.reporting.messaging;

import com.restaurant.foundation.messaging.DomainEventTopology;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ReportingMessagingConfiguration {

    static final String QUEUE = "reporting.activity-events.v1";

    @Bean
    TopicExchange reportingDomainEventsExchange() {
        return new TopicExchange(DomainEventTopology.EXCHANGE, true, false);
    }

    @Bean
    Queue reportingActivityEventsQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    Binding reportingShiftEvents(Queue reportingActivityEventsQueue, TopicExchange reportingDomainEventsExchange) {
        return BindingBuilder.bind(reportingActivityEventsQueue)
                .to(reportingDomainEventsExchange).with("operations.shift.*.v1");
    }

    @Bean
    Binding reportingTaskEvents(Queue reportingActivityEventsQueue, TopicExchange reportingDomainEventsExchange) {
        return BindingBuilder.bind(reportingActivityEventsQueue)
                .to(reportingDomainEventsExchange).with("operations.task.*.v1");
    }

    @Bean
    Binding reportingTableEvents(Queue reportingActivityEventsQueue, TopicExchange reportingDomainEventsExchange) {
        return BindingBuilder.bind(reportingActivityEventsQueue)
                .to(reportingDomainEventsExchange).with("restaurant.table.status-changed.v1");
    }
}
