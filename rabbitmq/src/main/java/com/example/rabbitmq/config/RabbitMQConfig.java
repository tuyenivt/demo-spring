package com.example.rabbitmq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

    // ===========================================
    // Message Converter (JSON instead of Java Serialization)
    // ===========================================

    // Boot applies this converter to the auto-configured RabbitTemplate and listener containers
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    // ===========================================
    // Publisher Confirms & Returns
    // ===========================================

    // publisher-confirm-type=correlated -> broker acks/nacks every publish
    // publisher-returns=true -> Boot sets mandatory=true, so unroutable messages are returned
    @Bean
    public RabbitTemplateCustomizer publisherCallbacksCustomizer() {
        return template -> {
            template.setConfirmCallback((correlationData, ack, cause) -> {
                var id = correlationData != null ? correlationData.getId() : null;
                if (ack) {
                    log.debug("[CONFIRM] Broker acknowledged message {}", id);
                } else {
                    log.error("[CONFIRM] Broker rejected message {}: {}", id, cause);
                }
            });
            template.setReturnsCallback(returned -> log.warn("[RETURN] Unroutable message (exchange={}, routingKey={}): {} {}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(), returned.getReplyText()));
        };
    }

    // ===========================================
    // 1. RPC Pattern (Topic Exchange) - Original Demo
    // ===========================================

    public static final String RPC_EXCHANGE = "rpc.topic.exchange";
    public static final String RPC_QUEUE = "rpc.queue";
    public static final String RPC_ROUTING_KEY = "rpc.request.#";

    @Bean
    public TopicExchange rpcExchange() {
        return new TopicExchange(RPC_EXCHANGE);
    }

    @Bean
    public Queue rpcQueue() {
        return QueueBuilder.durable(RPC_QUEUE).build();
    }

    @Bean
    public Binding rpcBinding(Queue rpcQueue, TopicExchange rpcExchange) {
        return BindingBuilder.bind(rpcQueue).to(rpcExchange).with(RPC_ROUTING_KEY);
    }

    // ===========================================
    // 2. Fanout Exchange (Pub/Sub) - Broadcast Notifications
    // ===========================================

    public static final String NOTIFICATION_FANOUT_EXCHANGE = "notifications.fanout";
    public static final String NOTIFICATION_EMAIL_QUEUE = "notifications.email";
    public static final String NOTIFICATION_SMS_QUEUE = "notifications.sms";

    @Bean
    public FanoutExchange notificationFanoutExchange() {
        return new FanoutExchange(NOTIFICATION_FANOUT_EXCHANGE);
    }

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(NOTIFICATION_EMAIL_QUEUE).build();
    }

    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(NOTIFICATION_SMS_QUEUE).build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, FanoutExchange notificationFanoutExchange) {
        return BindingBuilder.bind(emailQueue).to(notificationFanoutExchange);
    }

    @Bean
    public Binding smsBinding(Queue smsQueue, FanoutExchange notificationFanoutExchange) {
        return BindingBuilder.bind(smsQueue).to(notificationFanoutExchange);
    }

    // ===========================================
    // 3. Work Queue (Task Distribution)
    // ===========================================

    public static final String TASK_QUEUE = "tasks.queue";

    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(TASK_QUEUE).build();
    }

    // ===========================================
    // 4. Direct Exchange (Priority Routing)
    // ===========================================

    public static final String ORDER_DIRECT_EXCHANGE = "orders.direct";
    public static final String ORDER_HIGH_PRIORITY_QUEUE = "orders.high";
    public static final String ORDER_NORMAL_QUEUE = "orders.normal";
    public static final String ROUTING_KEY_HIGH = "high";
    public static final String ROUTING_KEY_NORMAL = "normal";

    @Bean
    public DirectExchange orderDirectExchange() {
        return new DirectExchange(ORDER_DIRECT_EXCHANGE);
    }

    @Bean
    public Queue highPriorityQueue() {
        return QueueBuilder.durable(ORDER_HIGH_PRIORITY_QUEUE).build();
    }

    @Bean
    public Queue normalPriorityQueue() {
        return QueueBuilder.durable(ORDER_NORMAL_QUEUE).build();
    }

    @Bean
    public Binding highPriorityBinding(Queue highPriorityQueue, DirectExchange orderDirectExchange) {
        return BindingBuilder.bind(highPriorityQueue).to(orderDirectExchange).with(ROUTING_KEY_HIGH);
    }

    @Bean
    public Binding normalPriorityBinding(Queue normalPriorityQueue, DirectExchange orderDirectExchange) {
        return BindingBuilder.bind(normalPriorityQueue).to(orderDirectExchange).with(ROUTING_KEY_NORMAL);
    }

    // ===========================================
    // 5. Dead Letter Queue (DLQ) - Failed Message Handling
    // ===========================================

    public static final String PAYMENT_QUEUE = "payments.queue";
    public static final String PAYMENT_DLX_EXCHANGE = "payments.dlx";
    public static final String PAYMENT_DLQ = "payments.dlq";
    public static final String PAYMENT_DLQ_ROUTING_KEY = "failed";

    @Bean
    public DirectExchange paymentDlxExchange() {
        return new DirectExchange(PAYMENT_DLX_EXCHANGE);
    }

    @Bean
    public Queue paymentQueue() {
        return QueueBuilder.durable(PAYMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", PAYMENT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue paymentDlq() {
        return QueueBuilder.durable(PAYMENT_DLQ).build();
    }

    @Bean
    public Binding paymentDlqBinding(Queue paymentDlq, DirectExchange paymentDlxExchange) {
        return BindingBuilder.bind(paymentDlq).to(paymentDlxExchange).with(PAYMENT_DLQ_ROUTING_KEY);
    }

    // ===========================================
    // 6. Delayed Message (TTL + DLX) - Scheduled Messages
    // ===========================================

    public static final String REMINDER_DELAY_QUEUE = "reminders.delay";
    public static final String REMINDER_QUEUE = "reminders.queue";
    public static final long REMINDER_DELAY_MS = 10000;

    @Bean
    public Queue reminderDelayQueue() {
        return QueueBuilder.durable(REMINDER_DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", "") // Default exchange
                .withArgument("x-dead-letter-routing-key", REMINDER_QUEUE)
                .withArgument("x-message-ttl", REMINDER_DELAY_MS)
                .build();
    }

    @Bean
    public Queue reminderQueue() {
        return QueueBuilder.durable(REMINDER_QUEUE).build();
    }
}
