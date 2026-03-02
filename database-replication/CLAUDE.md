# Database Replication Project

## Project Overview

Spring Boot application demonstrating **MySQL master-slave replication** with read-write separation using GTID mode and **AbstractRoutingDataSource** with annotation-based routing.

## Quick Reference

### Key Paths

- **Main Application**: `src/main/java/com/example/database/replication/MainApplication.java`
- **DataSource Config**: `src/main/java/.../config/DataSourceConfig.java` (routing datasource)
- **Repository**: `src/main/java/.../repository/UserRepository.java`
- **Routing**:
  - `src/main/java/.../routing/UseWriter.java` - annotation for write methods
  - `src/main/java/.../routing/DataSourceContextHolder.java` - ScopedValue-based context
  - `src/main/java/.../routing/RoutingDataSource.java` - AbstractRoutingDataSource impl
  - `src/main/java/.../routing/UseWriterAspect.java` - aspect for @UseWriter
- **DTOs**: `src/main/java/.../dto/CreateUserRequest.java`, `UserResponse.java`
- **Aspect**: `src/main/java/.../aspect/DatabaseRoutingAspect.java` (logging)
- **Docker Setup**: `docker/docker-compose.yml`
- **Schema Migrations**: `src/main/resources/db/changelog/db.changelog-master.xml`

### Architecture Pattern

```
[Client] --> [Controller] --> [Service] --> [UserRepository] --> [RoutingDataSource]
                |                 |                                      |
                |             @UseWriter?                        +-------+-------+
                |                 |                              |               |
             [DTOs]          Yes: WRITER                     [Writer DS]   [Reader DS]
                             No:  READER                     (port 3306)   (port 3307)
```

### Routing Mechanism

- **Default**: All operations route to READER datasource
- **@UseWriter**: Methods annotated with `@UseWriter` route to WRITER datasource
- **Context**: Uses `ScopedValue` for thread-safe routing context (compatible with virtual threads)
- **Aspect**: `UseWriterAspect` calls `DataSourceContextHolder.callWithWriter(ScopedValue.CallableOp)` to wrap `joinPoint.proceed()`
- **Logging**: `DatabaseRoutingAspect` (separate aspect in `aspect/` package) logs repository operations at TRACE level via `@Before`

### Important Configurations

- Single unified `UserRepository` (no separate read/write repositories)
- `RoutingDataSource` extends `AbstractRoutingDataSource` for dynamic routing
- Liquibase runs **only on master** with separate root credentials for DDL operations
- Application uses dedicated `app` user with DML-only privileges (SELECT, INSERT, UPDATE, DELETE)
- GTID replication ensures consistency across failovers
- Input validation with `@Valid` and Bean Validation annotations (includes @Size constraints)
- Spring Boot Actuator provides health indicators for both datasources at `/actuator/health`
- Spring Retry enabled with `@EnableRetry`; `findById` uses `@Retryable` (maxAttempts=5, exponential backoff) for `SQLException`/`DataAccessException`
- OpenAPI documentation available at `/swagger-ui.html`
- `GlobalExceptionHandler`: `EntityNotFoundException` → 404, `DataIntegrityViolationException` → 409, `MethodArgumentNotValidException` → 400

## Common Commands

```bash
# Start databases
cd docker && docker compose up -d

# Stop and clean
cd docker && docker compose down --volumes --remove-orphans

# Run application
./gradlew :database-replication:bootRun

# Run tests
./gradlew :database-replication:test
```

## API Endpoints

| Method | Path                 | Description           | DB Target      |
|--------|----------------------|-----------------------|----------------|
| POST   | `/users`             | Create user           | Master (write) |
| GET    | `/users`             | List users (paginated)| Replica (read) |
| GET    | `/users/{id}`        | Get by ID             | Replica (read) |
| GET    | `/users/name/{name}` | Find by name          | Replica (read) |
| PUT    | `/users/{id}`        | Update user           | Master (write) |
| DELETE | `/users/{id}`        | Delete user           | Master (write) |

### Pagination Parameters

- `page` - Page number (default: 0)
- `size` - Page size (default: 20)

Example: `GET /users?page=0&size=10`

## Key Implementation Details

1. **@UseWriter Annotation**: Mark service methods that need writer datasource
2. **ScopedValue Context**: Thread-safe, virtual-thread compatible routing context
3. **AbstractRoutingDataSource**: Dynamic datasource selection at runtime
4. **Unified Repository**: Single `UserRepository` for all operations
5. **GTID Mode**: Uses `SOURCE_AUTO_POSITION=1` for automatic position tracking
6. **DTOs**: Request/Response DTOs decouple API contracts from entities
7. **Input Validation**: Bean Validation with @Size constraints on DTOs (max 255 chars for name/email)
8. **Routing Logs**: RoutingDataSource logs at DEBUG level; aspect logs at TRACE level
9. **OpenAPI/Swagger**: Auto-generated API docs at `/swagger-ui.html`
10. **Health Monitoring**: Actuator endpoints expose datasource health status
11. **Retry Logic**: `@Retryable` with exponential backoff for transient failures
12. **Least Privilege**: Separate users for app (DML) and Liquibase (DDL)
13. **REST Conventions**: POST returns 201 Created; DELETE returns 204 No Content on success, 404 if not found; GET returns 404 if not found
14. **Complete CRUD**: Create, Read (by ID / by name / paginated), Update, Delete all implemented

## Usage Example

```java
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    // Write operation - routes to master
    @UseWriter
    @Transactional
    public User createUser(CreateUserRequest request) {
        return userRepository.save(user);
    }

    // Read operation - routes to replica (default)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }
}
```

## Tests

- **`DataSourceRoutingTest`** (7 unit tests, no Spring context): default→READER, runWithWriter, callWithWriter, revert-after-scope, nested scopes, thread isolation, revert-on-exception
- **`ReplicationIntegrationTest`** (9 tests, `@SpringBootTest` + single Testcontainers MySQL for both writer and reader): create+read, not-found GET, validation on create, delete, find-by-name, update, not-found update, not-found delete, validation on update

## Gotchas

- Replica is read-only (`--read-only=1`) - writes will fail if routing is misconfigured
- Replication lag may cause stale reads from replica
- Both databases must have identical schemas (Liquibase runs on master, replicates to slave)
- `@UseWriter` must be on the service method, not the repository method
- **CRITICAL**: Aspect order matters - `UseWriterAspect` uses `@Order(Ordered.LOWEST_PRECEDENCE - 10)` to run BEFORE `@Transactional`. The routing context MUST be set before any transaction begins. Do not change aspect ordering without understanding this requirement.
- JPA entities should use `@Getter/@Setter` instead of `@Data` to avoid equals/hashCode problems with mutable IDs
- Docker healthcheck ensures replica starts only after source is fully ready
- Application user has DML-only privileges; Liquibase uses root for schema migrations
