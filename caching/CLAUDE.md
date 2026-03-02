# Caching Module

## Overview

Spring Boot caching demo using Redis as the cache backend. Demonstrates Spring Cache abstraction with service-layer caching, direct Redis operations, cache-aside pattern with graceful database fallback, DTO pattern, input validation, comprehensive testing, and OpenAPI documentation.

## Tech Stack

- Java 21+
- Spring Boot 3.5.10
- Spring Web
- Spring Data JPA (MySQL)
- Spring Data Redis
- Spring Validation
- Spring Actuator
- SpringDoc OpenAPI 2.8.15
- Liquibase
- Lombok

## Project Structure

```
caching/
├── src/main/java/com/example/caching/
│   ├── MainApplication.java          # Entry point
│   ├── config/
│   │   ├── CacheConfig.java          # TTL, serialization, statistics
│   │   ├── CacheWarmer.java          # Resilient cache warming on startup
│   │   └── RedisConfig.java          # Redis pub/sub configuration
│   ├── controller/
│   │   ├── ProductController.java    # Product CRUD endpoints with validation
│   │   ├── CacheController.java      # Manual cache operations
│   │   ├── MessageController.java    # Pub/sub demo endpoint
│   │   └── UserActivityController.java # Activity tracking endpoint
│   ├── dto/
│   │   ├── ProductRequest.java       # Product creation/update DTO
│   │   └── ProductResponse.java      # Product response DTO
│   ├── entity/
│   │   └── Product.java              # JPA entity (Serializable)
│   ├── enums/
│   │   └── Category.java             # Product category enum
│   ├── listener/
│   │   └── RedisSubscriber.java      # Pub/sub message listener (NPE-safe)
│   ├── mapper/
│   │   └── ProductMapper.java        # Entity-DTO mapper
│   ├── repository/
│   │   └── ProductRepository.java    # Database access
│   ├── sender/
│   │   └── RedisPublisher.java       # Pub/sub message publisher
│   └── service/
│       ├── ProductService.java       # Service-layer caching with fallback
│       └── UserService.java          # Direct Redis list operations with TTL
└── src/main/resources/
    └── application.yml               # Configuration
└── src/test/java/com/example/caching/
    ├── controller/
    │   ├── ProductControllerTest.java      # @WebMvcTest for ProductController
    │   ├── CacheControllerTest.java        # @WebMvcTest for CacheController
    │   └── UserActivityControllerTest.java # @WebMvcTest for UserActivityController
    └── service/
        ├── ProductServiceTest.java         # Unit tests for ProductService
        ├── UserServiceTest.java            # Unit tests for UserService
        └── ProductCachingIntegrationTest.java # @SpringBootTest + Testcontainers end-to-end
```

## Key Components

### Cache Strategy

| Component        | Pattern                   | Description                                                                        |
|------------------|---------------------------|------------------------------------------------------------------------------------|
| `ProductService` | Service-layer caching     | Declarative caching with `@Cacheable`, `@CachePut`, `@CacheEvict` at service level |
| `CacheConfig`    | Framework error handler   | `CacheErrorHandler` logs Redis failures and lets the method execute against DB     |
| `UserService`    | Direct Redis with TTL     | Low-level list operations for activity tracking with 7-day expiration              |

**Key Design Points:**
- Cache annotations at service layer (not repository) for better separation of concerns
- Graceful fallback is handled by `CacheConfig.errorHandler()` (`CachingConfigurer`), not try/catch in service
- Consistent cache keys (`#result.productId` for both read and write operations)
- Comprehensive cache eviction on updates (both `product` and `product_list` caches)
- `delete()` uses `beforeInvocation = true` to evict before DB delete
- DTO pattern decouples API contract from database schema
- Input validation with Jakarta Bean Validation

### Cache Configuration

| Cache          | TTL        | Purpose               |
|----------------|------------|-----------------------|
| `product`      | 1 hour     | Single product lookup |
| `product_list` | 15 minutes | Product name search   |
| default        | 30 minutes | Other caches          |

### REST Endpoints

| Endpoint                            | Method | Description                                  |
|-------------------------------------|--------|----------------------------------------------|
| `/api/products/{id}`                | GET    | Get product by ID (cached)                   |
| `/api/products`                     | GET    | List all products (paginated)                |
| `/api/products/search?name=`        | GET    | Search products by name (paginated)          |
| `/api/products`                     | POST   | Create product                               |
| `/api/products/{id}`                | PUT    | Update product (404 if not found)            |
| `/api/products/{id}`                | DELETE | Delete product, evict caches (204/404)       |
| `/api/cache/names`                  | GET    | List all cache names                         |
| `/api/cache/stats`                  | GET    | Cache statistics (per RedisCache)            |
| `/api/cache/{name}`                 | DELETE | Clear entire cache                           |
| `/api/cache/{name}/{key}`           | DELETE | Evict specific key                           |
| `/api/messages/publish`             | POST   | Publish message to channel                   |
| `/api/users/{id}/activities`        | POST   | Add user activity                            |
| `/api/users/{id}/activities`        | GET    | Get all activities (non-destructive)         |
| `/api/users/{id}/activities/oldest` | GET    | Pop oldest activity (FIFO, destructive)      |

### Actuator Endpoints

| Endpoint            | Description         |
|---------------------|---------------------|
| `/actuator/health`  | Health check        |
| `/actuator/caches`  | Cache statistics    |
| `/actuator/metrics` | Application metrics |

### Annotations Used

```java
// Cache on read (skip if null result)
@Cacheable(cacheNames = "product", unless = "#result == null", key = "#id")

// Update cache on write (conditional) + evict list cache
@Caching(
    put = @CachePut(cacheNames = "product", condition = "#result.inStock gt 0", key = "#result.productId"),
    evict = {
        @CacheEvict(cacheNames = "product_list", allEntries = true),
        @CacheEvict(cacheNames = "product", condition = "#result.inStock eq 0", key = "#result.productId")
    }
)

// Transactional read-only operations
@Transactional(readOnly = true)
@Cacheable(cacheNames = "product", unless = "#result == null", key = "#id")
public Optional<Product> findById(Long id) { ... }
```

## Configuration

### application.yml

```yaml
spring:
  cache:
    type: redis
  data:
    redis:
      host: localhost
      port: 6379
      lettuce:
        pool:
          enabled: true
          max-active: 8
          max-idle: 8
          min-idle: 2

management:
  endpoints:
    web:
      exposure:
        include: health,caches,metrics
```

### Dependencies (build.gradle)

```gradle
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'org.springframework.boot:spring-boot-starter-validation'
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.15'
implementation 'org.apache.commons:commons-pool2'
implementation 'org.liquibase:liquibase-core'
runtimeOnly 'com.mysql:mysql-connector-j'
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:mysql'
testImplementation 'org.testcontainers:testcontainers'
```

## Redis Features Demonstrated

1. **Service-Layer Caching** - Method-level caching at service layer for better design
2. **TTL Configuration** - Per-cache expiration times (1h for product, 15m for product_list)
3. **JSON Serialization** - Human-readable cache values
4. **Connection Pooling** - Lettuce pool with commons-pool2
5. **Cache Statistics** - Via actuator endpoint
6. **Resilient Cache Warming** - Pre-populate cache on startup with error handling
7. **Manual Cache Operations** - Programmatic cache access via API
8. **Pub/Sub Messaging** - `RedisPublisher` and `RedisSubscriber` on channel `my-channel`
9. **List Operations with TTL** - Activity queue in `UserService` (FIFO pattern) with 7-day expiration
10. **Graceful Fallback** - Automatic database fallback when Redis is unavailable
11. **Input Validation** - Jakarta Bean Validation on DTOs
12. **OpenAPI Documentation** - Swagger UI at `/swagger-ui.html`

## Data Flow

```
Request → ProductController (validates DTOs)
            ↓
        ProductMapper (entity ↔ DTO conversion)
            ↓
        ProductService (with @Cacheable, @Transactional)
            ├─ Cache HIT → Return cached data
            ├─ Cache MISS → Query DB via ProductRepository
            │               ↓
            │           Cache result → Return
            └─ Cache ERROR (DataAccessException)
                ↓
            Catch exception → Fallback to ProductRepository
                ↓
            Return from database
            ↓
        ProductMapper (entity → response DTO)
            ↓
        Response to client
```

**Cache Eviction Flow on Save:**
1. ProductController receives validated ProductRequest
2. ProductMapper converts to Product entity
3. ProductService.save() is called:
   - Saves product to database
   - Updates `product` cache if inStock > 0
   - Evicts all entries from `product_list` cache
   - Evicts specific `product` cache entry if inStock == 0
4. Returns updated product to controller
5. Controller returns ProductResponse DTO

**Update Flow (Uses Managed Entity):**
1. ProductController receives validated ProductRequest for existing product ID
2. ProductService.findById() retrieves the managed entity (or 404 if not found)
3. ProductMapper.updateEntity() updates the managed entity in-place
4. ProductService.save() persists the changes (Hibernate detects dirty fields)
5. Cache is updated/evicted based on business rules
6. Controller returns ProductResponse DTO

This approach is more efficient than creating a new entity because:
- Leverages Hibernate's managed entity state and dirty checking
- Only updates changed fields in the database
- Works properly within the transaction boundary
- Avoids potential issues with detached entities

## Requirements

- MySQL database `demodb` (schema managed by Liquibase)
- Redis server on `localhost:6379`

## Running

```bash
./gradlew :caching:bootRun
```
