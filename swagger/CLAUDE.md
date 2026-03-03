# Swagger Subproject

## Overview

This is a **Feign-based API client** project that demonstrates consuming external REST APIs using OpenAPI code generation. It generates Java client code from an OpenAPI 3.0.3 specification, configures Feign clients with Spring Boot, and exposes a local REST API facade with Swagger UI documentation.

## Technology Stack

- **Java**: 25
- **Spring Boot**: 3.5.10
- **Spring Cloud**: 2025.0.1
- **OpenFeign**: Spring Cloud Starter + feign-okhttp + feign-jackson
- **OpenAPI Generator**: `org.openapi.generator` plugin v7.19.0
- **OpenAPI Docs**: springdoc-openapi-starter-webmvc-ui 2.8.x
- **HTTP Client**: OkHttp (via Feign)
- **Build Tool**: Gradle

## Project Structure

```
swagger/
├── build.gradle                    # Build config with OpenAPI code generation
├── openapi/
│   ├── petstore.yaml               # OpenAPI 3.0.3 specification
│   └── config-petstore.json        # Code generation settings (feign library)
└── src/main/java/com/example/openapi/
    ├── MainApplication.java        # Spring Boot entry point
    ├── config/
    │   ├── FeignConfig.java        # OkHttpClient bean for Feign
    │   ├── OpenApiConfig.java      # Springdoc OpenAPI metadata (title, server)
    │   └── PetStoreConfig.java     # Feign client beans (PetApi, StoreApi, UserApi)
    ├── controller/
    │   ├── PetController.java      # REST facade: /api/pets endpoints
    │   └── StoreController.java    # REST facade: /api/orders endpoints
    ├── dto/
    │   ├── CreatePetRequest.java   # Validated request record
    │   ├── PetResponse.java        # Response record
    │   └── ErrorResponse.java      # Standard error record (timestamp, status, message)
    ├── exception/
    │   ├── GlobalExceptionHandler.java   # Maps domain + Feign exceptions to HTTP responses
    │   ├── PetNotFoundException.java     # 404 — resource not found upstream
    │   ├── UpstreamClientException.java  # 400 — upstream 4xx error
    │   └── UpstreamServiceException.java # 502 — upstream 5xx error
    └── feign/
        ├── CorrelationIdInterceptor.java # Propagates X-Correlation-ID via MDC
        └── PetStoreErrorDecoder.java     # Maps Feign error responses to domain exceptions
```

## Key Components

### Configuration Classes

- `FeignConfig.java`: OkHttpClient bean + `Retryer.Default` (3 attempts, 100ms–1s) + `Logger.Level.FULL` + `CorrelationIdInterceptor` + `PetStoreErrorDecoder`
- `OpenApiConfig.java`: Configures Springdoc `OpenAPI` bean (title, version, local server) + global `bearerAuth` security scheme
- `PetStoreConfig.java`: `@ConfigurationProperties(prefix = "app.pet-store")` — builds PetApi, StoreApi, UserApi Feign clients with Basic Auth

### REST Facades

**PetController** (`/api/pets`):

| Method | Path                | Description                                                                        |
|--------|---------------------|------------------------------------------------------------------------------------|
| GET    | `/api/pets/{petId}` | Get pet by ID (`@Positive` validated)                                              |
| GET    | `/api/pets?status=` | Find pets by status (default: `available`; validated: `available\|pending\|sold`); **deprecated** |
| POST   | `/api/pets`         | Create a pet (`@Valid` body)                                                       |
| GET    | `/api/pets/internal/health` | `@Hidden` internal health check                                        |

**StoreController** (`/api/orders`):

| Method | Path                    | Description                                   |
|--------|-------------------------|-----------------------------------------------|
| GET    | `/api/orders/{orderId}` | Get order by ID (`@Positive` validated)       |
| POST   | `/api/orders`           | Create order                                  |
| DELETE | `/api/orders/{orderId}` | Delete order; **deprecated**                  |
| GET    | `/api/orders/internal/inventory` | `@Hidden` inventory lookup           |

### Generated Code (`build/openapi/src/main/java/`)

- **API Clients**: `com.example.openapi.petstore.api.{PetApi, StoreApi, UserApi}`
- **Models**: `com.example.openapi.petstore.model.{Pet, Category, Tag, Order, User, ApiResponse}`
- **Infrastructure**: `com.example.openapi.petstore.invoker.*`

### Feign Infrastructure

- `CorrelationIdInterceptor`: reads `correlationId` from MDC; generates UUID if absent; injects as `X-Correlation-ID` header
- `PetStoreErrorDecoder`: 404 → `PetNotFoundException`; 4xx → `UpstreamClientException`; 5xx → `UpstreamServiceException`

### Exception Handling

`GlobalExceptionHandler` maps:
- `MethodArgumentNotValidException` → 400
- `ConstraintViolationException` → 400
- `PetNotFoundException` → 404
- `UpstreamClientException` → 400
- `UpstreamServiceException` → 502
- `FeignException.NotFound` → 404
- `FeignException` → 502
- `Exception` → 500

## Build Commands

```bash
# Build and generate OpenAPI client code
./gradlew :swagger:build

# Run the application
./gradlew :swagger:bootRun

# Run tests
./gradlew :swagger:test
```

## Configuration

Key properties in `application.yml` (override via env vars):

```yaml
app:
  pet-store:
    base-url: ${PET_STORE_BASE_URL:https://petstore.swagger.io/v2}
    user-name: ${PET_STORE_USERNAME:user}
    password: ${PET_STORE_PASSWORD:pass}
```

## Code Generation

OpenAPI client is generated at build time via `openApiGenerate` task:
- Input spec: `openapi/petstore.yaml`
- Config: `openapi/config-petstore.json`
- Library: `feign`, Jakarta EE, `java8-localdatetime`, no nullable wrappers
- Output: `build/openapi/src/main/java/` (added to `sourceSets.main.java`)

## API Docs (Springdoc)

When running locally:
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Tests

- `PetControllerTest` (8 tests, `@WebMvcTest`): happy-path GET/POST, invalid status→400, negative ID→400, 404→404, blank name→400, upstream 502→502
- `MainApplicationTests`: context-load only

Missing tests: `StoreControllerTest` (GET/POST/DELETE order happy path + 404 + negative ID), upstream 4xx→400 via `UpstreamClientException`, blank `name` on `CreatePetRequest`

## Notes

- Base package: `com.example.openapi`; spec dir: `openapi/`
- Generated packages: `com.example.openapi.petstore.{api,model,invoker}`
- Authentication to Petstore uses Basic Auth via `BasicAuthRequestInterceptor`
- Global `bearerAuth` security scheme in OpenAPI docs (Authorize button in Swagger UI)
- `@Hidden` endpoints excluded from Swagger: `/api/pets/internal/health`, `/api/orders/internal/inventory`
- Deprecated endpoints: `GET /api/pets` (find by status), `DELETE /api/orders/{orderId}`
