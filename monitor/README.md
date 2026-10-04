# Monitoring Demo

Spring Boot application demonstrating monitoring and observability using Actuator, Prometheus, Grafana, and Micrometer observations.

## Quick Start

```bash
# Start the application
./gradlew :monitor:bootRun

# Start monitoring stack (Prometheus + Grafana)
docker compose -f docker/docker-compose.yml up -d
```

## Access Points

| Service     | URL                   | Credentials |
|-------------|-----------------------|-------------|
| Application | http://localhost:8080 | -           |
| Prometheus  | http://localhost:9090 | -           |
| Grafana     | http://localhost:3000 | admin/admin |

## Features

### Custom Business Metrics
- `customer.access` - Counter tracking customer list accesses
- `customer.transform` - Timer measuring transform operation duration
- `customer.count` - Gauge showing total customer count
- `db.query` - Timed repository query metric (`findAll`)

### Health Checks
- Built-in health indicators (`db`, `diskSpace`)
- Custom `externalApi` health indicator with runtime toggle
- Health groups: liveness and readiness probes

### Alerting Rules
Pre-configured Prometheus alerts in `docker/prometheus-alerts.yml`:
- High response time (p95 > 1s)
- High error rate (> 10%)
- High memory usage (heap > 90%)
- Database connection pool exhaustion
- Application down

### Grafana Dashboard
Auto-provisioned dashboard with panels for:
- Request rate by endpoint
- Response time percentiles (p50, p95, p99)
- Error rate by endpoint
- JVM heap usage
- HikariCP connection pool stats
- Active threads
- Custom business metrics

### Distributed Tracing and Correlated Logs
- Micrometer Tracing (Brave + Zipkin bridge)
- Observation API annotations via `@Observed`
- Trace/span correlation IDs included in log pattern

## API Endpoints

| Endpoint                                 | Description                                    |
|------------------------------------------|------------------------------------------------|
| `GET /ping`                              | Health check (minimal latency)                 |
| `GET /customers`                         | List all customers                             |
| `GET /customers/transform`               | Slow endpoint (0-5s delay) for latency testing |
| `GET /customers/unreliable?failureRate=` | Randomly fails for error-rate testing          |
| `POST /health/toggle`                    | Toggle custom `externalApi` health UP/DOWN     |

## Actuator Endpoints

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/metrics`
- `/actuator/prometheus`
- `/actuator/info`
- `/actuator/loggers`
- `/actuator/env`
- `/actuator/app` (custom endpoint)

## Load Testing

```bash
./gradlew :monitor:gatlingRun
```

`CustomerSimulation` ramps 5-100 users over 160 seconds and includes assertions:
- Successful requests >= 95%
- p99 response time < 5s
- Mean response time < 1s

## Tests

```bash
./gradlew :monitor:test
```

Covers:
- Actuator health/readiness/prometheus/metrics endpoints
- Controller behavior for `ping`, `customers`, and `unreliable`
