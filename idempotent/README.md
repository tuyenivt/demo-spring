# Idempotent Module

AOP-based idempotency library using Redis for duplicate request detection and response caching.

## Features

- **@Idempotent**: Full response caching with automatic replay on duplicate requests
- **HTTP status preservation**: Cached `ResponseEntity` replays keep original status codes (e.g. `201`, `204`)
- **@PreventRepeatedRequests**: Simple duplicate blocking without response caching
- **Atomic Redis operations**: Race-condition-free duplicate detection using `SETNX`
- **Per-endpoint configuration**: Override global timeout/expiry per annotation
- **Required idempotent key validation**: Returns 400 Bad Request if header is missing
- **Automatic cleanup on failure**: Retries allowed after method execution errors
- **Request validation**: Bean Validation on all request DTOs
- Redis-backed distributed idempotency
- Configurable TTL for duplicate detection and result caching
- Global exception handling with structured error responses

## Quick Start

### Prerequisites

- Java 21+
- Redis server running on localhost:6379

### Run the Application

```bash
./gradlew :idempotent:bootRun
```

### Test Idempotency

```bash
# First request - processes payment
curl -X POST http://localhost:8080/api/demo/payments \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: payment-123" \
  -d '{"amount": 100.00, "currency": "USD"}'

# Duplicate request - returns cached result (same transaction ID)
curl -X POST http://localhost:8080/api/demo/payments \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: payment-123" \
  -d '{"amount": 100.00, "currency": "USD"}'

# Refund demo with per-endpoint override (30s lock timeout)
curl -X POST http://localhost:8080/api/demo/payments/refunds \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: refund-123" \
  -d '{"amount": 25.00, "currency": "USD"}'
```

## Usage

### @Idempotent Annotation

Use for critical operations where you need to cache and replay the response:

```java
// With default global config
@PostMapping("/payments")
@Idempotent
public Payment processPayment(@Valid @RequestBody PaymentRequest request) {
    return paymentService.process(request);
}

// With custom per-endpoint config
@PostMapping("/payments")
@Idempotent(timeout = 30, timeUnit = TimeUnit.SECONDS, resultExpire = 60)
public Payment processPayment(@Valid @RequestBody PaymentRequest request) {
    return paymentService.process(request);
}
```

### @PreventRepeatedRequests Annotation

Use for simple duplicate prevention without response caching:

```java
// With default global config
@PostMapping("/subscribe")
@PreventRepeatedRequests
public void subscribe(@Valid @RequestBody SubscribeRequest request) {
    subscriptionService.subscribe(request);
}

// With custom timeout
@PostMapping("/subscribe")
@PreventRepeatedRequests(timeout = 5, timeUnit = TimeUnit.MINUTES)
public void subscribe(@Valid @RequestBody SubscribeRequest request) {
    subscriptionService.subscribe(request);
}
```

## HTTP Headers

| Header              | Required | Description                                                            |
|---------------------|----------|------------------------------------------------------------------------|
| `Idempotent-Key`    | **Yes**  | Unique identifier for the request (validated - returns 400 if missing) |
| `Idempotent-Replay` | No       | Set to `true` to force re-execution and cache update                   |

## Configuration

```yaml
app:
  idempotent:
    timeout-minutes: 10         # Duplicate detection window
    result-expire-minutes: 1440 # Result cache TTL (24 hours)
```

## Demo Endpoints

| Endpoint                     | Method | Annotation               | Use Case                                              |
|------------------------------|--------|--------------------------|-------------------------------------------------------|
| `/api/demo/payments`         | POST   | @Idempotent              | Payment processing with caching                       |
| `/api/demo/payments/refunds` | POST   | @Idempotent              | Refund processing with per-endpoint timeout override  |
| `/api/demo/payments/slow`    | POST   | @Idempotent              | Slow payment flow used to test in-progress duplicates |
| `/api/demo/orders`           | POST   | @Idempotent              | Order creation with caching                           |
| `/api/demo/orders/{orderId}` | DELETE | @Idempotent              | Idempotent order cancellation                         |
| `/api/demo/subscriptions`    | POST   | @PreventRepeatedRequests | Newsletter signup                                     |
| `/api/demo/vouchers/redeem`  | POST   | @Idempotent              | Voucher redemption with business-meaningful key       |

Voucher redemption key format example: `Idempotent-Key: SAVE10_user-42`.

## Testing

Run integration tests (requires Docker):

```bash
./gradlew :idempotent:test
```

## Error Response

Duplicate requests return HTTP 409 Conflict:

```json
{
  "code": "DUPLICATE_REQUEST",
  "message": "Request already processed or in progress",
  "detail": "...",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

Missing required headers return HTTP 400 Bad Request:

```json
{
  "code": "INVALID_REQUEST",
  "message": "Request validation failed",
  "detail": "Missing required header: Idempotent-Key",
  "timestamp": "2024-01-15T10:30:00Z"
}
```
