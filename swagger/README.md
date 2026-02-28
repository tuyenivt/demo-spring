# Swagger Demo

Spring Boot demo that generates a Petstore client and now exposes its own REST API facade with Swagger UI.

## What this module does

- Generates `PetApi`, `StoreApi`, and `UserApi` clients from an OpenAPI 3.x spec (`openapi/petstore.yaml`) using `org.openapi.generator`.
- Calls the external Petstore API via Feign + OkHttp.
- Exposes local REST endpoints under `/api/pets` and `/api/orders` with OpenAPI 3 documentation.
- Uses Feign retry (`3` attempts), FULL request/response logging, correlation-id propagation (`X-Correlation-ID`), and custom Feign error decoding.

## Run

From repository root:

```bash
./gradlew :swagger:bootRun
```

## API docs

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## Exposed endpoints

- `GET /api/pets/{petId}` - get a pet by id
- `GET /api/pets?status=available` - find pets by status (`available|pending|sold`) (deprecated)
- `POST /api/pets` - create a pet
- `GET /api/orders/{orderId}` - get order by id
- `POST /api/orders` - create order
- `DELETE /api/orders/{orderId}` - delete order (deprecated)

Internal hidden endpoints exist for demo purposes and are excluded from Swagger via `@Hidden`.

Example create payload:

```json
{
  "name": "Buddy",
  "status": "available"
}
```

## Configuration

Environment variable overrides:

- `PET_STORE_BASE_URL` (default `https://petstore.swagger.io/v2`)
- `PET_STORE_USERNAME` (default `user`)
- `PET_STORE_PASSWORD` (default `pass`)

Feign logging:

- `logging.level.com.example.openapi.petstore.api=DEBUG`

## Error handling

- Validation failures return `400`.
- Upstream `404` returns `404`.
- Upstream `4xx` maps to `400` via custom `ErrorDecoder` domain exceptions.
- Upstream `5xx` maps to `502`.

## OpenAPI security docs

The generated OpenAPI document includes a global `bearerAuth` HTTP security scheme for demonstration. Swagger UI shows the Authorize button and lock indicators.

## Tests

```bash
./gradlew :swagger:test
```
