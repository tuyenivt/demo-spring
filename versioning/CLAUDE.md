# Versioning Subproject

## Overview
Demonstrates API versioning with Spring Framework 7's native API versioning (Spring Boot 4.1, Java 25): four strategies (URI path, header, query parameter, media type parameter), version discovery, ETag support, metrics, RFC 9745 deprecation headers, and feature-flag-controlled v1 lifecycle.

## Project Structure
```
versioning/
├── src/main/java/com/example/versioning/
│   ├── EmployeeApplication.java
│   ├── controller/
│   │   ├── EmployeeController.java          # Spring Data REST BasePathAwareController (/v2/schedule)
│   │   ├── EmployeeControllerV1.java        # URI path v1, @ConditionalOnBooleanProperty(api.v1.enabled)
│   │   ├── EmployeeControllerV2.java        # URI path v2 (/api/v2/employees)
│   │   ├── EmployeeViewController.java      # @GetMapping(version) + @JsonView (/api/employees/view)
│   │   ├── DepartmentController.java        # @GetMapping(path = "/location", version = "2") + /v2/location alias
│   │   ├── ProductController.java           # @GetMapping(version = "1"|"2") (/api/products, media type param)
│   │   ├── ReportController.java            # @GetMapping(version = "1"|"2") (/api/reports, query param)
│   │   └── VersionDiscoveryController.java  # Version discovery (/api/versions)
│   ├── config/
│   │   ├── ApiVersionConfig.java            # ApiVersionResolver (URI path prefix) + StandardApiVersionDeprecationHandler beans
│   │   ├── ApiVersionInterceptor.java       # Metrics/logging; currentVersion() reads the resolved ApiVersionHolder
│   │   ├── DataSeeder.java                  # CommandLineRunner: seeds 6 employees on startup if empty
│   │   ├── EtagConfig.java                  # ShallowEtagHeaderFilter bean
│   │   ├── OpenApiConfig.java               # SpringDoc grouped APIs (v1, v2)
│   │   ├── ResponseVersionAdvice.java       # Wraps /api/** responses in ApiResponse envelope
│   │   ├── VersionDeprecationLogger.java    # Startup warning when v1 is enabled
│   │   └── WebConfig.java                   # Registers ApiVersionInterceptor
│   ├── dto/
│   │   ├── ApiMeta.java                     # Envelope metadata (apiVersion, deprecation, timestamp)
│   │   ├── ApiResponse.java                 # Generic envelope wrapper {data, meta}
│   │   ├── ApiVersionError.java             # Error body for version errors
│   │   ├── EmployeeResponse.java            # Unified DTO with @JsonView annotations
│   │   ├── EmployeeResponseV1.java          # V1 response (simple)
│   │   ├── EmployeeResponseV2.java          # V2 response (rich: status, hireDate)
│   │   ├── EmployeeStatus.java              # Enum (ACTIVE, ON_LEAVE, TERMINATED)
│   │   ├── ProductV1.java / ProductV2.java  # Versioned product DTOs
│   │   ├── ReportV1.java / ReportV2.java    # Versioned report DTOs
│   │   └── Views.java                       # @JsonView markers: V1, V2 extends V1
│   ├── exception/
│   │   └── VersionErrorHandler.java         # @ControllerAdvice: InvalidApiVersionException → ApiVersionError
│   ├── metrics/
│   │   ├── ApiVersionsEndpoint.java         # Custom actuator endpoint (id=api-versions)
│   │   ├── VersionUsageMetrics.java         # Micrometer counter + in-memory snapshot per version
│   │   └── VersionUsageSnapshot.java        # Record: {requests, lastUsed}
│   ├── repository/
│   │   └── EmployeeRepository.java          # ListPagingAndSortingRepository + ListCrudRepository
│   └── service/
│       └── EmployeeService.java             # Maps entities to V1/V2/View DTOs
└── src/main/resources/
    ├── application.properties               # basePath=/v2, spring.mvc.apiversion.*, api.v1.enabled, actuator access
    ├── application-v1-enabled.properties    # Profile: api.v1.enabled=true
    └── application-v1-disabled.properties   # Profile: api.v1.enabled=false + spring.mvc.apiversion.supported=2
```

## Native API Versioning

`application.properties`:
```properties
spring.mvc.apiversion.use.header=API-Version
spring.mvc.apiversion.use.query-parameter=version
spring.mvc.apiversion.use.media-type-parameter[application/vnd.company+json]=v
spring.mvc.apiversion.supported=1,2
spring.mvc.apiversion.default=2
spring.mvc.apiversion.detect-supported=false
```

- Boot's MVC auto-configuration applies the property resolvers, then `ApiVersionResolver` beans, then the `ApiVersionDeprecationHandler` bean
- Resolver order: header → query param → media type param → URI path prefix (`ApiVersionConfig#pathPrefixApiVersionResolver`: `/v1/**` → 1, `/v2/**` and `/api/v2/**` → 2) → default 2
- Resolvers are global: every request through `RequestMappingHandlerMapping` gets a version; unversioned mappings match any version
- Versions are parsed by `SemanticApiVersionParser` (`1` → `1.0.0`, leading non-digits like `v` skipped)
- `detect-supported=false`: only `spring.mvc.apiversion.supported` is accepted (mappings don't add versions), so the v1-disabled profile can drop v1
- Resolved version is stored as `ApiVersionHolder` in request attribute `HandlerMapping.API_VERSION_ATTRIBUTE`; `ApiVersionInterceptor.currentVersion()` maps it to `v<major>` (or `unversioned`)

## Versioning Strategies

### 1. URI Path Versioning
- Spring Data REST endpoints: `spring.data.rest.basePath=/v2` → `/v2/employees`
- Custom controllers: `/v1/employees` (deprecated), `/api/v2/employees` (current) — plain mappings; the path resolver bean assigns their version

### 2. Header Versioning
- Header: `API-Version: 2`; endpoint `GET /location` (`version = "2"`)
- Missing header → default 2 → 200; unsupported (`3`) or unparsable → 400 `ApiVersionError`; `API-Version: 1` → 404 (supported, but no v1 handler)

### 3. Media Type Versioning
- Endpoint: `GET /api/products`
- `Accept: application/vnd.company+json;v=1` → `ProductV1`; `;v=2` → `ProductV2`; `;v=3` → 400

### 4. Query Parameter Versioning
- Endpoint: `GET /api/reports?version=1|2` (default 2)
- `GET /api/employees/view?version=1|2` uses `@JsonView` on a shared `EmployeeResponse` DTO

## Cross-Cutting Features

### Response Envelope (`ResponseVersionAdvice`)
All `/api/**` endpoints (except error responses and existing `ApiResponse` bodies) are wrapped:
```json
{ "data": { ... }, "meta": { "apiVersion": "v2", "deprecation": null, "timestamp": "..." } }
```
V1 responses include `"deprecation": "2025-12-31"` (`ApiVersionConfig.V1_SUNSET_DATE`).

### Deprecation Headers (`ApiVersionConfig`)
`StandardApiVersionDeprecationHandler` configured for version `1` (only when `api.v1.enabled`), applied to every request resolved to v1:
```
Deprecation: @1735689600
Link: </api/versions>; rel="deprecation"; type="text/html"
Sunset: Wed, 31 Dec 2025 23:59:59 GMT
```

### Metrics (`ApiVersionInterceptor`, `ApiVersionsEndpoint`)
Records Micrometer counter `api.version.requests` tagged with `api.version`, `method`, `status`.
Custom actuator endpoint `GET /actuator/api-versions` returns per-version `{requests, lastUsed}`.
Requests rejected during version resolution never reach the interceptor and are not counted.

### ETag Support (`EtagConfig`)
`ShallowEtagHeaderFilter` computes ETags for all responses. V1 and V2 produce different ETags for the same resource.

### Feature Flag — V1 Lifecycle
- `api.v1.enabled=true` (default): `EmployeeControllerV1` + deprecation handler active; startup warning logged
- `api.v1.enabled=false` (profile `v1-disabled`): controller and handler excluded via `@ConditionalOnBooleanProperty`; profile also sets `spring.mvc.apiversion.supported=2` so v1 → 400 on versioned endpoints
- `VersionDiscoveryController` reflects enabled state in `/api/versions`

### Actuator
`management.endpoints.access.default=none`; `health` and `api-versions` set to `unrestricted` and exposed over web.

### OpenAPI / Swagger
SpringDoc grouped APIs: group `v1` matches `/v1/**`, group `v2` matches `/api/v2/**`.

## Key Endpoints

| Method | Path                   | Strategy           | Notes                                                |
|--------|------------------------|--------------------|------------------------------------------------------|
| GET    | /v2/employees          | URI path           | Spring Data REST (paginated, HAL)                    |
| POST   | /v2/employees          | URI path           | Spring Data REST create                              |
| GET    | /v1/employees          | URI path           | Deprecated; Deprecation/Sunset/Link headers          |
| GET    | /v1/employees/{id}     | URI path           | Deprecated single employee                           |
| GET    | /api/v2/employees      | URI path           | Rich V2 DTOs wrapped in envelope                     |
| GET    | /api/v2/employees/{id} | URI path           | Single employee with ETag                            |
| GET    | /v2/schedule           | URI path           | Spring Data REST BasePathAware                       |
| GET    | /location              | Header             | `API-Version: 2` (or omit)                           |
| GET    | /v2/location           | URI path           | Path-versioned alias for location                    |
| GET    | /api/products          | Media type param   | `application/vnd.company+json;v=1` or `;v=2`         |
| GET    | /api/reports           | Query param        | `?version=1` or `?version=2` (default 2)             |
| GET    | /api/employees/view    | Query + JsonView   | `?version=1` or `?version=2`                         |
| GET    | /api/versions          | —                  | Version discovery (current, supported, strategies)   |
| GET    | /actuator/health       | —                  | Health check                                         |
| GET    | /actuator/api-versions | —                  | Per-version request counts                           |

## Error Response Shape
`InvalidApiVersionException` (400) → `VersionErrorHandler`:
```json
{
  "error": "Unsupported API version",
  "requestedVersion": "3.0.0",
  "supportedVersions": ["1", "2"],
  "currentVersion": "2",
  "documentation": "/api/versions"
}
```
`requestedVersion` is the parsed version for unsupported values, or the raw value when it can't be parsed.

## Tech Stack
- Spring Boot 4.1 (Spring Framework 7 API versioning) with Spring Data REST
- Spring Data JPA + H2 in-memory database
- Micrometer (Prometheus-compatible counters)
- Spring Boot Actuator (custom endpoint)
- SpringDoc OpenAPI 3.x (Swagger UI)
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
- `V1DisabledProfileIntegrationTest` — same file, `@ActiveProfiles("v1-disabled")`: 404 on `/v1/employees`, 400 for `?version=1`, single-version discovery
