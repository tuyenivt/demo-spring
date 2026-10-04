# Database Migration Subproject

Production-ready Spring Boot application for continuous data synchronization between legacy and modern databases with schema transformation.

## Features

- **Dual DataSource**: Independent JPA configurations for source and target databases
- **Schema Management**: Flyway migrations for both databases - no manual table creation
- **Distributed Locking**: ShedLock prevents concurrent migration runs across instances
- **Transactional Safety**: Atomic batch writes with checkpoint updates
- **Database-Backed State**: Migration state stored in database (survives restarts, shared across instances)
- **Health Monitoring**: Actuator endpoint reports migration status and metrics
- **Error Resilience**: Retry logic with consecutive failure tracking
- **Type Safety**: `BigDecimal` for monetary values (no floating-point precision loss)
- **Configurable**: Externalized credentials and timezone offsets
- **Testable**: Unit tests for mapping logic, integration tests with Testcontainers

## Quick Start

### 1. Start MySQL Containers
```bash
# Source database (port 3306)
docker run -d --name mysql-old -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=demo mysql:8.0

# Target database (port 3307)
docker run -d --name mysql-new -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=demo mysql:8.0
```

### 2. Run Application
```bash
./gradlew :database-migration:bootRun
```

Flyway automatically creates all tables (`product`, `old_product`, `migration_state`, `shedlock`).

### 3. Monitor Migration
```bash
curl http://localhost:8080/actuator/health
```

## How It Works

1. **Scheduled Task** runs every 10 seconds (configurable)
2. **ShedLock** acquires distributed lock - only one instance proceeds
3. **Load State** from `migration_state` table (last sync timestamp)
4. **Query Source** for records where `updatedAt > lastSync` (read-only transaction)
5. **Transform** via MapStruct: `OldProduct` → `Product` with timezone adjustment
6. **Save Batch** and update state in single transaction (atomic)
7. **Release Lock** - other instances can run on next schedule

## Configuration

### Environment Variables (Production)
```bash
export DEMO_DB_USERNAME=prod_user
export DEMO_DB_PASSWORD=secure_password
export OLD_DEMO_DB_USERNAME=legacy_user
export OLD_DEMO_DB_PASSWORD=legacy_password
```

### application.yml
```yaml
scheduled:
  enabled: true     # Set false to turn off the scheduled migration
  fixedRate: 10000  # Migration frequency (milliseconds)

migration:
  timezone-offset-hours: -7  # Adjust source timestamps

spring:
  jpa:
    hibernate.ddl-auto: validate  # Fail fast on schema mismatch
    properties.hibernate.jdbc.batch_size: 100  # Batch size
```

## Architecture

```
┌─────────────────┐         ┌──────────────────┐
│  Source MySQL   │         │  Target MySQL    │
│  (port 3306)    │         │  (port 3307)     │
│                 │         │                  │
│  old_product    │         │  product         │
│  └─ quality     │  Map    │  └─ inStock      │
│  └─ updatedAt   │  ───►   │  └─ updatedAt-7h │
│  └─ price       │  Struct │  └─ vendor="ABC" │
│                 │         │                  │
│                 │         │  migration_state │
│                 │         │  shedlock        │
└─────────────────┘         └──────────────────┘
        ▲                           │
        │                           │
        └───────────────────────────┘
              ProductTasks
         (scheduled, locked)
```

## Testing

### Unit Tests
```bash
./gradlew :database-migration:test --tests ProductMapperTest
```

Tests MapStruct transformation logic:
- Field mapping correctness
- Null handling (updatedAt, quality)
- Timezone offset application
- Default values

### Integration Tests
```bash
./gradlew :database-migration:test --tests MainApplicationTests
```

End-to-end test with Testcontainers:
- Spins up two MySQL containers (source and target)
- Verifies application context loads successfully
- Flyway automatically creates all tables in both databases
- Seeds `old_product`, runs the migration and verifies `product`, `migration_state` and `/actuator/health`

## Production Checklist

- [ ] Configure environment variables for credentials
- [ ] Adjust `scheduled.fixedRate` based on data volume
- [ ] Set `migration.timezone-offset-hours` for your source database timezone
- [ ] Configure monitoring/alerting on CRITICAL log messages (3+ consecutive failures)
- [ ] Index `updated_at` columns in both databases (included in Flyway migrations)
- [ ] Test schema validation with `ddl-auto: validate` before deployment
- [ ] Verify ShedLock table exists in target database
- [ ] Set up actuator endpoint monitoring (`/actuator/health`)

## Troubleshooting

### Migration Not Running
```sql
-- Check ShedLock status
SELECT * FROM shedlock;

-- If lock is stale (locked_by crashed), delete:
DELETE FROM shedlock WHERE name = 'productMigration';
```

### Reset Migration (Re-process All Data)
```sql
DELETE FROM migration_state WHERE entity_name = 'Product';
```

### View Migration State
```sql
SELECT
  entity_name,
  last_updated_at,
  modified_at,
  TIMESTAMPDIFF(MINUTE, modified_at, NOW()) as minutes_since_last_sync
FROM migration_state;
```

### Schema Validation Failure
```
Caused by: SchemaManagementException: Schema-validation: missing column [vendor] in table [product]
```

Run Flyway manually or delete and recreate database:
```bash
docker rm -f mysql-new
docker run -d --name mysql-new -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=demo mysql:8.0
```

## Performance Notes

- **Batch Size**: Default 100 records per transaction. Increase for higher throughput (test memory impact).
- **Sequential Processing**: Uses `stream()` not `parallelStream()` - Hibernate session not thread-safe.
- **Read-Only Queries**: Source queries use `@Transactional(readOnly = true)` - no dirty checking overhead.
- **Indexed Queries**: `updated_at` columns indexed for fast range scans.

## Monitoring Metrics (Actuator)

```json
{
  "status": "UP",
  "details": {
    "migration": {
      "status": "UP",
      "details": {
        "lastSyncTimestamp": "2026-02-08T10:30:00",
        "lastModified": "2026-02-08T10:30:05",
        "timeSinceLastUpdate": "5 minutes",
        "recordsProcessedInLastRun": 150,
        "migrationRunning": false
      }
    }
  }
}
```

## Project Structure

```
database-migration/
├── src/main/java/com/example/database/migration/
│   ├── config/
│   │   ├── AppConfig.java                                # Batch size & timezone config
│   │   ├── FlywayConfig.java                             # Dual Flyway setup
│   │   ├── SchedulingConfig.java                         # Toggle via scheduled.enabled
│   │   └── ShedLockConfig.java                           # Distributed lock
│   ├── demo/                                             # Target database
│   │   ├── config/DemoDataSourceConfiguration.java
│   │   ├── entity/
│   │   │   ├── Product.java                              # Target entity (BigDecimal price)
│   │   │   └── MigrationState.java                       # Checkpoint tracking
│   │   ├── mapper/ProductMapper.java                     # MapStruct with @AfterMapping
│   │   ├── repository/
│   │   │   ├── ProductRepository.java
│   │   │   └── MigrationStateRepository.java
│   │   ├── service/ProductMigrationService.java          # Transactional writes
│   │   └── task/ProductTasks.java                        # Scheduled migration
│   ├── oldDemo/                                          # Source database
│   │   ├── config/OldDemoDataSourceConfiguration.java
│   │   ├── entity/OldProduct.java                        # Source entity (BigDecimal price)
│   │   ├── repository/OldProductRepository.java
│   │   └── service/OldProductService.java                # Read-only queries
│   └── health/MigrationHealthIndicator.java
├── src/main/resources/
│   ├── db/migration/
│   │   ├── demo/                                         # Target database migrations
│   │   │   ├── V1__create_product_table.sql
│   │   │   ├── V2__create_migration_state_table.sql
│   │   │   └── V3__create_shedlock_table.sql
│   │   └── old_demo/                                     # Source database migrations
│   │       └── V1__create_old_product_table.sql
│   └── application.yml
└── src/test/java/
    ├── demo/mapper/ProductMapperTest.java
    └── MainApplicationTests.java                         # Testcontainers integration test
```

## Dependencies

- Spring Boot Starter Data JPA
- Spring Boot Starter Actuator
- Spring Boot Starter Web MVC (serves `/actuator/health`)
- Spring Boot Starter Flyway + Flyway MySQL
- MapStruct (entity mapping)
- ShedLock Spring + ShedLock JDBC Template
- MySQL Connector/J
- Lombok
- Testcontainers MySQL (test)
- Spring Boot Testcontainers (test)
