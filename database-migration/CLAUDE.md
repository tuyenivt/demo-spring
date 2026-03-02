# Database Migration Subproject

## Tech Stack
- Spring Boot 3.5.10
- Java 25
- Spring Data JPA (dual datasource)
- MapStruct (entity mapping)
- MySQL 8.0
- Flyway (schema migration)
- ShedLock (distributed locking)
- Testcontainers (integration tests)

## Architecture

### Dual DataSource Pattern
Two independent JPA configurations for source and target databases:
- **Demo (Primary)**: `localhost:3307/demo` - Target database with `product` table
- **OldDemo (Secondary)**: `localhost:3306/demo` - Source database with `old_product` table

Each has isolated:
- DataSource (`demoDataSource`, `oldDemoDataSource`)
- EntityManagerFactory (`demoEntityManagerFactory`, `oldDemoEntityManagerFactory`)
- TransactionManager (`demoTransactionManager`, `oldDemoTransactionManager`)
- Repository scanning (`demo.repository`, `oldDemo.repository`)

### Migration Flow
1. **State Check**: Load last sync timestamp from `migration_state` table
2. **Query Source**: Read records from `old_product` where `updatedAt > lastSync` (read-only transaction)
3. **Transform**: MapStruct mapper converts `OldProduct` → `Product` with timezone adjustment
4. **Batch Write**: Save products and update migration state in single transaction
5. **Lock Management**: ShedLock ensures only one instance runs at a time

### Entity Mapping (MapStruct)

| OldProduct         | Product            | Transformation                |
|--------------------|--------------------|-------------------------------|
| productId          | productId          | Direct                        |
| productName        | productName        | Direct                        |
| price (BigDecimal) | price (BigDecimal) | Direct                        |
| quality            | inStock            | Renamed, default 0            |
| dateOfManufacture  | dateOfManufacture  | Direct                        |
| updatedAt          | updatedAt          | Timezone shift (configurable) |
| -                  | vendor             | Constant "ABC"                |

## Key Files

| File                               | Purpose                                            |
|------------------------------------|----------------------------------------------------|
| `ProductTasks.java:42`             | Scheduled migration entry point with ShedLock      |
| `ProductMapper.java:14`            | MapStruct entity transformation with @AfterMapping |
| `ProductMigrationService.java:18`  | Transactional batch save + state update            |
| `OldProductService.java:18`        | Read-only queries to source database               |
| `MigrationHealthIndicator.java:20` | Actuator health check for migration status         |
| `FlywayConfig.java:12`             | Dual Flyway configuration for both databases       |
| `ShedLockConfig.java:14`           | Distributed lock provider configuration            |

## Configuration

### application.yml
```yaml
spring:
  jpa:
    hibernate.ddl-auto: validate  # Fail fast on schema mismatch
migration:
  timezone-offset-hours: -7  # Configurable timezone adjustment
scheduled:
  fixedRate: 10000  # Migration runs every 10 seconds
```

### Environment Variables
- `DEMO_DB_USERNAME`, `DEMO_DB_PASSWORD`: Target database credentials
- `OLD_DEMO_DB_USERNAME`, `OLD_DEMO_DB_PASSWORD`: Source database credentials

## Database Schema

### Target Database (demo)
- `product`: Main product table
- `migration_state`: Tracks last sync timestamp per entity
- `shedlock`: Distributed lock table
- `flyway_schema_history`: Flyway version control

### Source Database (old_demo)
- `old_product`: Legacy product table
- `flyway_schema_history`: Flyway version control

## Running

### Prerequisites
Two MySQL instances:
```bash
# Source database (port 3306)
docker run -d --name mysql-old -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=demo mysql:8.0

# Target database (port 3307)
docker run -d --name mysql-new -p 3307:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=demo mysql:8.0
```

### Start Application
```bash
./gradlew :database-migration:bootRun
```

Flyway creates all tables automatically on startup.

## Monitoring

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

Response includes:
- Last sync timestamp
- Time since last update
- Records processed in last run
- Migration running status

### Logs
Migration logs at INFO level:
- Trigger events
- Batch processing progress
- State updates
- Error handling with retry counter

## Testing

### Unit Tests
`ProductMapperTest.java` - MapStruct transformation logic:
- Field mapping correctness
- Null handling (updatedAt, quality)
- Timezone offset application
- Default values (vendor, inStock)

### Integration Tests
`MainApplicationTests.java` - Context load test with Testcontainers:
- Two MySQL containers (source and target, both `mysql:8.4`)
- Verifies Spring context loads with dual datasource configuration
- Flyway auto-creates all tables (`product`, `migration_state`, `shedlock`, `old_product`)
- Scheduling disabled via `spring.task.scheduling.enabled=false`
- Does NOT seed, trigger, or verify migration data (context-load only)

Run tests:
```bash
./gradlew :database-migration:test
```

## Production Considerations

### Transactional Boundaries
- **Source queries**: `@Transactional(readOnly = true)` - no dirty checking overhead
- **Target writes**: `@Transactional` - atomic batch save + state update
- If save fails, checkpoint not advanced → no data skipped

### Concurrency Control
- **ShedLock**: Only one instance migrates at a time (across all instances)
- Lock held for minimum 10 seconds, maximum 5 minutes
- Uses database table for distributed coordination

### Error Handling
- Try-catch wraps entire migration
- Consecutive failure counter (max 3)
- CRITICAL log after 3 failures → alerting integration point
- State not updated on failure → retry processes same batch

### Performance
- Sequential stream (not parallel) - safer for Hibernate session
- Batch size: 100 (configurable via `spring.jpa.properties.hibernate.jdbc.batch_size`)
- Hibernate batch inserts enabled (`order_inserts`, `order_updates`)
- Indexed `updated_at` columns for efficient range queries

## Common Tasks

### Reset Migration State
```sql
DELETE FROM migration_state WHERE entity_name = 'Product';
```

### Check Last Sync
```sql
SELECT * FROM migration_state WHERE entity_name = 'Product';
```

### View ShedLock Status
```sql
SELECT * FROM shedlock;
```

### Change Timezone Offset
Update `application.yml`:
```yaml
migration:
  timezone-offset-hours: -5  # Changed from -7
```
Restart required.
