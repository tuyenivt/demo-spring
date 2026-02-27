# Temporal Demo

## Overview

A Temporal Java demo project demonstrating workflow orchestration with:
- Saga pattern for distributed transactions
- Workflow signals and queries
- Activity heartbeat for long operations
- Search attributes for workflow discovery
- Workflow versioning for safe deployments
- Rate limiting and graceful shutdown
- Durable timers with `Workflow.sleep()`
- Human-in-the-loop approval with `Workflow.await()`
- Cron/scheduled workflows replacing `@Scheduled`
- Continue-As-New for long-running polling workflows
- Non-retryable exceptions for permanent failures
- Validated workflow updates with `@UpdateMethod`
- Scoped cancellation with `CancellationScope`
- Async activity fan-out with `Promise.allOf`
- Atomic start-or-signal with `signalWithStart`

## Architecture Overview

```
---------------
│   Client    │ Starts Order Workflow
---------------
       │
       ├── [Signal] cancelOrder(reason)
       ├── [Signal] updateShippingAddress(address)
       ├── [Query] getStatus()
       ├── [Query] getShippingAddress()
       │
       v
-----------------------------------------------------------
│              Order Processing Workflow                  │
│  [Search Attributes: CustomerId, OrderAmount, Status]   │
│                                                         │
│  1. Validate Order Activity (non-retryable on invalid)  │
│     └── Fraud Check (v2 versioned feature)              │
│  2. Payment Child Workflow                              │
│     ├─→ Authorize Payment Activity [heartbeat]          │
│     ├─→ Capture Payment Activity [heartbeat]            │
│     └── [Saga] Refund on failure                        │
│  3. Inventory Child Workflow                            │
│     ├─→ Check Inventory Activity                        │
│     ├─→ Reserve Inventory Activity                      │
│     └── [Saga] Release on failure                       │
│  4. Send Notification Activity                          │
│  5. Workflow.sleep(24h) — durable timer                 │
│  6. Send shipping reminder notification                 │
-----------------------------------------------------------
       │
       v
---------------
│   Worker    │ Executes Workflows & Activities
│             │ [Rate Limited: 10 activities, 20 workflows]
---------------
```

## Quick Start

1. Start Temporal with docker

   ```bash
   docker run -d --name temporal --user root -p 7233:7233 -p 8233:8233 -v temporal-data:/data temporalio/temporal:1.5.1 server start-dev --ip 0.0.0.0 --db-filename /data/temporal.db
   ```

2. Run the Application

   ```bash
   ./gradlew :temporal:bootRun
   ```

3. API Tests

- Start a workflow (async - recommended)

   ```bash
   curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{"customerId":"CUST-123","amount":9999,"description":"Premium Widget"}'
   ```

- Start a workflow (sync - for quick demo only)

   ```bash
   curl -X POST http://localhost:8080/api/orders/sync \
     -H "Content-Type: application/json" \
     -d '{"customerId":"CUST-456","amount":5000,"description":"Basic Widget"}'
   ```

- Check workflow status
   ```bash
   curl http://localhost:8080/api/orders/ORD-XXXXXXXX/status
   ```

## Testing

Run workflow unit tests:

```bash
./gradlew :temporal:test
```

Tests use `TestWorkflowEnvironment` for isolated testing with mocked activities.

## API Reference

### Order Workflow

| Method | Endpoint                                 | Description                              |
|--------|------------------------------------------|------------------------------------------|
| POST   | `/api/orders`                            | Start order workflow (async)             |
| POST   | `/api/orders/sync`                       | Start order workflow (sync, demo only)   |
| GET    | `/api/orders/{orderId}/status`           | Get workflow execution status            |
| POST   | `/api/orders/{orderId}/cancel`           | Send cancel signal (returns 202)         |
| PUT    | `/api/orders/{orderId}/shipping-address` | Send address update signal (returns 202) |
| PATCH  | `/api/orders/{orderId}/quantity`         | Validated quantity update                |
| GET    | `/api/orders/{orderId}/shipping-address` | Query current shipping address           |

```bash
# Cancel a running order
curl -X POST http://localhost:8080/api/orders/ORD-XXXXXXXX/cancel \
  -H "Content-Type: application/json" \
  -d '{"reason":"Customer request"}'

# Update shipping address
curl -X PUT http://localhost:8080/api/orders/ORD-XXXXXXXX/shipping-address \
  -H "Content-Type: application/json" \
  -d '{"address":"123 New Street, Springfield"}'

# Query shipping address
curl http://localhost:8080/api/orders/ORD-XXXXXXXX/shipping-address

# Update quantity via Temporal Update API (validated)
curl -X PATCH http://localhost:8080/api/orders/ORD-XXXXXXXX/quantity \
  -H "Content-Type: application/json" \
  -d '{"quantity":2}'
```

### Approval Workflow (Human-in-the-Loop)

Demonstrates `Workflow.await()` — the workflow pauses and waits up to 24 hours for a manager decision.

| Method | Endpoint                                      | Description             |
|--------|-----------------------------------------------|-------------------------|
| POST   | `/api/approvals?orderId=&customerId=&amount=` | Start approval workflow |
| POST   | `/api/approvals/{orderId}/approve`            | Send approve signal     |
| POST   | `/api/approvals/{orderId}/reject`             | Send reject signal      |
| GET    | `/api/approvals/{orderId}/status`             | Query approval status   |

```bash
# Start approval (workflow pauses waiting for decision)
curl -X POST "http://localhost:8080/api/approvals?orderId=ORD-123&customerId=CUST-1&amount=9999"

# Approve
curl -X POST http://localhost:8080/api/approvals/ORD-123/approve \
  -H "Content-Type: application/json" \
  -d '{"note":"Approved by manager"}'

# Reject
curl -X POST http://localhost:8080/api/approvals/ORD-123/reject \
  -H "Content-Type: application/json" \
  -d '{"reason":"Amount exceeds limit"}'

# Query status (PENDING_APPROVAL / APPROVED / REJECTED / AUTO_REJECTED)
curl http://localhost:8080/api/approvals/ORD-123/status
```

### Report Workflow (Cron)

Demonstrates Temporal as a replacement for `@Scheduled` and cron jobs — durable, distributed, deduped.

| Method | Endpoint             | Description                          |
|--------|----------------------|--------------------------------------|
| POST   | `/api/reports/start` | Start daily cron workflow (9 AM UTC) |
| DELETE | `/api/reports/stop`  | Terminate the cron workflow          |

```bash
# Start cron (runs daily at 9 AM UTC, persists until stopped)
curl -X POST http://localhost:8080/api/reports/start

# Stop cron
curl -X DELETE http://localhost:8080/api/reports/stop
```

### Polling Workflow (Continue-As-New)

Demonstrates `Workflow.continueAsNew()` — resets event history every 10 iterations and also checks `Workflow.getInfo().isContinueAsNewSuggested()`.

| Method | Endpoint                         | Description                    |
|--------|----------------------------------|--------------------------------|
| POST   | `/api/polling/start?targetId=`   | Start polling workflow         |
| GET    | `/api/polling/{targetId}/status` | Query current iteration count  |
| DELETE | `/api/polling/{targetId}/stop`   | Terminate the polling workflow |

```bash
# Start polling (resets history every 10 iterations, stops at 50)
curl -X POST "http://localhost:8080/api/polling/start?targetId=resource-42"

# Check iteration count
curl http://localhost:8080/api/polling/resource-42/status

# Stop polling
curl -X DELETE http://localhost:8080/api/polling/resource-42/stop
```

### Notification Workflow (Async Fan-Out)

Demonstrates parallel activity execution (`Async.procedure` + `Promise.allOf`) for email/SMS/push.

| Method | Endpoint                  | Description                     |
|--------|---------------------------|---------------------------------|
| POST   | `/api/notifications/send` | Send email/sms/push in parallel |

```bash
curl -X POST http://localhost:8080/api/notifications/send \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-1","message":"Order shipped"}'
```

### Cart Workflow (SignalWithStart)

Demonstrates atomic signal-or-start to avoid start/signal race conditions.

| Method | Endpoint                    | Description                                |
|--------|-----------------------------|--------------------------------------------|
| POST   | `/api/carts/{cartId}/items` | Atomically start cart workflow or add item |

```bash
curl -X POST http://localhost:8080/api/carts/cart-123/items \
  -H "Content-Type: application/json" \
  -d '{"item":"SKU-42"}'
```

## Key Features

| Feature           | Description                                                                        |
|-------------------|------------------------------------------------------------------------------------|
| Saga Pattern      | Automatic compensation on failure (refund, release inventory)                      |
| Signals           | Cancel order, update shipping address during processing                            |
| Queries           | Read order status and shipping address (read-only, immediate)                      |
| Heartbeat         | Long-running activities report progress and detect cancellation                    |
| Search Attributes | Filter workflows by customer, amount, status                                       |
| Versioning        | Safe deployments with backward compatibility (`Workflow.getVersion()`)             |
| Rate Limiting     | Prevent overwhelming external services                                             |
| Durable Timer     | `Workflow.sleep(24h)` shipping reminder — survives worker restarts                 |
| Workflow.await()  | Human-in-the-loop approvals — workflow pauses waiting for signal                   |
| Cron Workflow     | `setCronSchedule()` replaces `@Scheduled` with durable distributed scheduling      |
| Continue-As-New   | `Workflow.continueAsNew()` resets history for long-running polling workflows       |
| Non-Retryable     | `ApplicationFailure.newNonRetryableFailure()` skips retries for permanent errors   |
| Update API        | `@UpdateMethod` + `@UpdateValidatorMethod` for synchronous validated state changes |
| CancellationScope | Cancel only a scoped timer/branch without cancelling the entire workflow           |
| Async Fan-Out     | `Promise.allOf` for parallel activities in one workflow execution                  |
| SignalWithStart   | Atomic start-or-signal prevents lost early signals                                 |

## Key Patterns

### Non-Retryable Exceptions

Not all failures should be retried. `OrderValidationException` represents a permanent business rule
violation — retrying will always produce the same result.

```java
// In activity: fail immediately, skip all retries
throw ApplicationFailure.newNonRetryableFailure(
    "Invalid amount: " + amount,
    OrderValidationException.class.getName());

// In workflow RetryOptions: declare which exceptions are non-retryable
RetryOptions.newBuilder()
    .setDoNotRetry(OrderValidationException.class.getName())
    .build()
```

### Durable Timer (`Workflow.sleep`)

Unlike `Thread.sleep()`, `Workflow.sleep()` is persisted in Temporal's event history and **survives
worker restarts**. In `TestWorkflowEnvironment`, time is skipped instantly — no real waiting.

```java
// Send confirmation, then wait 24h for shipping reminder
activities.sendNotification(customerId, "Order confirmed!");
Workflow.sleep(Duration.ofHours(24)); // durable — survives restarts
activities.sendNotification(customerId, "Your order ships today!");
```

### Human-in-the-Loop (`Workflow.await`)

`Workflow.await(timeout, condition)` pauses the workflow until an external signal arrives or the
timeout expires. The pause is fully durable.

```java
// Workflow pauses here — survives restarts while waiting
boolean decided = Workflow.await(Duration.ofHours(24), () -> approved || rejected);
if (!decided) { /* auto-reject after timeout */ }
```

### Cron Workflow

```java
WorkflowOptions.newBuilder()
    .setCronSchedule("0 9 * * *")   // 9 AM UTC daily
    .setWorkflowId("daily-report")  // only one runs at a time
    .build()

// Access result of previous run (for incremental processing)
var lastResult = Workflow.getLastCompletionResult(String.class);
```

### Continue-As-New

Prevents unbounded event history growth in long-running workflows. After N iterations, start a fresh
run with the same workflow ID, passing accumulated state as arguments.

```java
if (currentRunIterations >= HISTORY_RESET_THRESHOLD) {
    // Terminates current run, starts new run with these args
    Workflow.continueAsNew(targetId, totalIterations);
}
```

### Validated Update Handler

Updates are synchronous and can be rejected before mutating workflow state.

```java
@UpdateMethod
int updateQuantity(String orderId, int newQuantity);

@UpdateValidatorMethod(updateName = "updateQuantity")
void validateUpdateQuantity(String orderId, int newQuantity);
```

### Cancellation Scope

Cancel only the reminder timer branch while allowing the workflow to complete.

```java
var scope = Workflow.newCancellationScope(() -> {
    Workflow.sleep(Duration.ofHours(24));
    activities.sendNotification(customerId, "ships today");
});
scope.run();
// later from signal handler:
scope.cancel("cancel reminder");
```
