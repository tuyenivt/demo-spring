# Temporal Subproject

## Overview

Spring Boot application demonstrating **Temporal.io** workflow orchestration. Features order processing with saga compensation, durable timers, human-in-the-loop approvals, cron scheduling, continue-as-new for long-running workflows, non-retryable exceptions, signals/queries, validated updates, activity heartbeat, search attributes, workflow versioning, cancellation scopes, async fan-out with `Promise.allOf`, and `signalWithStart`.

## Tech Stack

- Java 21+ with Virtual Threads
- Spring Boot with Actuator
- Temporal SDK 1.32.1
- Gradle build

## Project Structure

```
temporal/
├── src/main/java/com/example/temporal/
│   ├── MainApplication.java
│   ├── controller/
│   │   ├── OrderController.java          # Order workflow CRUD + signals/queries/update
│   │   ├── ApprovalController.java       # Human-in-the-loop approval workflow
│   │   ├── ReportController.java         # Cron report workflow management
│   │   ├── PollingController.java        # Continue-As-New polling workflow
│   │   ├── NotificationController.java   # Async fan-out notification workflow
│   │   └── CartController.java           # SignalWithStart cart workflow
│   ├── config/
│   │   ├── TemporalConfig.java           # Temporal client & worker setup
│   │   └── TemporalProperties.java       # Configuration properties
│   ├── workflows/
│   │   ├── OrderWorkflow.java            # Main workflow (signals, queries, @UpdateMethod)
│   │   ├── PaymentChildWorkflow.java     # Payment child workflow
│   │   ├── InventoryChildWorkflow.java
│   │   ├── ApprovalWorkflow.java         # Human-in-the-loop with Workflow.await()
│   │   ├── ReportWorkflow.java           # Cron workflow with setCronSchedule()
│   │   ├── PollingWorkflow.java          # Continue-As-New polling workflow
│   │   ├── NotificationWorkflow.java     # Async fan-out with Promise.allOf
│   │   ├── CartWorkflow.java             # SignalWithStart cart workflow
│   │   └── impl/                         # Workflow implementations
│   ├── activities/
│   │   ├── OrderActivities.java
│   │   ├── PaymentActivities.java        # With refund for saga compensation
│   │   ├── ReportActivities.java         # For cron report workflow
│   │   ├── NotificationActivities.java   # Email/SMS/push for fan-out
│   │   └── impl/                         # Activity implementations (with heartbeat)
│   ├── dto/                              # Request/Response DTOs
│   └── exception/
│       ├── OrderActivitiesException.java
│       ├── PaymentActivitiesException.java
│       └── OrderValidationException.java  # Non-retryable permanent failure
├── src/test/java/com/example/temporal/
│   └── workflows/
│       ├── OrderWorkflowTest.java         # success/failure/signal/saga/cancellation-scope
│       ├── ApprovalWorkflowTest.java      # approve/reject/auto-reject timeout
│       ├── NotificationWorkflowTest.java  # fan-out parallel activities
│       ├── PollingWorkflowTest.java       # continue-as-new iterations
│       └── ReportWorkflowTest.java        # cron last-completion-result
└── src/main/resources/
    └── application.yml
```

## API Endpoints

### Order Workflow

| Method | Endpoint                                 | Description                              |
|--------|------------------------------------------|------------------------------------------|
| POST   | `/api/orders`                            | Start order workflow async (recommended) |
| POST   | `/api/orders/sync`                       | Start workflow sync (demo only)          |
| GET    | `/api/orders/{orderId}/status`           | Get workflow execution status            |
| POST   | `/api/orders/{orderId}/cancel`           | Send cancelOrder signal (202)            |
| PUT    | `/api/orders/{orderId}/shipping-address` | Send updateShippingAddress signal (202)  |
| GET    | `/api/orders/{orderId}/shipping-address` | Query current shipping address           |
| PATCH  | `/api/orders/{orderId}/quantity`         | Validated quantity update (@UpdateMethod)|

### Approval Workflow

| Method | Endpoint                           | Description                               |
|--------|------------------------------------|-------------------------------------------|
| POST   | `/api/approvals`                   | Start approval workflow (awaits decision) |
| POST   | `/api/approvals/{orderId}/approve` | Send approve signal                       |
| POST   | `/api/approvals/{orderId}/reject`  | Send reject signal                        |
| GET    | `/api/approvals/{orderId}/status`  | Query approval status                     |

### Report Cron Workflow

| Method | Endpoint             | Description                 |
|--------|----------------------|-----------------------------|
| POST   | `/api/reports/start` | Start daily cron (9 AM UTC) |
| DELETE | `/api/reports/stop`  | Terminate cron workflow     |

### Polling Workflow (Continue-As-New)

| Method | Endpoint                         | Description                   |
|--------|----------------------------------|-------------------------------|
| POST   | `/api/polling/start?targetId=`   | Start polling workflow        |
| GET    | `/api/polling/{targetId}/status` | Query current iteration count |
| DELETE | `/api/polling/{targetId}/stop`   | Terminate polling workflow    |

### Notification Workflow (Async Fan-Out)

| Method | Endpoint                  | Description                         |
|--------|---------------------------|-------------------------------------|
| POST   | `/api/notifications/send` | Send email/SMS/push in parallel     |

### Cart Workflow (SignalWithStart)

| Method | Endpoint                    | Description                                |
|--------|-----------------------------|--------------------------------------------|
| POST   | `/api/carts/{cartId}/items` | Atomically start cart workflow or add item |

## Configuration

```yaml
temporal:
  namespace: default
  task-queue: ORDER_PROCESSING_QUEUE
  target: localhost:7233
  ui: http://localhost:8233
  worker:
    shutdown-timeout-seconds: 30
```

## Workflow Architecture

```
OrderWorkflow (Main)
├── [Signal] cancelOrder(reason)
├── [Signal] updateShippingAddress(newAddress)
├── [Query]  getStatus()
├── [Query]  getShippingAddress()
├── [Update] updateQuantity(orderId, qty) — @UpdateMethod + @UpdateValidatorMethod
│
├── validateOrder() [Activity - 3 retries, non-retryable on OrderValidationException]
│   └── Fraud check (v2 feature via versioning)
├── PaymentChildWorkflow
│   ├── authorizePayment() [Activity - 5 retries, heartbeat]
│   └── capturePayment()   [Activity - 5 retries, heartbeat]
│   └── [Saga] refundPayment() on failure
├── InventoryChildWorkflow
│   ├── checkInventory()   [Activity - 3 retries]
│   └── reserveInventory() [Activity - 3 retries]
│   └── [Saga] releaseInventory() on failure
├── sendNotification() [Activity] — order confirmed
├── CancellationScope { Workflow.sleep(24h) + reminder notification }
│   └── cancelReminderScope() from signal cancels only this branch
└── sendNotification() [Activity] — shipping reminder (inside scope)

ApprovalWorkflow
├── sendNotification() [Activity] — notify approver
├── Workflow.await(24h, () -> approved || rejected) — pause for human decision
│   ├── [Signal] approve(note)  → APPROVED
│   ├── [Signal] reject(reason) → REJECTED
│   └── timeout                 → AUTO_REJECTED
└── sendNotification() [Activity] — notify outcome

ReportWorkflow (Cron: "0 9 * * *")
├── Workflow.getLastCompletionResult() — previous run result
└── generateOrderReport() [Activity] — aggregate and report

PollingWorkflow (Continue-As-New)
├── Loop: sendNotification() [Activity] per iteration
├── Workflow.sleep(5s) between polls
└── Workflow.continueAsNew() every 10 iterations — resets event history

NotificationWorkflow (Async Fan-Out)
├── Async.procedure(sendEmail)   ─┐
├── Async.procedure(sendSms)      ├─ Promise.allOf(...).get() — parallel
└── Async.procedure(sendPush)    ─┘

CartWorkflow (SignalWithStart)
├── [Signal] addItem(item) — received on start or during execution
└── Workflow.await() until checkout or timeout
```

## Key Patterns

### 1. Non-Retryable Exceptions
Permanent failures should not be retried — the result is always the same:
```java
// Activity: fail immediately, no retries
throw ApplicationFailure.newNonRetryableFailure(
    "Invalid amount: " + amount, OrderValidationException.class.getName());

// Workflow RetryOptions: declare non-retryable exception classes
RetryOptions.newBuilder()
    .setDoNotRetry(OrderValidationException.class.getName())
    .build()
```

### 2. Durable Timer (`Workflow.sleep`)
Unlike `Thread.sleep()`, `Workflow.sleep()` persists in event history and survives worker restarts.
In `TestWorkflowEnvironment`, time is skipped instantly — no real waiting in tests.

### 3. Human-in-the-Loop (`Workflow.await`)
```java
// Pauses until signal arrives or timeout — fully durable
boolean decided = Workflow.await(Duration.ofHours(24), () -> approved || rejected);
if (!decided) { /* auto-reject */ }
```
- Always set a timeout to avoid permanently blocked workflows
- Condition lambda is re-evaluated after every signal arrival

### 4. Cron Workflow
```java
WorkflowOptions.newBuilder()
    .setCronSchedule("0 9 * * *")   // 9 AM UTC daily
    .setWorkflowId("daily-report")  // one instance at a time
    .build()
// Access previous run result for incremental processing:
var lastResult = Workflow.getLastCompletionResult(String.class);
```

### 5. Continue-As-New
Prevents unbounded event history in long-running workflows:
```java
if (currentRunIterations >= THRESHOLD) {
    Workflow.continueAsNew(targetId, totalIterations); // starts fresh run, same workflow ID
}
```

### 6. Saga Pattern
Compensations registered after each step, executed in reverse on failure:
- Payment refund if inventory reservation fails
- Inventory release if later steps fail

### 7. Workflow Signals/Queries
- Signals: fire-and-forget, modify state (`cancelOrder`, `updateShippingAddress`, `approve`, `reject`)
- Queries: read-only, immediate return (`getStatus`, `getShippingAddress`, `getApprovalStatus`)
- REST endpoints use typed stubs: `workflowClient.newWorkflowStub(OrderWorkflow.class, workflowId)`

### 8. Activity Heartbeat
- Prevents timeout for long-running activities
- Enables cancellation detection mid-operation
- Configured with 10-second heartbeat timeout in PaymentActivities

### 9. Search Attributes
- `CustomerId`, `OrderAmount`, `OrderStatus` — filter workflows in Temporal UI
- `temporal workflow list --query "CustomerId='CUST-123'"`

### 10. Workflow Versioning
- `Workflow.getVersion("FraudCheck", DEFAULT_VERSION, 2)` — v2 adds fraud check
- Running workflows continue on their original version

### 11. Validated Update Handler
Updates are synchronous (client awaits result) and can be rejected before mutating state:
```java
@UpdateMethod
int updateQuantity(String orderId, int newQuantity);

@UpdateValidatorMethod(updateName = "updateQuantity")
void validateUpdateQuantity(String orderId, int newQuantity); // throws to reject
```

### 12. Cancellation Scope
Cancel only a scoped branch (reminder timer) without cancelling the whole workflow:
```java
var scope = Workflow.newCancellationScope(() -> {
    Workflow.sleep(Duration.ofHours(24));
    activities.sendNotification(customerId, "ships today");
});
scope.run();
// from signal: scope.cancel("cancel reminder");
```

### 13. Async Fan-Out (`Promise.allOf`)
Run activities in parallel within one workflow execution:
```java
Promise<Void> email = Async.procedure(activities::sendEmail, customerId, message);
Promise<Void> sms   = Async.procedure(activities::sendSms, customerId, message);
Promise<Void> push  = Async.procedure(activities::sendPush, customerId, message);
Promise.allOf(email, sms, push).get();
```

### 14. SignalWithStart
Atomic start-or-signal prevents lost signals on race with workflow start:
```java
workflowClient.signalWithStart(CartWorkflow.class,
    CartWorkflow::addItem, new Object[]{item},
    CartWorkflow::execute, new Object[]{cartId},
    options);
```

### 15. Rate Limiting & Graceful Shutdown
- Max 10 concurrent activities, 20 concurrent workflow tasks
- `@PreDestroy` drains sticky task queue before shutdown (30s timeout)

## Running Locally

```bash
# Start Temporal server
docker run -d --name temporal --user root -p 7233:7233 -p 8233:8233 \
  -v temporal-data:/data temporalio/temporal:1.5.1 \
  server start-dev --ip 0.0.0.0 --db-filename /data/temporal.db

# Run application
./gradlew :temporal:bootRun

# Start an order (returns immediately with workflow ID)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-123","amount":9999,"description":"Widget"}'

# Start approval workflow (pauses waiting for approve/reject)
curl -X POST "http://localhost:8080/api/approvals?orderId=ORD-123&customerId=CUST-123&amount=9999"
curl -X POST http://localhost:8080/api/approvals/ORD-123/approve \
  -H "Content-Type: application/json" -d '{"note":"Approved"}'

# Start daily report cron
curl -X POST http://localhost:8080/api/reports/start
```

## Testing

```bash
./gradlew :temporal:test
```

Tests use Temporal's `TestWorkflowEnvironment` for:
- In-memory Temporal server (no Docker needed)
- Concrete stub activity implementations per test scenario
- Time skipping — `Workflow.sleep(24h)` completes instantly
- `OrderWorkflowTest`: success/failure/cancel-signal/saga-compensation/cancellation-scope
- `ApprovalWorkflowTest`: approve/reject/auto-reject on timeout
- `NotificationWorkflowTest`: parallel fan-out (email+sms+push)
- `PollingWorkflowTest`: continue-as-new iteration count
- `ReportWorkflowTest`: cron last-completion-result

## Development Notes

- Activities simulate transient failures (10-20% rate) for retry demo
- Virtual threads enabled for improved concurrency
- Temporal UI at http://localhost:8233 for workflow inspection
- Search attributes require Temporal server configuration for persistence
- `Workflow.currentTimeMillis()` must be used instead of `System.currentTimeMillis()` in workflow code
