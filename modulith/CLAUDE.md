# Modulith Project

Spring Modulith demo showcasing modular monolith architecture with event-driven communication.

## Quick Start

```bash
# Start MySQL
docker run -d --name demodb -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=demodb -p 3306:3306 mysql:8.4

# Run application
./gradlew modulith:bootRun
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Actuator: http://localhost:8080/actuator/modulith

## Architecture

```
modulith/
├── customer/          # Customer registration (no dependencies)
├── order/             # Order management (depends on customer)
├── inventory/         # Stock management (depends on order for events)
└── shared/            # Cross-cutting concerns (API response, exceptions)
```

Each module follows hexagonal structure: `domain/`, `application/`, `infrastructure/web/`

### Module Dependencies

| Module    | Allowed Dependencies  | Publishes                                                                        | Listens To                                           |
|-----------|-----------------------|----------------------------------------------------------------------------------|------------------------------------------------------|
| customer  | shared::api           | CustomerRegisteredEvent                                                          | -                                                    |
| order     | customer, shared::api | OrderCreatedEvent, OrderConfirmedEvent, OrderCancelledEvent, OrderCompletedEvent | CustomerRegisteredEvent, StockReservationFailedEvent |
| inventory | order, shared::api    | StockReservedEvent, StockReservationFailedEvent                                  | OrderCreatedEvent, OrderCancelledEvent               |

## Key Patterns

### Module Boundaries

Each module declares dependencies in `package-info.java`:

```java
@ApplicationModule(
    displayName = "Order Module",
    allowedDependencies = {"customer", "shared::api"}
)
package com.example.modulith.order;
```

### Facade Pattern

Modules expose public APIs via Facade classes:

```java
@Component
public class CustomerFacade {
    public boolean customerExists(Long customerId) { ... }
}
```

Facades: `CustomerFacade`, `OrderFacade`, `InventoryFacade`

### Event-Driven Communication

Cross-module communication uses Spring events:

```java
// Publishing (OrderService)
eventPublisher.publishEvent(new OrderCreatedEvent(orderId, customerId, amount, sku, quantity, createdAt));

// Listening (InventoryEventListener)
@ApplicationModuleListener
public void on(OrderCreatedEvent event) { ... }
```

### Order State Machine

```
PENDING → CONFIRMED → COMPLETED
    ↘         ↘
     CANCELLED  CANCELLED
```

## Exception Handling

`GlobalExceptionHandler` (shared module) maps exceptions by naming convention:
- `*NotFoundException` → 404
- `OrderStateTransitionException` → 422
- `DuplicateEmailException` / `DuplicateSkuException` / `InsufficientStockException` → 409
- `IllegalArgumentException` → 400
- `IllegalStateException` → 409
- `MethodArgumentNotValidException` → 400 with field-level validation errors

## API Endpoints

| Method | Endpoint                              | Description                                   |
|--------|---------------------------------------|-----------------------------------------------|
| POST   | /api/customers                        | Register customer                             |
| GET    | /api/customers/{id}                   | Get customer                                  |
| GET    | /api/customers/{id}/exists            | Check customer exists                         |
| GET    | /api/customers                        | List customers (paginated)                    |
| POST   | /api/orders                           | Create order                                  |
| GET    | /api/orders/{orderId}                 | Get order                                     |
| GET    | /api/orders                           | List orders (paginated, filter by customerId) |
| PATCH  | /api/orders/{orderId}/confirm         | Confirm order (PENDING → CONFIRMED)           |
| PATCH  | /api/orders/{orderId}/cancel          | Cancel order                                  |
| PATCH  | /api/orders/{orderId}/complete        | Complete order (CONFIRMED → COMPLETED)        |
| POST   | /api/inventory/products               | Create product                                |
| GET    | /api/inventory/products               | List products (paginated)                     |
| GET    | /api/inventory/products/{sku}         | Get product by SKU                            |
| PUT    | /api/inventory/products/{sku}         | Update product (name, price)                  |
| POST   | /api/inventory/products/{sku}/restock | Restock product                               |
| GET    | /api/inventory/check/{sku}            | Check stock availability                      |
| POST   | /api/inventory/reserve                | Reserve stock                                 |

## Database

- MySQL 8.4 with Flyway migrations
- Tables: `customers`, `orders`, `products`, `event_publication`
- Event publication table tracks async event completion (JDBC persistence enabled)
- Flyway migrations: V1 (schema), V2 (add sku/quantity to orders), V3 (seed products)
- `shared/BaseEntity`: `@MappedSuperclass` with id, createdAt, updatedAt audit fields

## Tech Stack

- Java 21+ with Virtual Threads
- Spring Boot 3.x + Spring Modulith
- JPA/Hibernate (batch inserts/updates enabled), Flyway, Lombok
- springdoc-openapi for Swagger
- spring-modulith-observability for distributed tracing
- spring-modulith-docs for PlantUML generation

## Testing

```bash
./gradlew modulith:test
```

- `ModulithStructureTests`: verifies module boundaries + generates PlantUML docs (uses `spring-modulith-starter-test` + `spring-modulith-docs`)
- `CustomerModuleTest`: `@ApplicationModuleTest` — bootstraps customer module, asserts `CustomerFacade` not null
- `OrderModuleTest`: `@ApplicationModuleTest` — `@MockitoBean CustomerFacade`, asserts `OrderFacade` not null
- `InventoryModuleTest`: `@ApplicationModuleTest` — bootstraps inventory module, asserts `InventoryFacade` not null
- Tests use H2 in-memory DB with `src/test/resources/schema.sql` (not Flyway)

## Configuration

Key settings in `application.yml`:
- Virtual threads enabled
- JPA open-in-view disabled; Hibernate batch inserts/updates enabled (batch_size=20)
- Modulith event JDBC persistence enabled + schema auto-initialization
- Events republished on restart (resilience)
- Actuator exposes health, metrics, modulith endpoints; liveness/readiness probes enabled
- Graceful shutdown with 30s timeout

## Missing Demos

- `@Externalized` events for externalizing to message broker (Kafka/RabbitMQ)
- Scenario-based integration tests using Spring Modulith `Scenario` API
- Module-level transaction rollback testing
- `ApplicationModules.of().getModuleByName()` for programmatic boundary inspection
- Event sourcing / event store querying via `PublishedEventRepository`
- Custom `@ApplicationModule` named interfaces (restrict what's public within a module)
