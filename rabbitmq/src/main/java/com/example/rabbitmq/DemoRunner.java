package com.example.rabbitmq;

import com.example.rabbitmq.dto.*;
import com.example.rabbitmq.producer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBooleanProperty(name = "demo.runner.enabled", matchIfMissing = true)
public class DemoRunner implements CommandLineRunner {

    private final RpcProducer rpcProducer;
    private final NotificationProducer notificationProducer;
    private final TaskProducer taskProducer;
    private final OrderProducer orderProducer;
    private final PaymentProducer paymentProducer;
    private final ReminderProducer reminderProducer;

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("Starting RabbitMQ Demo Patterns");
        log.info("========================================");

        Thread.sleep(1000); // Wait for listeners to be ready

        demoRpcPattern();
        Thread.sleep(500);

        demoFanoutPattern();
        Thread.sleep(500);

        demoWorkQueue();
        Thread.sleep(500);

        demoDirectExchange();
        Thread.sleep(500);

        demoDeadLetterQueue();
        Thread.sleep(500);

        demoDelayedMessage();

        log.info("========================================");
        log.info("All demos completed. Application running...");
        log.info("========================================");
    }

    private void demoRpcPattern() {
        log.info("\n--- Demo 1: RPC Pattern (Topic Exchange) ---");
        for (int i = 1; i <= 3; i++) {
            var request = new RpcRequest(i, "Hello from RPC #" + i);
            var response = rpcProducer.sendAndReceive(request);
            log.info("RPC Response: {}", response);
        }
    }

    private void demoFanoutPattern() {
        log.info("\n--- Demo 2: Fanout Exchange (Pub/Sub) ---");
        var notification = new Notification(
                UUID.randomUUID().toString(),
                "ALERT",
                "System maintenance scheduled for tonight",
                "all-users"
        );
        notificationProducer.broadcast(notification);
    }

    private void demoWorkQueue() {
        log.info("\n--- Demo 3: Work Queue (Task Distribution) ---");
        for (int i = 1; i <= 5; i++) {
            var task = new Task(
                    UUID.randomUUID().toString(),
                    "Task-" + i,
                    "Processing data batch #" + i,
                    i
            );
            taskProducer.submitTask(task);
        }
    }

    private void demoDirectExchange() {
        log.info("\n--- Demo 4: Direct Exchange (Priority Routing) ---");
        // Send urgent order
        var urgentOrder = new Order(
                UUID.randomUUID().toString(),
                "Express Laptop",
                1,
                true
        );
        orderProducer.sendOrder(urgentOrder);

        // Send normal order
        var normalOrder = new Order(
                UUID.randomUUID().toString(),
                "Standard Monitor",
                2,
                false
        );
        orderProducer.sendOrder(normalOrder);
    }

    private void demoDeadLetterQueue() {
        log.info("\n--- Demo 5: Dead Letter Queue ---");
        // Valid payment
        var validPayment = new Payment(
                UUID.randomUUID().toString(),
                "customer-123",
                new BigDecimal("500.00"),
                "pending",
                0
        );
        paymentProducer.submitPayment(validPayment);

        // Invalid payment (will go to DLQ)
        var invalidPayment = new Payment(
                UUID.randomUUID().toString(),
                "customer-456",
                new BigDecimal("15000.00"), // Exceeds limit
                "pending",
                0
        );
        paymentProducer.submitPayment(invalidPayment);
    }

    private void demoDelayedMessage() {
        log.info("\n--- Demo 6: Delayed Message (TTL + DLX) ---");
        var reminder = new Reminder(
                UUID.randomUUID().toString(),
                "user-789",
                "Don't forget your meeting in 10 seconds!",
                Instant.now()
        );
        reminderProducer.scheduleReminder(reminder);
        log.info("Reminder scheduled. It will be delivered after 10 seconds...");
    }
}
