# Spring Boot Redis Caching Demo

A comprehensive demonstration of Redis caching patterns in Spring Boot, showcasing service-layer caching, cache-aside strategy with graceful fallback, DTO pattern, input validation, comprehensive testing, and OpenAPI documentation.

## Features

- **Service-Layer Caching** - Cache annotations at service layer for better separation of concerns
- **Cache Error Handling** - Framework-level cache error handler logs Redis failures and continues with DB path
- **TTL Configuration** - Per-cache expiration times with JSON serialization
- **Connection Pooling** - Lettuce pool for efficient Redis connections
- **Cache Statistics** - Monitor cache performance via Actuator
- **Resilient Cache Warming** - Pre-populate cache on startup with error handling
- **Manual Cache Operations** - REST API for cache management
- **Product CRUD Completeness** - Includes delete endpoint with cache eviction
- **Pagination Support** - Paginated list/search APIs for controlled payload sizes
- **Pub/Sub Messaging** - Redis publish/subscribe demonstration
- **Activity Tracking with TTL** - Redis list operations with automatic 7-day expiration and list trimming
- **DTO Pattern** - Clean API contract separated from database schema
- **Input Validation** - Jakarta Bean Validation on all inputs
- **OpenAPI Documentation** - Interactive API docs via Swagger UI
- **Comprehensive Testing** - Unit tests and controller tests with high coverage

## Quick Start

### 1. Start Infrastructure

```bash
# Start Redis
docker run -d --name redis -p 6379:6379 redis:8.4-alpine

# Start MySQL
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=demodb mysql:8.4
```

### 2. Run Application

```bash
./gradlew :caching:bootRun
```

## API Endpoints

### OpenAPI Documentation

Access interactive API documentation:
```
http://localhost:8080/swagger-ui.html
```

### Product Operations

```bash
# Create a product (validated input)
curl -X POST http://localhost:8080/api/products -H "Content-Type: application/json" -d '{
  "productName": "Widget",
  "category": "PRODUCT",
  "inStock": 100,
  "price": 29.99,
  "dateOfManufacture": "2026-01-02T09:59:01",
  "vendor": "ABC"
}'

# Get product by ID (cache miss, then cache hit)
curl http://localhost:8080/api/products/1
curl http://localhost:8080/api/products/1  # faster - from cache

# List products (paginated)
curl "http://localhost:8080/api/products?page=0&size=20&sort=productId,asc"

# Search products by name (paginated)
curl "http://localhost:8080/api/products/search?name=Widget&page=0&size=20&sort=updatedAt,desc"

# Update product (evicts cache and returns 404 if not found)
curl -X PUT http://localhost:8080/api/products/1 -H "Content-Type: application/json" -d '{
  "productName": "Widget Pro",
  "category": "PRODUCT",
  "inStock": 150,
  "price": 39.99,
  "vendor": "ABC"
}'

# Try to update non-existent product (returns 404)
curl -X PUT http://localhost:8080/api/products/999 -H "Content-Type: application/json" -d '{...}'

# Delete product (evicts caches, returns 204)
curl -X DELETE http://localhost:8080/api/products/1
```

### Cache Management

```bash
# List all cache names
curl http://localhost:8080/api/cache/names

# Get cache stats
curl http://localhost:8080/api/cache/stats

# Clear a specific cache
curl -X DELETE http://localhost:8080/api/cache/product

# Evict a specific key
curl -X DELETE http://localhost:8080/api/cache/product/1
```

### Pub/Sub Messaging

```bash
# Publish a message (default channel: my-channel)
curl -X POST "http://localhost:8080/api/messages/publish" -H "Content-Type: text/plain" -d "Hello Redis!"

# Publish to custom channel
curl -X POST "http://localhost:8080/api/messages/publish?channel=notifications" -H "Content-Type: text/plain" -d "New notification"
```

### User Activity Tracking

```bash
# Add user activity (auto-expires after 7 days)
curl -X POST http://localhost:8080/api/users/user123/activities -H "Content-Type: text/plain" -d "Logged in"

# Add multiple activities
curl -X POST http://localhost:8080/api/users/user123/activities -H "Content-Type: text/plain" -d "Viewed dashboard"
curl -X POST http://localhost:8080/api/users/user123/activities -H "Content-Type: text/plain" -d "Updated profile"

# Read all activities (non-destructive)
curl http://localhost:8080/api/users/user123/activities

# Get oldest activity (FIFO)
curl http://localhost:8080/api/users/user123/activities/oldest
# Returns: "Logged in"

# Try with invalid userId (returns 400)
curl -X POST http://localhost:8080/api/users//activities -H "Content-Type: text/plain" -d "Test"
```

### Actuator Endpoints

```bash
# Health check
curl http://localhost:8080/actuator/health

# Cache statistics
curl http://localhost:8080/actuator/caches

# Application metrics
curl http://localhost:8080/actuator/metrics
```

## Cache Configuration

| Cache          | TTL        | Purpose               |
|----------------|------------|-----------------------|
| `product`      | 1 hour     | Single product lookup |
| `product_list` | 15 minutes | Product name search   |
| default        | 30 minutes | Other caches          |

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Controller    │────▶│     Service     │────▶│   Repository    │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               │                        │
                               │                        │
                        ┌──────▼──────┐          ┌──────▼──────┐
                        │    Redis    │          │    MySQL    │
                        │   (Cache)   │          │    (DB)     │
                        └─────────────┘          └─────────────┘
```

### Cache-Aside Flow

1. Request comes in for a product
2. ProductController validates input and maps to/from DTOs
3. ProductService tries to read from cache (via `@Cacheable`)
4. On cache hit: return cached data immediately
5. On cache miss: fetch from DB via `ProductRepository`, cache it, return data
6. On cache error: `CacheErrorHandler` logs and ignores cache failure so method executes against DB
7. On save: update/evict caches appropriately based on business rules

### Testing

Run all tests:
```bash
./gradlew :caching:test
```

Test coverage includes:
- **ProductServiceTest** - Unit tests for caching logic, pagination methods, and delete behavior
- **UserServiceTest** - Unit tests for Redis list operations and validation
- **ProductControllerTest** - @WebMvcTest for REST endpoints with validation
- **CacheControllerTest** - @WebMvcTest for cache management endpoints
- **UserActivityControllerTest** - @WebMvcTest for non-destructive activity retrieval
- **ProductCachingIntegrationTest** - @SpringBootTest + Testcontainers for end-to-end cache verification

Most tests run without Docker/Redis/MySQL using Mockito for fast, isolated testing. The integration test uses Testcontainers.

## Connection Pooling

Lettuce connection pool is configured for optimal Redis performance:

```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          enabled: true
          max-active: 8
          max-idle: 8
          min-idle: 2
          max-wait: -1ms
```

## Monitoring

Access cache statistics via Spring Actuator:

```bash
curl http://localhost:8080/actuator/caches
```

Response includes hit/miss counts, eviction statistics, and cache size information.
