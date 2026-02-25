# Spring AOP Demo

Annotation-driven Spring AOP demo with production-style concerns and educational examples.

## Run

```bash
./gradlew :aop:test
./gradlew :aop:bootRun
```

## Aspects

- `@ExecutionLogging` (`ExecutionLoggingAspect`) for detailed method logs
- `@MonitorPerformance` (`PerformanceMonitoringAspect`) for slow-call warnings
- `@Retryable` (`RetryAspect`) for retry-on-exception
- `@SimpleCache` (`CacheAspect`) for in-memory method result caching
- `@RequiresRole` (`AuthorizationAspect`) for role checks
- `@Audited` (`AuditAspect`) for success/failure audit logs
- `ControllerLoggingAspect` for `@RestController` entry/exit + correlation id
- `DemoAspect` for all 5 advice types and pointcut composition
- `@RateLimited` (`RateLimitingAspect`) using per-method/per-caller fixed-window limits
- `@DemoTransactional` (`DemoTransactionAspect`) with `BEGIN/COMMIT/ROLLBACK` simulation
- `@ValidateArgs` (`ValidationAspect`) with custom `@NotNull`, `@Min`, `@Max`
- `@FeatureEnabled` (`FeatureFlagAspect`) with runtime `FeatureFlagsRegistry`
- `@Timed` (`TimedAspect`) with aggregated in-memory `MetricsRegistry`

## Aspect Ordering

Lower `@Order` runs first (outer wrapper):

| Order | Aspect                        |
|-------|-------------------------------|
| -1    | `DemoTransactionAspect`       |
| 0     | `RetryAspect`                 |
| 1     | `ControllerLoggingAspect`     |
| 2     | `ExecutionLoggingAspect`      |
| 3     | `PerformanceMonitoringAspect` |
| 4     | `TimedAspect`                 |
| 10    | `DemoAspect`                  |

## REST Endpoints

| Method | Path                                        | Purpose / Aspect coverage                                     |
|--------|---------------------------------------------|---------------------------------------------------------------|
| GET    | `/accounts/{id}`                            | Validation + cache + performance + timed + controller logging |
| POST   | `/accounts`                                 | Execution logging + audit                                     |
| DELETE | `/accounts/{id}` (`X-Role: ADMIN`)          | Authorization + audit                                         |
| GET    | `/accounts/retry/{id}`                      | Retry demo                                                    |
| GET    | `/accounts/rate-limited/{id}`               | Rate limiting demo                                            |
| GET    | `/batch?factor=2`                           | Self-invocation proxy limitation demo                         |
| POST   | `/transfer?fromId=1&toId=2&amount=100`      | Transaction + retry (commit path)                             |
| POST   | `/transfer/fail?fromId=1&toId=2&amount=100` | Transaction rollback path                                     |
| GET    | `/pricing/{amountCents}`                    | Feature flag guarded behavior                                 |
| GET    | `/metrics`                                  | Returns aggregated timing metrics                             |
| GET    | `/flags`                                    | Lists runtime feature flags                                   |
| PUT    | `/flags/{flag}?enabled=true/false`          | Toggle feature flags                                          |

## Notes

- Feature flags can disable the guarded method entirely (`aop.feature-flags.aspect-enabled=true`).
- Validation uses custom lightweight annotations, not `jakarta.validation`.
- `ApiExceptionHandler` maps:
  - `AccessDeniedException` -> `403`
  - `RateLimitExceededException` -> `429`
  - `FeatureDisabledException` -> `404`
  - `IllegalArgumentException` -> `400`

## Tests

`aop` includes integration tests for:
- Existing aspects (execution, performance, retry, cache, auth, demo, self-invocation)
- New aspects (`rate-limit`, `feature`, `transaction`, `validation`, `timed`)
- Controller logging via `MockMvc`
