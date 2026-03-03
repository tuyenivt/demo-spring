# Versioning Subproject

## Overview
Demonstrates API versioning strategies in Spring Boot with four strategies, version discovery, ETag support, metrics, and feature-flag-controlled v1 lifecycle.

## Project Structure
```
versioning/
├── src/main/java/com/example/versioning/
│   ├── EmployeeApplication.java
│   ├── controller/
│   │   ├── EmployeeController.java          # Spring Data REST BasePathAwareController (/v2/schedule)
│   │   ├── EmployeeControllerV1.java        # URI path v1, deprecation headers, @ConditionalOnProperty
│   │   ├── EmployeeControllerV2.java        # URI path v2 (/api/v2/employees)
│   │   ├── EmployeeViewController.java      # Query-param versioning via @JsonView (/api/employees/view)
│   │   ├── DepartmentController.java        # Header versioning (/location)
│   │   ├── ProductController.java           # Media type versioning (/api/products)
│   │   ├── ReportController.java            # Query-parameter versioning (/api/reports)
│   │   └── VersionDiscoveryController.java  # Version discovery (/api/versions)
│   ├── config/
│   │   ├── ApiVersionInterceptor.java       # Centralized version detection + metrics recording
│   │   ├── DataSeeder.java                  # CommandLineRunner: seeds 6 employees on startup if empty
│   │   ├── EtagConfig.java                  # ShallowEtagHeaderFilter bean
│   │   ├── OpenApiConfig.java               # SpringDoc grouped APIs (v1, v2)
│   │   ├── ResponseVersionAdvice.java       # Wraps /api/** responses in ApiResponse envelope
│   │   ├── VersionDeprecationLogger.java    # Startup warning when v1 is enabled
│   │   └── WebConfig.java                  # Registers ApiVersionInterceptor
│   ├── dto/
│   │   ├── ApiMeta.java                     # Envelope metadata (apiVersion, deprecation, timestamp)
│   │   ├── ApiResponse.java                 # Generic envelope wrapper {data, meta}
│   │   ├── ApiVersionError.java             # Error body for version errors
│   │   ├── EmployeeResponse.java            # Unified DTO with @JsonView annotations
│   │   ├── EmployeeResponseV1.java          # V1 response (simple)
│   │   ├── EmployeeResponseV2.java          # V2 response (rich: status, hireDate)
│   │   ├── EmployeeStatus.java              # Enum (ACTIVE, INACTIVE, ON_LEAVE)
│   │   ├── ProductV1.java / ProductV2.java  # Media-type versioned product DTOs
│   │   ├── ReportV1.java / ReportV2.java    # Query-param versioned report DTOs
│   │   └── Views.java                       # @JsonView markers: V1, V2 extends V1
│   ├── exception/
│   │   ├── ApiVersionException.java         # Runtime exception with requestedVersion + HttpStatus
│   │   └── VersionErrorHandler.java         # @ControllerAdvice for ApiVersionException + 406
│   ├── metrics/
│   │   ├── ApiVersionsEndpoint.java         # Custom actuator endpoint (id=api-versions)
│   │   ├── VersionUsageMetrics.java         # Micrometer counter + in-memory snapshot per version
│   │   └── VersionUsageSnapshot.java        # Record: {requests, lastSeen}
│   └── service/
│       └── EmployeeService.java             # Maps entities to V1/V2/View DTOs
└── src/main/resources/
    ├── application.properties               # basePath=/v2, api.v1.enabled=true, actuator config
    ├── application-v1-enabled.properties    # Profile: api.v1.enabled=true
    └── application-v1-disabled.properties  # Profile: api.v1.enabled=false (disables v1 controller)
```

## Versioning Strategies

### 1. URI Path Versioning
- Spring Data REST endpoints: `spring.data.rest.basePath=/v2` → `/v2/employees`
- Custom controllers: `/v1/employees` (deprecated), `/api/v2/employees` (current)

### 2. Header Versioning
- Header: `Accept-version: v2`
- Endpoint: `GET /location`
- Missing or `v2` header → 200; unknown version → 400 with `ApiVersionError`

### 3. Media Type Versioning (Content Negotiation)
- Endpoint: `GET /api/products`
- `Accept: application/vnd.company.v1+json` → `ProductV1`
- `Accept: application/vnd.company.v2+json` → `ProductV2`
- Unknown media type → 406 with `ApiVersionError`

### 4. Query Parameter Versioning
- Endpoint: `GET /api/reports?version=1|2`
- Default: version=2
- `GET /api/employees/view?version=1|2` uses `@JsonView` on a shared `EmployeeResponse` DTO

## Cross-Cutting Features

### Response Envelope (`ResponseVersionAdvice`)
All `/api/**` endpoints (except error responses and existing `ApiResponse` bodies) are wrapped:
```json
{ "data": { ... }, "meta": { "apiVersion": "v2", "deprecation": null, "timestamp": "..." } }
```
V1 responses include `"deprecation": "2025-12-31"`.

### Version Detection (`ApiVersionInterceptor`)
Priority order: query param → `Accept-version` header → media type → URI path → default `v2`.
Records Micrometer counter `api.version.requests` tagged with `api.version`, `method`, `status`.

### ETag Support (`EtagConfig`)
`ShallowEtagHeaderFilter` computes ETags for all responses. V1 and V2 produce different ETags for the same resource.

### Feature Flag — V1 Lifecycle
- `api.v1.enabled=true` (default): `EmployeeControllerV1` is active; startup warning logged
- `api.v1.enabled=false` (profile `v1-disabled`): controller excluded via `@ConditionalOnProperty`
- `VersionDiscoveryController` reflects enabled state in `/api/versions`

### Metrics (`ApiVersionsEndpoint`)
Custom actuator endpoint `GET /actuator/api-versions` returns per-version `{requests, lastSeen}`.

### OpenAPI / Swagger
SpringDoc grouped APIs: group `v1` matches `/v1/**`, group `v2` matches `/api/v2/**`.

## Key Endpoints

| Method | Path                   | Strategy         | Notes                                                |
|--------|------------------------|------------------|------------------------------------------------------|
| GET    | /v2/employees          | URI path         | Spring Data REST (paginated, HAL)                    |
| POST   | /v2/employees          | URI path         | Spring Data REST create                              |
| GET    | /v1/employees          | URI path         | Deprecated; includes Deprecation/Sunset/Link headers |
| GET    | /v1/employees/{id}     | URI path         | Deprecated single employee                           |
| GET    | /api/v2/employees      | URI path         | Rich V2 DTOs wrapped in envelope                     |
| GET    | /api/v2/employees/{id} | URI path         | Single employee with ETag                            |
| GET    | /v2/schedule           | URI path         | Spring Data REST BasePathAware                       |
| GET    | /location              | Header           | `Accept-version: v2` (or omit)                       |
| GET    | /v2/location           | URI path         | Path-versioned alias for location                    |
| GET    | /api/products          | Media type       | `application/vnd.company.v1+json` or `v2+json`       |
| GET    | /api/reports           | Query param      | `?version=1` or `?version=2` (default 2)             |
| GET    | /api/employees/view    | Query + JsonView | `?version=1` or `?version=2`                         |
| GET    | /api/versions          | —                | Version discovery (current, supported, strategies)   |
| GET    | /actuator/health       | —                | Health check                                         |
| GET    | /actuator/api-versions | —                | Per-version request counts                           |

## Deprecation Headers (V1)
```
Deprecation: true
Sunset: Sat, 31 Dec 2025 23:59:59 GMT
Link: </v2/employees>; rel="successor-version"
```

## Error Response Shape
```json
{
  "error": "Unsupported API version",
  "requestedVersion": "v3",
  "supportedVersions": ["v1", "v2"],
  "currentVersion": "v2",
  "documentation": "/api/versions"
}
```

## Tech Stack
- Spring Boot with Spring Data REST
- Spring Data JPA + H2 in-memory database
- Micrometer (Prometheus-compatible counters)
- Spring Boot Actuator (custom endpoint)
- SpringDoc OpenAPI (Swagger UI)
- Bean Validation, Lombok

## Running
```bash
./gradlew :versioning:bootRun
```

## Testing
```bash
./gradlew :versioning:test
```

Test file: `VersioningIntegrationTest.java` contains two top-level classes:
- `VersioningIntegrationTest` — nested suites per strategy: `UriPathVersioning`, `HeaderVersioning`, `MediaTypeVersioning`, `QueryParameterVersioning`, `JsonViewFieldEvolution`, `ETagBehavior`, `DiscoveryAndEnvelope`, `VersionContract`
- `V1DisabledProfileIntegrationTest` — same file, `@ActiveProfiles("v1-disabled")`: verifies 404 on `/v1/employees` and single-version discovery
