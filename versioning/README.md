# Versioning Demo

Demonstrates API versioning in Spring Boot 4 using Spring Framework's native API versioning (`spring.mvc.apiversion.*`, `@GetMapping(version = ...)`), with four strategies plus response envelopes, ETag caching, Micrometer metrics, RFC 9745 deprecation headers, feature-flag-controlled v1 lifecycle, and OpenAPI grouped docs.

## Quick Start

```bash
./gradlew :versioning:bootRun
```

Server starts on `http://localhost:8080`.

## Prerequisites

- Java 25

## How Versions Are Resolved

Configured in `application.properties`; Spring MVC resolves, parses and validates the version of every request before handler matching:

```properties
spring.mvc.apiversion.use.header=API-Version
spring.mvc.apiversion.use.query-parameter=version
spring.mvc.apiversion.use.media-type-parameter[application/vnd.company+json]=v
spring.mvc.apiversion.supported=1,2
spring.mvc.apiversion.default=2
spring.mvc.apiversion.detect-supported=false
```

Resolvers are tried in order — header, query parameter, media type parameter, then the URI path resolver bean from `ApiVersionConfig` (`/v1/**` → 1, `/v2/**` and `/api/v2/**` → 2). The first one that finds a value wins; without one, the default version `2` applies. Versions are semantic (`1` = `1.0.0`), and a leading `v` is ignored.

Resolvers are global, so any strategy can select the version of any versioned endpoint (e.g. `GET /api/reports` with `API-Version: 1`).

## Versioning Strategies

### 1. URI Path Versioning

Version is embedded in the URL. Spring Data REST uses `basePath=/v2`.

```bash
# V2 (current) — Spring Data REST
curl http://localhost:8080/v2/employees

# V1 (deprecated) — includes Deprecation/Sunset/Link headers
curl -i http://localhost:8080/v1/employees

# V2 with rich DTOs + response envelope
curl http://localhost:8080/api/v2/employees
```

### 2. Header Versioning

Single URI; version is specified via the `API-Version` header (`@GetMapping(path = "/location", version = "2")`).

```bash
# V2 (explicit)
curl -H "API-Version: 2" http://localhost:8080/location

# Omitting the header defaults to v2
curl http://localhost:8080/location

# Unsupported version → 400 with structured error
curl -H "API-Version: 3" http://localhost:8080/location
```

### 3. Media Type Versioning (Content Negotiation)

Version is a parameter of the vendor media type in the `Accept` header.

```bash
# V1 product
curl -H "Accept: application/vnd.company+json;v=1" http://localhost:8080/api/products

# V2 product (adds description, sku)
curl -H "Accept: application/vnd.company+json;v=2" http://localhost:8080/api/products

# Unsupported version → 400 with structured error
curl -H "Accept: application/vnd.company+json;v=3" http://localhost:8080/api/products
```

### 4. Query Parameter Versioning

Version passed as a query parameter. Default is `2`.

```bash
# Report V1 (compact: id, title, content)
curl "http://localhost:8080/api/reports?version=1"

# Report V2 (adds author, createdAt, tags) — default
curl http://localhost:8080/api/reports

# Employees via @JsonView: V1 hides title/hireDate/status
curl "http://localhost:8080/api/employees/view?version=1"
curl "http://localhost:8080/api/employees/view?version=2"
```

## Response Envelope

All `/api/**` endpoints wrap their response body in a standard envelope:

```json
{
  "data": { "..." },
  "meta": {
    "apiVersion": "v2",
    "deprecation": null,
    "timestamp": "2026-02-12T00:00:00Z"
  }
}
```

V1 responses include `"deprecation": "2025-12-31"` (the v1 sunset date).

## Version Discovery

```bash
curl http://localhost:8080/api/versions
```

```json
{
  "data": {
    "current": "v2",
    "supported": ["v1", "v2"],
    "deprecated": ["v1"],
    "strategies": [
      "uri-path (/v1/**, /api/v2/**)",
      "header (API-Version)",
      "query-parameter (version)",
      "media-type-parameter (application/vnd.company+json;v=)"
    ],
    "v1": { "status": "deprecated", "sunset": "2025-12-31", "docs": "/v1/docs" },
    "v2": { "status": "stable", "docs": "/v2/docs" }
  },
  "meta": { "apiVersion": "v2", ... }
}
```

## ETag Caching

`ShallowEtagHeaderFilter` generates ETags for all responses. V1 and V2 produce different ETags for the same resource.

```bash
# First request — note the ETag
curl -i http://localhost:8080/api/v2/employees/1

# Conditional request — 304 Not Modified if unchanged
curl -i -H 'If-None-Match: "<etag-value>"' http://localhost:8080/api/v2/employees/1
```

## Metrics

Per-version request counts tracked by Micrometer and exposed via a custom actuator endpoint. The version label comes from the version Spring MVC resolved for the request.

```bash
curl http://localhost:8080/actuator/api-versions
```

```json
{
  "v1": { "requests": 3, "lastUsed": "2026-02-12T10:00:00Z" },
  "v2": { "requests": 12, "lastUsed": "2026-02-12T10:05:00Z" }
}
```

## Deprecation Headers (V1)

A `StandardApiVersionDeprecationHandler` bean adds these headers to every request resolved to version 1 (`/v1/**`, `?version=1`, `API-Version: 1`, `;v=1`):

```
Deprecation: @1735689600
Link: </api/versions>; rel="deprecation"; type="text/html"
Sunset: Wed, 31 Dec 2025 23:59:59 GMT
```

`Deprecation` is an RFC 9745 timestamp (`@` + epoch seconds, here 2025-01-01T00:00:00Z); `Sunset` is an RFC 8594 HTTP date.

## V1 Feature Flag

V1 can be disabled at startup via a Spring profile or property.

```bash
# Disable V1 entirely
./gradlew :versioning:bootRun --args='--spring.profiles.active=v1-disabled'
```

With `api.v1.enabled=false`, `EmployeeControllerV1` and the deprecation handler are not created, `/api/versions` shows only v2, and the profile also sets `spring.mvc.apiversion.supported=2`, so version 1 is rejected with 400 on versioned endpoints.

Property file equivalents:
- `application-v1-enabled.properties` → `api.v1.enabled=true`
- `application-v1-disabled.properties` → `api.v1.enabled=false`, `spring.mvc.apiversion.supported=2`

## Error Response

Unparsable or unsupported versions (`InvalidApiVersionException`) return a structured error:

```json
{
  "error": "Unsupported API version",
  "requestedVersion": "3.0.0",
  "supportedVersions": ["1", "2"],
  "currentVersion": "2",
  "documentation": "/api/versions"
}
```

| Status | Trigger |
|--------|---------|
| 400 | Unparsable version, or version not in `spring.mvc.apiversion.supported` (any strategy) |
| 404 | Supported version, but the endpoint has no handler for it (e.g. `API-Version: 1` on `/location`) |

## OpenAPI / Swagger UI

SpringDoc groups endpoints by version. Navigate to `http://localhost:8080/swagger-ui.html` and switch between the `v1` and `v2` groups.

## All Endpoints

| Method | Path                   | Description                                      |
|--------|------------------------|--------------------------------------------------|
| GET    | /v2/employees          | List employees (Spring Data REST, paginated HAL) |
| POST   | /v2/employees          | Create employee (Spring Data REST)               |
| GET    | /v1/employees          | List employees V1 (deprecated)                   |
| GET    | /v1/employees/{id}     | Get employee V1 (deprecated)                     |
| GET    | /api/v2/employees      | List employees V2 with envelope                  |
| GET    | /api/v2/employees/{id} | Get employee V2 with ETag                        |
| GET    | /v2/schedule           | Schedule (BasePathAware)                         |
| GET    | /location              | Header-versioned location (v2 only)              |
| GET    | /v2/location           | Path-versioned location alias                    |
| GET    | /api/products          | Media-type-versioned product                     |
| GET    | /api/reports           | Query-param-versioned report                     |
| GET    | /api/employees/view    | @JsonView field evolution                        |
| GET    | /api/versions          | Version discovery                                |
| GET    | /actuator/health       | Health check                                     |
| GET    | /actuator/api-versions | Per-version request metrics                      |

## Managing Versions

- Group breaking changes into a major version bump (v1 → v2)
- Set a sunset date when releasing a new major version (announce 6+ months ahead)
- Keep old versions independently deployable behind a load balancer
  - Route by URI prefix (`/v1/`, `/v2/`) or header value
- Cherry-pick bug fixes from the new version to the old version
- Disable old versions cleanly with a feature flag once traffic drops to zero

## Testing

```bash
./gradlew :versioning:test
```

Integration test suites (`VersioningIntegrationTest`):

| Nested Class               | What it covers                                                        |
|----------------------------|-----------------------------------------------------------------------|
| `UriPathVersioning`        | V2 routing, V1 Deprecation/Sunset/Link headers, none on V2            |
| `HeaderVersioning`         | `API-Version` routing, default, 400 on unsupported/unparsable, 404 on no handler |
| `MediaTypeVersioning`      | `;v=1` / `;v=2`, deprecation header on v1, 400 on unsupported         |
| `QueryParameterVersioning` | Report versions, default, same endpoint via header, 400 on unknown    |
| `JsonViewFieldEvolution`   | V1 hides V2 fields, V2 exposes all                                    |
| `ETagBehavior`             | 304 on match, different ETags across versions                         |
| `DiscoveryAndEnvelope`     | /api/versions payload, envelope shape                                 |
| `VersionContract`          | Parameterized field contracts, actuator metrics per version           |

`V1DisabledProfileIntegrationTest` (profile `v1-disabled`): 404 on `/v1/employees`, 400 for `?version=1`, single-entry discovery.
