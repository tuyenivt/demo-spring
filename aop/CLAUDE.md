# AOP Subproject

## Overview
Production-ready Spring AOP demonstration showcasing annotation-driven aspects with clear separation of concerns.

## Architecture

```
Production Aspects (opt-in via annotations):
├── ExecutionLoggingAspect       - @ExecutionLogging for detailed method logging
├── PerformanceMonitoringAspect  - @MonitorPerformance for slow method detection
├── RetryAspect                  - @Retryable for automatic retry
├── CacheAspect                  - @SimpleCache for method-level caching
├── AuthorizationAspect          - @RequiresRole for RBAC
├── AuditAspect                  - @Audited for audit trails
├── RateLimitingAspect           - @RateLimited with per-method fixed-window limits
├── DemoTransactionAspect        - @DemoTransactional with BEGIN/COMMIT/ROLLBACK simulation
├── ValidationAspect             - @ValidateArgs with custom @NotNull, @Min, @Max
├── FeatureFlagAspect            - @FeatureEnabled with runtime FeatureFlagsRegistry
└── TimedAspect                  - @Timed with aggregated in-memory MetricsRegistry

Layer-Specific Aspects (automatic):
└── ControllerLoggingAspect      - Auto-logs @RestController with correlation IDs

Educational Aspects (demonstrations only):
└── DemoAspect                   - Shows all 5 advice types + pointcut composition
```

## Key Design Principles

1. **Annotation-Driven**: Prefer explicit `@Annotation` opt-in over blanket execution pointcuts
2. **Single Responsibility**: Each aspect has ONE clear purpose
3. **Production-Ready**: Configurable, testable, and maintainable
4. **Educational Separation**: Demo code isolated in DemoAspect
5. **Package by Feature**: Related files grouped in subpackages

## Project Structure
```
aop/
├── build.gradle
└── src/main/java/com/example/aop/
    ├── MainApplication.java
    ├── aspect/
    │   ├── audit/                             # Audit concern
    │   │   ├── AuditAspect.java
    │   │   ├── Audited.java
    │   │   └── AuditLog.java
    │   ├── auth/                              # Authorization concern
    │   │   ├── AccessDeniedException.java
    │   │   ├── AuthorizationAspect.java
    │   │   ├── RequiresRole.java
    │   │   └── SecurityContext.java
    │   ├── cache/                             # Caching concern
    │   │   ├── CacheAspect.java
    │   │   └── SimpleCache.java
    │   ├── feature/                           # Feature flags concern
    │   │   ├── FeatureDisabledException.java
    │   │   ├── FeatureEnabled.java
    │   │   ├── FeatureFlagAspect.java
    │   │   └── FeatureFlagsRegistry.java
    │   ├── metrics/                           # Metrics concern
    │   │   ├── MetricsRegistry.java
    │   │   ├── Timed.java
    │   │   └── TimedAspect.java
    │   ├── ratelimit/                         # Rate limiting concern
    │   │   ├── RateLimitExceededException.java
    │   │   ├── RateLimited.java
    │   │   └── RateLimitingAspect.java
    │   ├── retry/                             # Retry concern
    │   │   ├── RetryAspect.java
    │   │   └── Retryable.java
    │   ├── transaction/                       # Transaction simulation concern
    │   │   ├── DemoTransactionAspect.java
    │   │   ├── DemoTransactional.java
    │   │   └── FakeTransactionManager.java
    │   ├── validation/                        # Argument validation concern
    │   │   ├── Max.java
    │   │   ├── Min.java
    │   │   ├── NotNull.java
    │   │   ├── ValidateArgs.java
    │   │   └── ValidationAspect.java
    │   ├── ControllerLoggingAspect.java       # @RestController logging (@Order 1)
    │   ├── DemoAspect.java                    # Educational demos (@Order 10)
    │   ├── ExecutionLogging.java
    │   ├── ExecutionLoggingAspect.java        # @ExecutionLogging handler (@Order 2)
    │   ├── MonitorPerformance.java
    │   └── PerformanceMonitoringAspect.java   # @MonitorPerformance handler (@Order 3)
    ├── controller/
    │   ├── AccountController.java             # REST endpoints
    │   └── ApiExceptionHandler.java           # @ControllerAdvice exception mapping
    ├── dao/
    │   └── AccountDao.java                    # @Repository
    ├── entity/
    │   └── Account.java                       # Domain model
    └── service/
        └── AccountService.java                # @Service
```

## Aspect Ordering

Aspects execute in `@Order` sequence (lower = higher priority / outermost wrapper):

| Order | Aspect                        | Purpose                            |
|-------|-------------------------------|------------------------------------|
| -1    | `DemoTransactionAspect`       | Outermost transaction wrapper      |
| 0     | `RetryAspect`                 | Retry wrapper                      |
| 1     | `ControllerLoggingAspect`     | Correlation ID for @RestController |
| 2     | `ExecutionLoggingAspect`      | @ExecutionLogging detailed logs    |
| 3     | `PerformanceMonitoringAspect` | @MonitorPerformance slow detection |
| 4     | `TimedAspect`                 | @Timed metrics aggregation         |
| 10    | `DemoAspect`                  | Educational demos (low priority)   |

## Annotations

| Annotation            | Target       | Purpose                                          |
|-----------------------|--------------|--------------------------------------------------|
| `@ExecutionLogging`   | Method       | Detailed execution logging with timing           |
| `@MonitorPerformance` | Method, Type | Detect slow methods (configurable threshold)     |
| `@Audited`            | Method       | Record audit trail (action + entity)             |
| `@Retryable`          | Method       | Retry on failure (maxAttempts, retryOn)          |
| `@SimpleCache`        | Method       | Method-level caching backed by ConcurrentHashMap |
| `@RequiresRole`       | Method       | Role-based authorization via SecurityContext     |
| `@RateLimited`        | Method       | Fixed-window rate limiting (requestsPerSecond)   |
| `@DemoTransactional`  | Method       | BEGIN/COMMIT/ROLLBACK simulation                 |
| `@ValidateArgs`       | Method       | Trigger param validation (@NotNull/@Min/@Max)    |
| `@FeatureEnabled`     | Method       | Guard method behind a named feature flag         |
| `@Timed`              | Method       | Record invocation time to MetricsRegistry        |

## REST Endpoints

| Method | Path                                        | Aspect coverage                                               |
|--------|---------------------------------------------|---------------------------------------------------------------|
| GET    | `/accounts/{id}`                            | Validation + cache + performance + timed + controller logging |
| POST   | `/accounts`                                 | Execution logging + audit                                     |
| DELETE | `/accounts/{id}` (`X-Role: ADMIN`)          | Authorization + audit                                         |
| GET    | `/accounts/retry/{id}`                      | Retry demo                                                    |
| GET    | `/accounts/rate-limited/{id}`               | Rate limiting demo                                            |
| GET    | `/batch?factor=2`                           | Self-invocation proxy limitation demo                         |
| POST   | `/transfer?fromId=1&toId=2&amount=100`      | Transaction + retry (commit path)                             |
| POST   | `/transfer/fail?fromId=1&toId=2&amount=100` | Transaction rollback path                                     |
| GET    | `/pricing/{amountCents}`                    | Feature flag + timed                                          |
| GET    | `/metrics`                                  | Returns aggregated MetricsRegistry snapshot                   |
| GET    | `/flags`                                    | Lists runtime feature flags                                   |
| PUT    | `/flags/{flag}?enabled=true/false`          | Toggle feature flags at runtime                               |

## Exception Mapping (`ApiExceptionHandler`)

| Exception                    | HTTP Status |
|------------------------------|-------------|
| `AccessDeniedException`      | 403         |
| `RateLimitExceededException` | 429         |
| `FeatureDisabledException`   | 404         |
| `IllegalArgumentException`   | 400         |

## Notable Patterns

### Self-Invocation Limitation
Spring AOP is proxy-based. Internal method calls bypass the proxy:
```java
public void processBatch(int factor) {
    serve(factor); // Bypasses @ExecutionLogging — self-invocation!
}
```
**Workarounds**: inject self, use `AopContext.currentProxy()`, or move to separate bean.

### Pointcut Composition
```java
@Pointcut("@within(Service) && @annotation(ExecutionLogging)")  // AND
@Pointcut("execution(* com.example.aop.dao..*(..)) && !execution(* com.example.aop.entity..*(..))")  // NOT
```

### Feature Flags
- `FeatureFlagsRegistry` holds runtime flag state (defaults from `aop.feature-flags.*` properties)
- `aop.feature-flags.aspect-enabled=true` — master switch to enable/disable the aspect itself
- Toggle at runtime via `PUT /flags/{flag}?enabled=true`

### Validation
Uses custom lightweight annotations (`@NotNull`, `@Min`, `@Max`), not `jakarta.validation`.
`@ValidateArgs` on the method triggers `ValidationAspect` to inspect parameter annotations.

### Rate Limiting
`RateLimitingAspect` uses a per-method+per-caller `FixedWindowRateLimiter` (inner class).
Keyed by `method signature + caller thread name`.

## Build & Run
```bash
./gradlew :aop:test
./gradlew :aop:bootRun
```

## Dependencies
- `spring-boot-starter-aop` — Core AOP support
- `spring-boot-starter-web` — REST endpoints
- Lombok — Annotation processing
- JUnit 5 + `OutputCaptureExtension` — Testing with log assertion
