# Monitor Subproject

## Overview

Spring Boot application demonstrating monitoring and observability using Actuator, Prometheus, and Grafana.

- **Java**: 25
- **Spring Boot**: 3.5.10
- **Database**: H2 (in-memory)

## Project Structure

```
monitor/
├── src/main/java/com/example/monitor/
│   ├── SpringMonitorApplication.java       # Entry point
│   ├── config/
│   │   ├── MetricsConfig.java              # Aspects for @Timed, @Counted, @Observed + Random bean
│   │   └── DataSeederConfig.java           # Sample data seeder
│   ├── controller/RootController.java      # REST endpoints
│   ├── endpoint/AppEndpoint.java           # Custom actuator endpoint (/actuator/app)
│   ├── entity/Customer.java                # JPA entity
│   ├── health/
│   │   └── ExternalApiHealthIndicator.java # Custom health check (toggleable)
│   ├── info/AppInfoContributor.java        # Custom /actuator/info contributor
│   ├── metrics/CustomerMetrics.java        # customer.total Gauge (MeterBinder)
│   └── repository/CustomerRepository.java
├── src/main/resources/application.yml      # Configuration
├── src/gatling/java/.../CustomerSimulation.java  # Load testing
├── docker/
│   ├── docker-compose.yml                  # Prometheus + Grafana stack
│   ├── prometheus.yml                      # Scrape configuration
│   ├── prometheus-alerts.yml               # Alert rules
│   ├── grafana-dashboard.json              # Pre-built dashboard
│   └── grafana-provisioning/               # Auto-provisioning configs
└── build.gradle
```

## Key Dependencies

- `spring-boot-starter-actuator` - Monitoring endpoints
- `spring-boot-starter-aop` - Required for `@Timed`/`@Counted`/`@Observed` aspect processing
- `spring-boot-starter-data-jpa` - Data persistence
- `micrometer-registry-prometheus` - Prometheus metrics export
- `micrometer-tracing-bridge-brave` - Distributed tracing
- `zipkin-reporter-brave` - Zipkin integration
- `h2` - Embedded database
- `lombok` - Boilerplate reduction
- `io.gatling.gradle` - Load testing

## API Endpoints

| Endpoint                    | Purpose                                                                 |
|-----------------------------|-------------------------------------------------------------------------|
| `GET /ping`                 | Health check (minimal latency)                                          |
| `GET /customers`            | List all customers (increments `customer.access` counter, `@Observed`)  |
| `GET /customers/transform`  | Slow endpoint (0-5s delay), timed by `customer.transform` (`@Observed`) |
| `GET /customers/unreliable` | Returns 500 randomly; `?failureRate=30` (0–100, default 30%)            |
| `POST /health/toggle`       | Toggles `ExternalApiHealthIndicator` between UP/DOWN                    |

## Custom Metrics

- `customer.access` - Counter for customer list accesses (`@Counted`)
- `customer.transform` - Timer for transform operation duration (`@Timed`); SLOs at 500ms/1s/3s, p50/p95/p99
- `customer.total` - Gauge tracking total customers in DB (`CustomerMetrics` via `MeterBinder`)
- `customer.list` / `customer.transform.observed` - Observation spans via `@Observed`
- `db.query` - `@Timed` on `CustomerRepository.findAll()`; extra tags `entity=customer, operation=findAll`

## Actuator Endpoints

- `/actuator/health` - Health status with details
- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe (includes db, externalApi)
- `/actuator/metrics` - Available metrics
- `/actuator/prometheus` - Prometheus-formatted metrics
- `/actuator/info` - App info (env + `AppInfoContributor`: name, description, java.version)
- `/actuator/app` - Custom endpoint: customerCount + timestamp
- `/actuator/loggers` - Logger levels
- `/actuator/env` - Environment properties

## Configuration Highlights

```yaml
# Health groups
management:
  endpoint:
    health:
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState, db, externalApi

# Metrics distribution
management:
  metrics:
    distribution:
      percentiles[http.server.requests]: 0.5, 0.7, 0.95, 0.99
      percentiles-histogram[http.server.requests]: true
      slo[http.server.requests]: 10ms, 100ms
      slo[customer.transform]: 500ms, 1s, 3s
      percentiles[customer.transform]: 0.5, 0.95, 0.99

# Tracing
management:
  tracing:
    sampling:
      probability: 1.0

# HikariCP pool sizing
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
      leak-detection-threshold: 2000
```

## Running the Application

```bash
# Start the app
./gradlew :monitor:bootRun

# Start monitoring stack
docker compose -f monitor/docker/docker-compose.yml up -d
```

## Access Points

- **Application**: http://localhost:8080
- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)

## Grafana Dashboard

Auto-provisioned on startup with panels for:
- Request rate by endpoint
- Response time percentiles (p50, p95, p99)
- Error rate by endpoint
- JVM heap usage
- HikariCP connection pool stats
- Active threads
- Custom business metrics

## Prometheus Alerts

Pre-configured alerts in `prometheus-alerts.yml`:
- `HighResponseTime` - p95 > 1s for 1 minute
- `HighErrorRate` - Error rate > 10% for 2 minutes
- `HighMemoryUsage` - Heap > 90% for 5 minutes
- `DatabaseConnectionPoolExhausted` - Pool > 90% for 2 minutes
- `ApplicationDown` - App unreachable for 1 minute

## Load Testing

```bash
./gradlew :monitor:gatlingRun
```

Runs `CustomerSimulation`: ramps 5-100 users over 160 seconds hitting `/customers` and `/customers/transform`. Assertions: success rate ≥ 95%, p99 < 5s, mean < 1s.

## Tests

Two test classes:
- `SpringMonitorApplicationTests` (`@SpringBootTest`, random port) - 4 tests: health UP, readiness includes db+externalApi, prometheus contains custom metrics, metrics endpoint lists `customer.access`/`customer.transform`/`db.query`
- `RootControllerTest` (`@WebMvcTest`) - 3 tests: ping, customers list, unreliable bad request validation; mocks `CustomerRepository`, `ExternalApiHealthIndicator`, `Random`

```bash
./gradlew :monitor:test
```

## Architecture

```
Controller Layer (@RestController)
       ↓
Repository Layer (JpaRepository)
       ↓
Entity Layer (@Entity)
```

Uses constructor injection via Lombok `@RequiredArgsConstructor`.
