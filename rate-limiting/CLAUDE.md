# Rate Limiting Module

## Overview

A Spring Boot application demonstrating distributed rate limiting using Redis and the Bucket4j token bucket algorithm. Rate limits are applied declaratively via annotations with AOP interception. Supports user-based and IP-based rate limiting, named configuration profiles, both `INTERVALLY` and `GREEDY` refill strategies, class-level and repeatable annotations, stacked limits per endpoint, and standard rate-limit headers.

## Tech Stack

- Spring Boot with AOP
- Redis (Lettuce client)
- Bucket4j (token bucket algorithm)
- Testcontainers (integration testing)
- Lombok

## Project Structure

```
rate-limiting/
├── src/main/java/com/example/ratelimiting/
│   ├── MainApplication.java                        # Entry point
│   ├── controller/
│   │   ├── HomeController.java                     # REST endpoints (demo)
│   │   └── RateLimitStatusController.java          # Status endpoint (no-consume)
│   ├── dto/
│   │   ├── ConsumeResult.java                      # Token-consumption result
│   │   └── ErrorResponse.java                      # Error JSON body
│   ├── exception/
│   │   └── GlobalExceptionHandler.java             # JSON error responses
│   └── ratelimit/
│       ├── RateLimit.java                          # Annotation (method + class, repeatable)
│       ├── RateLimits.java                         # Container annotation for stacked limits
│       ├── RefillStrategy.java                     # INTERVALLY | GREEDY enum
│       ├── RateLimitAspect.java                    # AOP advice with headers + fail-open
│       ├── RateLimitService.java                   # Token bucket logic + status query
│       ├── RateLimitProperties.java                # @ConfigurationProperties for profiles
│       ├── RedisBucketConfig.java                  # Redis/Bucket4j config
│       └── UserContext.java                        # User/IP identification
├── src/test/java/com/example/ratelimiting/
│   ├── RateLimitIntegrationTest.java               # Testcontainers + concurrent test
│   ├── TestcontainersConfiguration.java
│   └── ratelimit/
│       ├── UserContextTest.java                    # Unit tests for identifier resolution
│       ├── RateLimitServiceTest.java               # Unit tests for token-bucket logic
│       └── RateLimitAspectTest.java                # Unit tests for AOP behaviour
└── src/main/resources/
    └── application.yml                             # Redis config + rate-limit profiles
```

## Key Components

### RateLimit Annotation
Method- or class-level annotation. Repeatable via `@RateLimits` container for stacked limits.

Fields:
- `limit`: Maximum requests (ignored when `profile` is set)
- `durationSeconds`: Time window (ignored when `profile` is set)
- `profile`: Named profile from `rate-limiting.profiles.*` in YAML
- `strategy`: `INTERVALLY` (default) or `GREEDY` refill

### RateLimits Annotation
Container annotation enabling multiple `@RateLimit` definitions on a single method or class
(burst + sustained protection pattern).

### RefillStrategy Enum
- `INTERVALLY` – all tokens refill at once when the window expires (token-bucket classic)
- `GREEDY` – tokens are added gradually over the window, preventing boundary burst spikes

### RateLimitAspect
AOP `@Before` advice intercepting any element annotated with `@RateLimit` or `@RateLimits` at
method or class level. Resolution order: method `@RateLimits` → method `@RateLimit` →
class `@RateLimits` → class `@RateLimit`.

Redis key format: `rate-limit:{identifier}:{method}:{durationSeconds}`

Sets rate-limit headers on every response. Returns HTTP 429 when any limit is exceeded.
**Fail-open**: when Redis is unreachable the request is allowed and a `WARN` is logged.

### RateLimitService
Manages Bucket4j token buckets via Redis. Builds `INTERVALLY` or `GREEDY` configurations.
Fixes reset-time accuracy by computing it from `probe.getNanosToWaitForRefill()`.
Exposes `getAvailableTokens()` for the status endpoint (non-consuming read).

### RateLimitProperties
`@ConfigurationProperties(prefix = "rate-limiting")` bean loaded from YAML.
Profiles define `limit`, `duration-seconds`, and optional `strategy`.

### UserContext
Request-scoped bean identifying users by:
1. `X-USER-ID` header (if present and non-blank)
2. Client IP from `X-Forwarded-For` (first IP if chain) or `RemoteAddr` as fallback

### RateLimitStatusController
`GET /api/rate-limit/status?profile={name}` — returns available token count **without**
consuming a token (uses `Bucket4j.getAvailableTokens()`).

### GlobalExceptionHandler
Converts 429 `ResponseStatusException` to structured JSON with status, error type, message,
and timestamp.

## Response Headers

All rate-limited endpoints return:
- `X-RateLimit-Limit`: Maximum requests allowed
- `X-RateLimit-Remaining`: Remaining requests in window
- `X-RateLimit-Reset`: Unix timestamp when limit resets (accurate, from probe)
- `Retry-After`: Seconds to wait (only on 429)

## Configuration

`application.yml`:
```yaml
rate-limiting:
  profiles:
    strict:
      limit: 5
      duration-seconds: 60
      strategy: INTERVALLY
    standard:
      limit: 20
      duration-seconds: 60
      strategy: GREEDY
    relaxed:
      limit: 100
      duration-seconds: 3600
      strategy: INTERVALLY
```

Redis pool: max-active=5, max-idle=5, max-wait=2s.

## API Endpoints

| Endpoint                             | Rate Limit            | Strategy   |
|--------------------------------------|-----------------------|------------|
| GET /api/hello                       | None                  | —          |
| GET /api/orders                      | 5 req/60s (strict)    | INTERVALLY |
| GET /api/search                      | 20 req/60s (standard) | GREEDY     |
| GET /api/reports                     | 100 req/3600s         | INTERVALLY |
| GET /api/submit                      | 10/s + 100/min        | Stacked    |
| GET /api/rate-limit/status?profile=X | Returns token status  | —          |

## Testing

```bash
# No rate limit
curl localhost:8080/api/hello

# Rate limited with user ID (view headers)
curl -i localhost:8080/api/orders -H "X-USER-ID: user123"

# Rate limited with IP fallback
curl -i localhost:8080/api/orders

# Stacked limits demo
curl -i localhost:8080/api/submit -H "X-USER-ID: user123"

# Check status without consuming a token
curl localhost:8080/api/rate-limit/status?profile=strict -H "X-USER-ID: user123"
```

## Integration Tests

Run with Testcontainers (requires Docker):
```bash
./gradlew :rate-limiting:test
```

Tests cover:
- Requests within limit allowed
- Requests exceeding limit rejected (429 + Retry-After)
- IP-based fallback rate limiting
- Rate-limit response headers
- No-limit endpoint behaviour
- **Concurrent requests** (20 parallel threads, asserts exactly 5 succeed)
- **Status endpoint** (non-consuming read)

## Unit Tests

Pure unit tests with Mockito (no Docker needed):
- `UserContextTest` — identifier resolution (header, blank, IP, X-Forwarded-For chain)
- `RateLimitServiceTest` — token consumption, exhaustion, reset-time from probe, available-tokens
- `RateLimitAspectTest` — header population, 429 throw, fail-open on Redis error, profile resolution, class-level annotation

## UserContext Identifier Format

Redis key prefixes:
- `user:{value}` when `X-USER-ID` header is present and non-blank
- `ip:{value}` when falling back to IP (from `X-Forwarded-For` first IP, or `RemoteAddr`)

`UserContext` has a commented-out `getUserIdFromSecurityContext()` stub for future Spring Security integration.

## Dependencies

```gradle
implementation 'org.springframework.boot:spring-boot-starter-aop'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'com.bucket4j:bucket4j_jdk17-lettuce:8.16.1'
testImplementation 'org.springframework.boot:spring-boot-testcontainers'
testImplementation 'org.testcontainers:junit-jupiter'
```

## Missing Demos

- Spring Security integration — extract user from `SecurityContext` instead of `X-USER-ID` header
- Dynamic profile reload at runtime (actuator endpoint to update `RateLimitProperties` without restart)
- Per-role rate limits (admin vs. regular user with different profiles)
- `@RateLimit` on class level demo endpoint (currently only documented, not shown in `HomeController`)
- Sliding window strategy (Bucket4j `SmoothlySpreadingRefill`) vs. fixed window comparison
- Warm-up refill (`withInitialTokens`) for gradual ramp-up on startup
- Distributed tracing integration — include `X-USER-ID` in MDC for correlated logs

## Missing Tests

- Class-level `@RateLimit` applied to a controller (integration test)
- `RateLimitAspectTest`: unknown profile name → `IllegalArgumentException`
- Status endpoint reflecting correct remaining count after partial consumption (unit level)
- Stacked limits: first limit exhausted stops processing second limit (integration test)
- `X-Forwarded-For` chain parsing in `UserContextTest` (multi-hop proxy scenario)
