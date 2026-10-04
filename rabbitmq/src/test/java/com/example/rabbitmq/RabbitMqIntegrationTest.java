package com.example.rabbitmq;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.dto.*;
import com.example.rabbitmq.producer.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.test.RabbitListenerTest;
import org.springframework.amqp.rabbit.test.RabbitListenerTestHarness;
import org.springframework.amqp.rabbit.test.RabbitListenerTestHarness.InvocationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "demo.runner.enabled=false"
)
@AutoConfigureRestTestClient
// Captures each @RabbitListener invocation (by listener id) so tests can assert on what was consumed
@RabbitListenerTest(spy = false, capture = true)
class RabbitMqIntegrationTest {

    // Container as a bean: Spring stops it after the context closes, so listeners shut down cleanly
    @TestConfiguration(proxyBeanMethods = false)
    static class RabbitMqContainerConfig {

        @Bean
        @ServiceConnection
        RabbitMQContainer rabbitMqContainer() {
            return new RabbitMQContainer("rabbitmq:4.2-management-alpine");
        }
    }

    @Autowired
    private RabbitListenerTestHarness harness;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private RpcProducer rpcProducer;

    @Autowired
    private NotificationProducer notificationProducer;

    @Autowired
    private TaskProducer taskProducer;

    @Autowired
    private OrderProducer orderProducer;

    @Autowired
    private PaymentProducer paymentProducer;

    @Autowired
    private ReminderProducer reminderProducer;

    @Test
    void rpcRequestReceivesReply() {
        var response = rpcProducer.sendAndReceive(new RpcRequest(42, "ping"));

        assertThat(response).isEqualTo(new RpcResponse(42, "Reply to: ping"));
    }

    @Test
    void fanoutBroadcastsToEmailAndSmsQueues() throws InterruptedException {
        var notification = new Notification(UUID.randomUUID().toString(), "ALERT", "Maintenance", "all-users");

        notificationProducer.broadcast(notification);

        assertThat(nextArgument("emailNotification")).isEqualTo(notification);
        assertThat(nextArgument("smsNotification")).isEqualTo(notification);
    }

    @Test
    void workQueueDeliversTaskToWorker() throws InterruptedException {
        var task = new Task(UUID.randomUUID().toString(), "Task-1", "batch #1", 1);

        taskProducer.submitTask(task);

        assertThat(nextArgument("task")).isEqualTo(task);
    }

    @Test
    void directExchangeRoutesOrdersByPriority() throws InterruptedException {
        var urgent = new Order(UUID.randomUUID().toString(), "Express Laptop", 1, true);
        var normal = new Order(UUID.randomUUID().toString(), "Standard Monitor", 2, false);

        orderProducer.sendOrder(urgent);
        orderProducer.sendOrder(normal);

        assertThat(nextArgument("highPriorityOrder")).isEqualTo(urgent);
        assertThat(nextArgument("normalOrder")).isEqualTo(normal);
    }

    @Test
    void invalidPaymentIsDeadLettered() throws InterruptedException {
        var valid = new Payment(UUID.randomUUID().toString(), "customer-123", new BigDecimal("500.00"), "pending", 0);
        var invalid = new Payment(UUID.randomUUID().toString(), "customer-456", new BigDecimal("15000.00"), "pending", 0);

        paymentProducer.submitPayment(valid);
        paymentProducer.submitPayment(invalid);

        // Only the payment over the limit is rejected (requeue=false) and routed through payments.dlx
        assertThat(nextArgument("failedPayment")).isEqualTo(invalid);
        assertThat(harness.getNextInvocationDataFor("failedPayment", 1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void reminderIsDeliveredAfterTtlExpires() throws InterruptedException {
        var reminder = new Reminder(UUID.randomUUID().toString(), "user-789", "Meeting soon", Instant.now());
        var sentAt = System.currentTimeMillis();

        reminderProducer.scheduleReminder(reminder);

        var invocation = harness.getNextInvocationDataFor("reminder", RabbitMQConfig.REMINDER_DELAY_MS + 10_000, TimeUnit.MILLISECONDS);
        assertThat(invocation).isNotNull();
        assertThat(invocation.getArguments()[0]).isEqualTo(reminder);
        assertThat(System.currentTimeMillis() - sentAt).isGreaterThanOrEqualTo(RabbitMQConfig.REMINDER_DELAY_MS - 500);
    }

    @Test
    void publisherConfirmsAckAndReturnUnroutableMessage() throws Exception {
        var routed = new CorrelationData(UUID.randomUUID().toString());
        var unroutable = new CorrelationData(UUID.randomUUID().toString());
        var order = new Order(UUID.randomUUID().toString(), "Keyboard", 1, false);

        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_DIRECT_EXCHANGE, RabbitMQConfig.ROUTING_KEY_NORMAL, order, routed);
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_DIRECT_EXCHANGE, "unknown", order, unroutable);

        assertThat(routed.getFuture().get(5, TimeUnit.SECONDS).ack()).isTrue();
        assertThat(routed.getReturned()).isNull();

        // mandatory=true (publisher-returns) makes the broker return the message before confirming it
        assertThat(unroutable.getFuture().get(5, TimeUnit.SECONDS).ack()).isTrue();
        assertThat(unroutable.getReturned()).isNotNull();
        assertThat(unroutable.getReturned().getReplyText()).isEqualTo("NO_ROUTE");

        assertThat(nextArgument("normalOrder")).isEqualTo(order);
    }

    @Test
    void rabbitHealthIsUp() {
        restTestClient.get().uri("/actuator/health/rabbit")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.details.version").isNotEmpty();
    }

    private Object nextArgument(String listenerId) throws InterruptedException {
        InvocationData invocation = harness.getNextInvocationDataFor(listenerId, 10, TimeUnit.SECONDS);
        assertThat(invocation).as("invocation of listener '%s'", listenerId).isNotNull();
        return invocation.getArguments()[0];
    }
}
