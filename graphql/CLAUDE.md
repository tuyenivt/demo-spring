# GraphQL Subproject

Spring Boot GraphQL API for Student-Vehicle management system with advanced pagination, filtering, sorting, and versioning.

## Quick Start

```bash
cd graphql
../gradlew bootRun
```

- GraphiQL UI: http://localhost:8080/graphiql
- GraphQL Endpoint: http://localhost:8080/graphql

## Project Structure

```
src/main/java/com/example/graphql/
├── config/           # GraphQL configuration (scalars)
├── controller/       # GraphQL resolvers (Student, Vehicle, Version)
├── service/          # Business logic with validation
├── repository/       # JPA repositories with Specification support
├── entity/           # JPA entities (Student, Vehicle)
├── dto/
│   ├── filter/       # StringFilter, DateTimeFilter, VehicleTypeFilter, UUIDFilter
│   ├── input/        # Create/Update/Upsert input records
│   ├── pagination/   # PageInput, PageResult, Connection, Edge, PageInfoDto
│   ├── sort/         # StudentSort, VehicleSort, SortField enums, SortDirection
│   └── versioning/   # ApiVersion, StudentV1, VehicleV1
├── enums/            # VehicleType enum
├── exception/        # ErrorCode, GraphQLErrorHandler, custom exceptions
├── interceptor/      # VersionInterceptor
├── specification/    # JPA Specification for dynamic filtering
├── validation/       # StudentValidator, VehicleValidator
└── util/             # CursorUtils (Base64 encode/decode), SortUtils, AgeUtils

src/main/resources/graphql/
├── root.graphqls         # Base Query/Mutation types
├── scalars.graphqls      # UUID, DateTime scalars
├── pagination.graphqls   # PageInput, filters, connection types
├── studentql.graphqls    # Student schema
├── vehicleql.graphqls    # Vehicle schema
└── versioning.graphqls   # V1 deprecated types + ApiVersion
```

## Key Technologies

- **Spring Boot 3.x** with Spring GraphQL
- **H2 Database** (embedded, in-memory)
- **JPA/Hibernate** with Specification pattern
- **graphql-java-extended-scalars** for UUID, DateTime
- **Virtual Threads** enabled

## Domain Model

### Student
- id (UUID), name, address, dateOfBirth
- One-to-Many relationship with Vehicle
- Timestamps: createdAt, updatedAt

### Vehicle
- id (UUID), type (enum: CAR, MOTORCYCLE, BICYCLE, TRUCK, BUS, VAN, SCOOTER)
- Many-to-One relationship with Student (optional)
- Timestamps: createdAt, updatedAt

## GraphQL Operations

### Queries

| Query                                          | Description                                                  |
|------------------------------------------------|--------------------------------------------------------------|
| `student(id)`                                  | Get student by ID                                            |
| `students(limit)` *(deprecated)*               | List students (use studentsPage)                             |
| `studentsPage(page, filter, sort)`             | Offset-based pagination                                      |
| `studentsConnection(connection, filter, sort)` | Cursor-based (Relay) pagination                              |
| `vehicles(limit)` *(deprecated)*               | List vehicles (use vehiclesPage)                             |
| `vehiclesPage(page, filter, sort)`             | Offset-based pagination                                      |
| `vehiclesConnection(connection, filter, sort)` | Cursor-based pagination                                      |
| `apiVersion`                                   | API version info (v2.0, deprecated features, supportedUntil) |
| `studentV1(id)` *(deprecated)*                 | V1 student shape                                             |
| `studentsV1(limit)` *(deprecated)*             | V1 student list                                              |
| `vehiclesV1(limit)` *(deprecated)*             | V1 vehicle list                                              |

### Mutations

| Mutation                 | Description           |
|--------------------------|-----------------------|
| `createStudent(input)`   | Create single student |
| `createStudents(inputs)` | Bulk create students  |
| `updateStudent(input)`   | Partial update        |
| `upsertStudent(input)`   | Create or update      |
| `createVehicle(input)`   | Create vehicle        |
| `createVehicles(inputs)` | Bulk create vehicles  |
| `updateVehicle(input)`   | Partial update        |
| `upsertVehicle(input)`   | Create or update      |

**Note on Partial Updates:** `update` mutations skip `null` fields (cannot explicitly clear a field). Use `upsert` with all fields to do a full replacement.

### BatchMapping (N+1-safe)
- `StudentController.vehicles(List<Student>)` → `Map<Student, List<Vehicle>>`
- `VehicleController.student(List<Vehicle>)` → `Map<Vehicle, Student>`

## Filtering

### StringFilter
```graphql
{ name: { contains: "John" } }
{ name: { startsWith: "J", endsWith: "n" } }
{ name: { in: ["John", "Jane"] } }
```

### DateTimeFilter
```graphql
{ createdAt: { gt: "2024-01-01T00:00:00Z" } }
{ createdAt: { between: { start: "2024-01-01", end: "2024-12-31" } } }
```

### VehicleTypeFilter
```graphql
{ type: { eq: CAR } }
{ type: { in: [CAR, MOTORCYCLE] } }
```

### UUIDFilter
```graphql
{ studentId: { eq: "uuid-here" } }
```

## Pagination Examples

### Offset-based
```graphql
query {
  studentsPage(
    page: { page: 0, size: 10 }
    filter: { name: { contains: "John" } }
    sort: { field: NAME, direction: ASC }
  ) {
    content { id name }
    pageInfo { totalElements hasNext }
  }
}
```

### Cursor-based (Relay)
```graphql
query {
  studentsConnection(
    connection: { first: 10, after: "cursor..." }
  ) {
    edges { cursor node { id name } }
    pageInfo { hasNextPage endCursor }
    totalCount
  }
}
```

## Business Rules

### Student Validation
- Name: 2-100 characters, letters/spaces/hyphens/apostrophes
- Address: max 200 characters
- DateOfBirth: not future, age 5-100 years

### Vehicle Validation
- Max 5 vehicles per student
- Age restrictions by vehicle type:
  - CAR, TRUCK, VAN, BUS: 16+
  - MOTORCYCLE, SCOOTER: 18+
  - BICYCLE: no restriction
- `createAll` validates total count before any saves (uses `countByStudentId`)

## Error Handling

`ErrorCode` enum with code, message, statusCode, and `isTechnicalError()`:
- `VALIDATION_ERROR` (400) - Input validation failed
- `INVALID_INPUT` (400) - Invalid input provided
- `RESOURCE_NOT_FOUND` (404) - Entity not found
- `DUPLICATE_RESOURCE` (409) - Resource already exists
- `BUSINESS_RULE_VIOLATION` (422) - Business rule violated
- `STUDENT_AGE_INVALID` (422) - Student age is invalid
- `VEHICLE_ASSIGNMENT_ERROR` (422) - Cannot assign vehicle to student
- `STUDENT_HAS_VEHICLES` (409) - Cannot delete student with vehicles
- `INTERNAL_SERVER_ERROR` (500), `DATABASE_ERROR` (500), `EXTERNAL_SERVICE_ERROR` (503)

`GraphQLErrorHandler` maps exceptions to GraphQL errors with `extensions` (errorCode, statusCode, fieldErrors for validation).

## Versioning

- `VersionController` serves `apiVersion`, `studentV1`, `studentsV1`, `vehiclesV1`
- `ApiVersion` record: version="2.0", deprecatedFeatures list, supportedUntil="2027-12-31"
- `StudentV1` / `VehicleV1`: flattened V1 DTOs mapped from current entities via static `from()` factory
- `VersionInterceptor`: HTTP interceptor for version tracking
- `@deprecated` used in schema; deprecated queries still functional

## Configuration

Key settings in `application.yml`:
```yaml
server.port: 8080
spring.graphql.graphiql.enabled: true
spring.threads.virtual.enabled: true
```

## Testing

```bash
../gradlew test
```

4 test classes — all use `@MockitoBean` (Spring Boot 3.4+):
- `StudentControllerTest` (6 tests) — `@GraphQlTest` + `GraphQlTester`; imports `GraphQLConfig`, mocks `StudentService` + `VehicleRepository`
- `VehicleControllerTest` (5 tests) — `@GraphQlTest` + `GraphQlTester`; imports `GraphQLConfig`, mocks `VehicleService` + `StudentRepository`
- `StudentServiceTest` (8 tests) — `@ExtendWith(MockitoExtension.class)`; unit tests for create/findById/update/findPage/findConnection/createAll/findAll
- `VehicleServiceTest` (9 tests) — `@ExtendWith(MockitoExtension.class)`; unit tests for create (with/without student)/update/findPage/findConnection/createAll/findAll

## Missing Demos

- `deleteStudent` / `deleteVehicle` mutations (requires `STUDENT_HAS_VEHICLES` error code already defined)
- GraphQL subscriptions (`Flux<Student>`)
- Computed field `age` via `@SchemaMapping`
- `vehicleCount` field with `countByStudentId`
- `vehiclesByStudent` convenience query
- `DataFetchingEnvironment` field selection optimization
- Bean Validation integration (`@Valid` on inputs)
- `@BatchMapping` test coverage

## Missing Tests

- `updateStudent` / `upsertStudent` / `updateVehicle` / `upsertVehicle` mutation tests in controller tests
- Back-pagination (`last`/`before`) cursor tests
- `VersionController` / `VersionInterceptor` tests
- `ETag` / cache header tests
