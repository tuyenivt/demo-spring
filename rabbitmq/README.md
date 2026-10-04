# RabbitMQ Demo

Spring Boot application demonstrating common RabbitMQ messaging patterns.

## Messaging Patterns

| Pattern             | Exchange Type | Use Case                                   |
|---------------------|---------------|--------------------------------------------|
| RPC (Request-Reply) | Topic         | Synchronous request-response communication |
| Pub/Sub (Fanout)    | Fanout        | Broadcast notifications to all subscribers |
| Work Queue          | Default       | Distribute tasks across multiple workers   |
| Priority Routing    | Direct        | Route messages by priority/category        |
| Dead Letter Queue   | Direct        | Handle failed messages for retry/review    |
| Delayed Message     | TTL + DLX     | Schedule messages for future delivery      |

## Prerequisites

- Java 25
- RabbitMQ server running on `localhost:5672`
- Docker (for tests - Testcontainers starts RabbitMQ)

## Quick Start

```bash
# Start RabbitMQ (Docker)
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4.2-management-alpine

# Run the demo
./gradlew :rabbitmq:bootRun
```

## Configuration

Environment variables (with defaults):

| Variable        | Default   | Description   |
|-----------------|-----------|---------------|
| `RABBITMQ_HOST` | localhost | RabbitMQ host |
| `RABBITMQ_PORT` | 5672      | RabbitMQ port |
| `RABBITMQ_USER` | guest     | Username      |
| `RABBITMQ_PASS` | guest     | Password      |

Set `demo.runner.enabled=false` to start the app without running the pattern demos.

## Pattern Details

### 1. RPC Pattern (Topic Exchange)

Synchronous request-reply using `convertSendAndReceive()`.

- **Exchange**: `rpc.topic.exchange`
- **Queue**: `rpc.queue`
- **Routing Key**: `rpc.request.#`

### 2. Fanout Exchange (Pub/Sub)

Broadcasts notifications to all bound queues (email + SMS).

- **Exchange**: `notifications.fanout`
- **Queues**: `notifications.email`, `notifications.sms`

### 3. Work Queue (Task Distribution)

Distributes CPU-intensive tasks with manual acknowledgment and fair dispatch.

- **Queue**: `tasks.queue`
- **Prefetch**: 1 (fair dispatch)
- **Ack Mode**: Manual

### 4. Direct Exchange (Priority Routing)

Routes orders to different queues by priority.

- **Exchange**: `orders.direct`
- **Queues**: `orders.high`, `orders.normal`
- **Routing Keys**: `high`, `normal`

### 5. Dead Letter Queue (DLQ)

Failed payments route to DLQ for manual review.

- **Queue**: `payments.queue` (with DLX config)
- **DLX Exchange**: `payments.dlx`
- **DLQ**: `payments.dlq`

### 6. Delayed Message (TTL + DLX)

Messages wait in delay queue before delivery.

- **Delay Queue**: `reminders.delay` (10s TTL)
- **Target Queue**: `reminders.queue`

## Health Check

```bash
curl http://localhost:8080/actuator/health
```

## Commands

```bash
# Build
./gradlew :rabbitmq:build

# Test
./gradlew :rabbitmq:test

# Run
./gradlew :rabbitmq:bootRun
```
