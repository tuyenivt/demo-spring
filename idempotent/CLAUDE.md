# Idempotent Module

## Overview

AOP-based idempotency library using Redis for duplicate request detection and response caching. Includes demo endpoints showcasing both `@Idempotent` and `@PreventRepeatedRequests` annotations.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    HTTP Request                             │
│              (with Idempotent-Key header)                   │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                  IdempotentAspect                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ @Around("@annotation(Idempotent)")                  │    │
│  │ @Before("@annotation(PreventRepeatedRequests)")     │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                      Redis                                  │
│  Key: {prefix}_{ip}_{path}_{clientKey}                      │
│  Value: "first-request" | cached result                     │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
idempotent/
├── src/main/java/com/example/idempotent/
│   ├── MainApplication.java
│   ├── config/
│   │   └── AppConfig.java              # App-level config values
│   ├── controller/
│   │   ├── PaymentDemoController.java  # @Idempotent demo (payment, refund, slow)
│   │   ├── OrderDemoController.java    # @Idempotent demo
│   │   ├── SubscriptionDemoController.java # @PreventRepeatedRequests demo
│   │   └── VoucherDemoController.java  # @Idempotent with business-meaningful key
│   ├── dto/
│   │   ├── PaymentRequest.java
│   │   ├── PaymentResponse.java
│   │   ├── OrderRequest.java
│   │   ├── OrderResponse.java
│   │   ├── OrderItem.java
│   │   ├── SubscribeRequest.java
│   │   ├── VoucherRedeemRequest.java
│   │   ├── VoucherRedeemResponse.java
│   │   └── ErrorResponse.java
│   ├── exception/
│   │   └── IdempotentExceptionHandler.java # Global exception handler
│   └── idempotent/
│       ├── Idempotent.java             # Full caching annotation
│       ├── PreventRepeatedRequests.java # Simple blocking annotation
│       ├── IdempotentAspect.java       # Core AOP logic
│       ├── CachedResponse.java         # Wrapper preserving HTTP status code
│       ├── IdempotentConfig.java       # Idempotent settings
│       ├── IdempotentException.java    # Duplicate error
│       └── IdempotentRedisConfig.java  # Redis cache config
├── src/test/java/com/example/idempotent/
│   └── IdempotentIntegrationTest.java  # Testcontainers integration tests
└── src/main/resources/
    └── application.yml
```

## Key Components

### Annotations

| Annotation                 | Purpose                                           | Use Case                              | Configuration                         |
|----------------------------|---------------------------------------------------|---------------------------------------|---------------------------------------|
| `@Idempotent`              | Caches full response, returns cached on duplicate | Payments, orders, critical operations | `timeout`, `timeUnit`, `resultExpire` |
| `@PreventRepeatedRequests` | Blocks duplicates, no result caching              | Form submissions, button clicks       | `timeout`, `timeUnit`                 |

Both annotations support per-endpoint configuration. Set `timeout = -1` to use global config.

### Cache Key Composition

**@Idempotent**: `{prefix}_{clientIP}_{requestPath}_{headerKey}`
**@PreventRepeatedRequests**: `{prefix}_{methodName}_{headerKey|args}`

### HTTP Headers

| Header                    | Required | Purpose                                                                       |
|---------------------------|----------|-------------------------------------------------------------------------------|
| `Idempotent-Key`          | **Yes**  | Client-provided unique request identifier (validated, returns 400 if missing) |
| `Idempotent-Replay: true` | No       | Force re-execution and cache update                                           |

## Configuration

```yaml
app:
  idempotent:
    timeout-minutes: 10         # Duplicate detection window
    result-expire-minutes: 1440 # Result cache TTL (24h)
    cache-store-key: my-idempotent
    client-header-key: Idempotent-Key
    client-header-replay: Idempotent-Replay
```

## Dependencies

- Spring Boot AOP
- Spring Data Redis
- Spring Boot Web
- Spring Boot Validation (Bean Validation)
- Lombok
- Testcontainers (testing)
- Mockito (unit testing)

## Request Flow

1. **New request**: Atomic lock acquisition → execute method → cache result → return
2. **Duplicate (in progress)**: Return 409 Conflict with TTL info
3. **Duplicate (completed)**: Return cached result
4. **Replay header**: Delete cache, execute method, update cache
5. **Method failure**: Delete cache key to allow retries

## Security & Reliability Features

- **Atomic Redis operations**: Uses `SETNX` to prevent race conditions on concurrent duplicate requests
- **Required idempotent key**: Validates `Idempotent-Key` header presence (returns 400 Bad Request if missing)
- **HTTP status preservation**: `CachedResponse` wraps `statusCode + body`; replayed responses restore original status (e.g. 201, 204)
- **Cleanup on failure**: Deletes cache keys when method execution fails to allow retries
- **Per-endpoint configuration**: Override global timeout/expiry settings per annotation
- **Request validation**: All DTOs validated with Bean Validation annotations

## Demo Endpoints

| Endpoint                     | Method | Annotation                 | Description                                                   |
|------------------------------|--------|----------------------------|---------------------------------------------------------------|
| `/api/demo/payments`         | POST   | `@Idempotent`              | Payment processing with full response caching                 |
| `/api/demo/payments/refunds` | POST   | `@Idempotent`              | Refund with per-endpoint override (30s timeout, 60m expire)   |
| `/api/demo/payments/slow`    | POST   | `@Idempotent`              | Slow payment (1.5s delay) to test in-progress duplicate 409   |
| `/api/demo/orders`           | POST   | `@Idempotent`              | Order creation (returns 201 CREATED), caches with status code |
| `/api/demo/orders/{orderId}` | DELETE | `@Idempotent`              | Idempotent order cancellation (returns 204 NO_CONTENT)        |
| `/api/demo/subscriptions`    | POST   | `@PreventRepeatedRequests` | Newsletter signup with simple duplicate blocking              |
| `/api/demo/vouchers/redeem`  | POST   | `@Idempotent`              | Voucher redemption; key format: `{voucherCode}_{userId}`      |

### Testing Demo Endpoints

```bash
# Payment: First request processes payment
curl -X POST http://localhost:8080/api/demo/payments \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: payment-123" \
  -d '{"amount": 100.00, "currency": "USD", "description": "Test payment"}'

# Payment: Duplicate request returns cached result (no double charge)
curl -X POST http://localhost:8080/api/demo/payments \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: payment-123" \
  -d '{"amount": 100.00, "currency": "USD", "description": "Test payment"}'

# Refund: per-endpoint override (30s lock timeout)
curl -X POST http://localhost:8080/api/demo/payments/refunds \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: refund-123" \
  -d '{"amount": 25.00, "currency": "USD", "description": "Refund"}'

# Order: Create order with idempotency (returns 201)
curl -X POST http://localhost:8080/api/demo/orders \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: order-456" \
  -d '{"items": [{"productId": "PROD-001", "productName": "Widget", "quantity": 2, "price": 50.00}], "shippingAddress": "123 Main St"}'

# Order: Cancel order (idempotent DELETE, returns 204)
curl -X DELETE http://localhost:8080/api/demo/orders/order-123 \
  -H "Idempotent-Key: cancel-456"

# Subscription: First request succeeds
curl -X POST http://localhost:8080/api/demo/subscriptions \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: sub-789" \
  -d '{"email": "test@example.com", "name": "Test User"}'

# Subscription: Duplicate returns 409 Conflict
curl -X POST http://localhost:8080/api/demo/subscriptions \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: sub-789" \
  -d '{"email": "test@example.com", "name": "Test User"}'

# Voucher: business-meaningful key = {voucherCode}_{userId}
curl -X POST http://localhost:8080/api/demo/vouchers/redeem \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: SAVE10_user-42" \
  -d '{"voucherCode": "SAVE10", "userId": "user-42"}'

# Replay: force re-execution and cache update
curl -X POST http://localhost:8080/api/demo/payments \
  -H "Content-Type: application/json" \
  -H "Idempotent-Key: payment-123" \
  -H "Idempotent-Replay: true" \
  -d '{"amount": 100.00, "currency": "USD", "description": "Test payment"}'

# Missing Idempotent-Key header returns 400 Bad Request
curl -X POST http://localhost:8080/api/demo/payments \
  -H "Content-Type: application/json" \
  -d '{"amount": 100.00, "currency": "USD"}'
```

## Tests

Integration tests (`IdempotentIntegrationTest`, Testcontainers Redis, 9 tests):

| Test                                                         | Description                                     |
|--------------------------------------------------------------|-------------------------------------------------|
| `shouldReturnCachedResponseOnDuplicatePaymentRequest`        | Same transaction ID returned on duplicate       |
| `shouldReturnCachedResponseOnDuplicateOrderRequest`          | 201 status preserved on cached order reply      |
| `shouldPreserveNoContentStatusOnDuplicateOrderCancelRequest` | 204 preserved on duplicate DELETE               |
| `shouldReturn409WhenSubscriptionDuplicated`                  | `@PreventRepeatedRequests` duplicate → 409      |
| `shouldProcessDifferentRequestsWithDifferentKeys`            | Different keys → different transaction IDs      |
| `shouldBypassIdempotencyWithReplayHeader`                    | `Idempotent-Replay: true` creates new result    |
| `shouldReturn400WhenIdempotentKeyHeaderIsMissing`            | Missing header → 400 INVALID_REQUEST            |
| `shouldAllowReplayForPreventRepeatedRequests`                | Replay clears lock, re-execution succeeds       |
| `shouldReturn409WhenRequestInProgress`                       | Concurrent slow request → 409 DUPLICATE_REQUEST |

## Error Handling

Duplicate requests return HTTP 409 Conflict:

```json
{
  "code": "DUPLICATE_REQUEST",
  "message": "Request already processed or in progress",
  "detail": "Repeated requests, previous request expired in 10 minutes",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## Build & Run

```bash
# Requires Redis running on localhost:6379
./gradlew :idempotent:bootRun

# Run integration tests (requires Docker for Testcontainers)
./gradlew :idempotent:test
```
