# RabbitMQ Subproject

## Overview

Spring Boot application demonstrating 6 common RabbitMQ messaging patterns with JSON serialization and best practices.

## Project Structure

```
rabbitmq/
├── build.gradle
└── src/main/
    ├── java/com/example/rabbitmq/
    │   ├── Application.java           # Spring Boot main class
    │   ├── DemoRunner.java            # Runs all pattern demos (CommandLineRunner)
    │   ├── config/
    │   │   └── RabbitMQConfig.java    # All exchanges, queues, bindings + RabbitTemplate
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
    └── resources/
        └── application.yml            # Externalized configuration
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

- **JSON Serialization**: `Jackson2JsonMessageConverter` on `RabbitTemplate` + listener container
- **Publisher Confirms**: `publisher-confirm-type: correlated` + `publisher-returns: true` enabled; no `ConfirmCallback` wired
- **Manual Acknowledgment**: `TaskConsumer` and `PaymentConsumer` use `ackMode = "MANUAL"`
- **ACK Strategy in PaymentConsumer**: `basicReject(false)` for `PaymentValidationException` (→ DLQ), `basicNack(false, true)` for `PaymentProcessingException` (recoverable, requeue)
- **Listener Retry**: Spring AMQP retry enabled (3 attempts, initial 1s, multiplier 2.0, max 10s)
- **Fair Dispatch**: Global `prefetch: 1` in `application.yml`
- **Health Check**: Actuator endpoint at `/actuator/health`
- **Externalized Config**: Environment variables for connection settings
- **Tests**: None written yet (`spring-rabbit-test` dependency present)

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
- `lombok` - Boilerplate reduction

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
