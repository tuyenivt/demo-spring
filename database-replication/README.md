# Database Replication Demo

A Spring Boot application demonstrating **MySQL master-slave replication** with read-write separation using GTID (Global Transaction Identifier) mode.

## Overview

This project showcases how to implement database replication in a Spring application:

- **Write operations** route to the master database
- **Read operations** route to the replica database
- **Read-after-write consistency** ensures fresh data after inserts

## Architecture

```
                                  ┌─────────────────┐
                                  │  Master (Write) │
                           ┌─────►│   MySQL :3306   │
                           │      └────────┬────────┘
┌────────┐    ┌─────────┐  │               │ Replication
│ Client │───►│ Service │──┤               ▼
└────────┘    └─────────┘  │      ┌─────────────────┐
                           │      │ Replica (Read)  │
                           └─────►│   MySQL :3307   │
                                  └─────────────────┘
```

## Tech Stack

- Java 25+
- Spring Boot 4.1
- Spring Data JPA
- MySQL 8.4 (GTID replication)
- Liquibase (schema migrations)
- Docker Compose
- SpringDoc OpenAPI (Swagger UI)
- Testcontainers (integration tests)

## Quick Start

### 1. Start the Databases

```bash
cd docker
docker compose up -d
```

This starts:
- **mysql-writer** (master) on port 3306
- **mysql-reader** (replica) on port 3307

### 2. Run the Application

```bash
./gradlew :database-replication:bootRun
```

### 3. Test the API

```bash
# Create a user (writes to master)
curl -X POST http://localhost:8080/users \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe", "email": "john@example.com"}'

# List all users with pagination (reads from replica)
curl "http://localhost:8080/users?page=0&size=10"

# Get user by ID (reads from replica)
curl http://localhost:8080/users/1

# Find users by name (reads from replica)
curl "http://localhost:8080/users/name/John%20Doe"

# Delete a user (writes to master)
curl -X DELETE http://localhost:8080/users/1
```

### 4. View API Documentation

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) in your browser to see the interactive API documentation.

### 5. Check Health Status

Check the health of both datasources:
```bash
curl http://localhost:8080/actuator/health
```

This will show the status of both writer and reader datasources independently.

### 6. Clean Up

```bash
cd docker
docker compose down --volumes --remove-orphans
```

## Key Concepts

### Read-Write Separation with @UseWriter

The application uses `AbstractRoutingDataSource` with annotation-based routing:

- **@UseWriter annotation**: Mark service methods that require write operations
- **UseWriterAspect**: Intercepts `@UseWriter` methods and routes to the master datasource
- **Default routing**: All operations route to the replica (reader) by default
- **DataSourceContextHolder**: Uses `ScopedValue` for thread-safe, virtual-thread-compatible context

```java
@Service
@Transactional(readOnly = true)
public class UserService {

    @UseWriter
    @Transactional
    public User createUser(CreateUserRequest request) {
        return userRepository.save(user);
    }

    // No annotation - routes to replica
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }
}
```

### GTID Replication

GTID (Global Transaction Identifier) provides:
- Automatic failover positioning
- Consistent replication across all servers
- No need to track binary log positions manually

### Security Best Practices

The application demonstrates proper security practices:
- **Separate Users**: Application uses `app` user with DML-only privileges (SELECT, INSERT, UPDATE, DELETE)
- **Liquibase Access**: Schema migrations use `root` with DDL privileges via `spring.liquibase.url/user/password`, isolated from application runtime
- **Least Privilege**: Each component has only the minimum required permissions

## API Reference

| Method | Endpoint             | Description              | Database |
|--------|----------------------|--------------------------|----------|
| POST   | `/users`             | Create a new user        | Master   |
| GET    | `/users`             | List users (paginated)   | Replica  |
| GET    | `/users/{id}`        | Get user by ID           | Replica  |
| GET    | `/users/name/{name}` | Find users by name       | Replica  |
| PUT    | `/users/{id}`        | Update a user            | Master   |
| DELETE | `/users/{id}`        | Delete a user            | Master   |

### Request/Response DTOs

**CreateUserRequest**:
```json
{
  "name": "John Doe",         // required, max 255 chars
  "email": "john@example.com" // required, valid email, max 255 chars
}
```

**UpdateUserRequest**:
```json
{
  "name": "John Doe Updated", // required, max 255 chars
  "email": "john@example.com" // required, valid email, max 255 chars
}
```

**UserResponse**:
```json
{
  "id": 1,
  "name": "John Doe",
  "email": "john@example.com"
}
```

### Pagination

Use query parameters for paginated list endpoint:
- `page` - Page number (0-indexed, default: 0)
- `size` - Page size (default: 20)

Example: `GET /users?page=0&size=10`

## Troubleshooting

### Check Replication Status

```bash
docker exec -it mysql-reader mysql -uroot -proot -e "SHOW REPLICA STATUS\G"
```

### View Logs

```bash
docker logs mysql-writer
docker logs mysql-reader
```

### Common Issues

1. **Replica not syncing**: The healthcheck now ensures master is fully started before replica
2. **Connection refused**: Wait for MySQL containers to be healthy (healthcheck runs every 5s)
3. **Stale reads**: Expected due to replication lag; reads go to replica by default
4. **Permission errors**: Application uses `app` user; only Liquibase uses `root`
