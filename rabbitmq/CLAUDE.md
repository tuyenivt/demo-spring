# RabbitMQ Subproject

## Overview

Spring Boot application demonstrating 6 common RabbitMQ messaging patterns with JSON serialization and best practices.

- **Java**: 25
- **Spring Boot**: 4.1 (Spring AMQP 4.1)

## Project Structure

```
rabbitmq/
├── build.gradle
└── src/
    ├── main/java/com/example/rabbitmq/
    │   ├── Application.java           # Spring Boot main class
    │   ├── DemoRunner.java            # Runs all pattern demos (CommandLineRunner, `demo.runner.enabled`)
    │   ├── config/
    │   │   └── RabbitMQConfig.java    # Exchanges, queues, bindings, JSON converter, publisher callbacks
    │   ├── producer/
    │   │   ├── RpcProducer.java       # RPC pattern
    │   │   ├── NotificationProducer.java  # Fanout
    │   │   ├── TaskProducer.java      # Work queue
    │   │   ├── OrderProducer.java     # Direct exchange
    │   │   ├── PaymentProducer.java   # DLQ demo
    │   │   └── ReminderProducer.java  # Delayed message
    │   ├── consumer/
    │   │   ├── RpcConsumer.java
    │   │   ├── NotificationConsumer.java
    │   │   ├── TaskConsumer.java      # Manual ACK; basicNack(requeue=true) on failure
    │   │   ├── OrderConsumer.java
    │   │   ├── PaymentConsumer.java   # Manual ACK; basicReject→DLQ or basicNack(requeue=true)
    │   │   └── ReminderConsumer.java
    │   ├── dto/
    │   │   ├── RpcRequest.java
    │   │   ├── RpcResponse.java
    │   │   ├── Notification.java
    │   │   ├── Task.java
    │   │   ├── Order.java
    │   │   ├── Payment.java
    │   │   └── Reminder.java
    │   └── exception/
    │       ├── PaymentValidationException.java   # Unrecoverable → DLQ
    │       └── PaymentProcessingException.java   # Recoverable → requeue
    ├── main/resources/
    │   └── application.yml            # Externalized configuration
    └── test/java/com/example/rabbitmq/
        └── RabbitMqIntegrationTest.java  # Testcontainers RabbitMQ, all patterns end-to-end
```

## Messaging Patterns

| # | Pattern           | Exchange               | Key Classes                                |
|---|-------------------|------------------------|--------------------------------------------|
| 1 | RPC (Topic)       | `rpc.topic.exchange`   | RpcProducer, RpcConsumer                   |
| 2 | Fanout (Pub/Sub)  | `notifications.fanout` | NotificationProducer, NotificationConsumer |
| 3 | Work Queue        | Default                | TaskProducer, TaskConsumer                 |
| 4 | Direct (Routing)  | `orders.direct`        | OrderProducer, OrderConsumer               |
| 5 | Dead Letter Queue | `payments.dlx`         | PaymentProducer, PaymentConsumer           |
| 6 | Delayed Message   | TTL + DLX              | ReminderProducer, ReminderConsumer         |

## Key Features

- **JSON Serialization**: `JacksonJsonMessageConverter` (Jackson 3) bean; Boot applies it to the auto-configured `RabbitTemplate` and listener containers
- **Publisher Confirms & Returns**: `publisher-confirm-type: correlated` + `publisher-returns: true` (Boot sets `mandatory=true`); a `RabbitTemplateCustomizer` wires a `ConfirmCallback` (logs broker ack/nack) and `ReturnsCallback` (logs unroutable messages)
- **Listener IDs**: every `@RabbitListener` has an `id` (`rpc`, `emailNotification`, `smsNotification`, `task`, `highPriorityOrder`, `normalOrder`, `payment`, `failedPayment`, `reminder`)
- **Manual Acknowledgment**: `TaskConsumer` and `PaymentConsumer` use `ackMode = "MANUAL"`
- **ACK Strategy in PaymentConsumer**: `basicReject(false)` for `PaymentValidationException` (→ DLQ), `basicNack(false, true)` for `PaymentProcessingException` (recoverable, requeue)
- **Listener Retry**: Spring AMQP retry enabled (`max-retries: 2` = 3 attempts, initial 1s, multiplier 2.0, max 10s)
- **Fair Dispatch**: Global `prefetch: 1` in `application.yml`
- **Health Check**: `/actuator/health` (details shown, includes `rabbit` with broker version)
- **Externalized Config**: Environment variables for connection settings

## Configuration Constants

All exchange/queue names are defined in `RabbitMQConfig`:

| Constant                       | Value                |
|--------------------------------|----------------------|
| `RPC_EXCHANGE`                 | rpc.topic.exchange   |
| `NOTIFICATION_FANOUT_EXCHANGE` | notifications.fanout |
| `TASK_QUEUE`                   | tasks.queue          |
| `ORDER_DIRECT_EXCHANGE`        | orders.direct        |
| `PAYMENT_DLX_EXCHANGE`         | payments.dlx         |
| `REMINDER_DELAY_QUEUE`         | reminders.delay      |

## Dependencies

- `spring-boot-starter-amqp` - Spring AMQP for RabbitMQ
- `spring-boot-starter-actuator` - Health checks
- `spring-boot-starter-webmvc` - Serves the actuator endpoints over HTTP
- `lombok` - Boilerplate reduction
- Test: `spring-boot-starter-amqp-test` (`spring-rabbit-test`), `spring-boot-starter-actuator-test`, `spring-boot-starter-webmvc-test`, `spring-boot-testcontainers`, `testcontainers-rabbitmq`

## Tests

`RabbitMqIntegrationTest` (`@SpringBootTest`, random port, `demo.runner.enabled=false`):
- RabbitMQ via Testcontainers (`rabbitmq:4.2-management-alpine`), declared as a `@Bean @ServiceConnection` in a nested `@TestConfiguration`
- `@RabbitListenerTest(capture = true)` + `RabbitListenerTestHarness` asserts what each listener (by `id`) received
- 8 tests: RPC reply, fanout to email + SMS, work queue, direct routing by priority, invalid payment dead-lettered, reminder delivered after the TTL, publisher confirm ack + unroutable message returned (`NO_ROUTE`), `/actuator/health/rabbit` UP (`RestTestClient`)
- Requires Docker; the reminder test waits for the 10s TTL

## Common Commands

```bash
# Build
./gradlew :rabbitmq:build

# Test
./gradlew :rabbitmq:test

# Run
./gradlew :rabbitmq:bootRun
```

## Demo Output

When running, the application demonstrates all 6 patterns sequentially:

1. **RPC**: Sends 3 request-reply messages
2. **Fanout**: Broadcasts 1 notification to email + SMS queues
3. **Work Queue**: Submits 5 tasks for worker processing
4. **Direct**: Sends 1 urgent + 1 normal order
5. **DLQ**: Submits 1 valid + 1 invalid payment (invalid goes to DLQ)
6. **Delayed**: Schedules 1 reminder (delivered after 10s delay)
