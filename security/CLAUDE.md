# Security Subproject

Spring Boot MVC application demonstrating Spring Security with role-based access control (RBAC).

## Tech Stack

- Java 25 / Spring Boot 4.1 / Spring Security 7.1
- Spring Security (form-based auth, method-level security)
- Thymeleaf + `thymeleaf-extras-springsecurity6` (`sec:authorize` dialect; the artifact managed by Boot 4.1)
- H2 Database (embedded, unused — JPA + `spring-boot-h2console` deps present but no entities, console not enabled)
- Lombok
- JUnit 5 + `spring-boot-starter-security-test` / `-webmvc-test` / `-thymeleaf-test` / `-data-jpa-test`

## Project Structure

```
security/
├── src/main/java/com/example/security/
│   ├── MainApplication.java          # Entry point
│   ├── config/
│   │   └── SecurityConfig.java       # Security configuration
│   └── controller/
│       ├── HomeController.java       # GET / (EMPLOYEE)
│       ├── LoginController.java      # GET /my-login/ (login page), GET /access-denied
│       ├── LeadersController.java    # GET /leaders/ (MANAGER)
│       └── SystemsController.java    # GET /systems/ (ADMIN)
├── src/main/resources/
│   ├── application.properties
│   └── templates/                    # Thymeleaf templates
│       ├── home/index.html
│       ├── login/index.html
│       ├── login/access-denied.html
│       ├── leaders/index.html
│       └── systems/index.html
└── src/test/java/
    ├── MainApplicationTests.java     # Context load test
    ├── SecurityTests.java            # Security integration tests
    └── EncryptionTests.java          # Encryption utility demos
```

## Security Configuration

### Features

- **Password Encoding**: BCryptPasswordEncoder
- **Method-Level Security**: @EnableMethodSecurity with @PreAuthorize support
- **Remember-Me**: Cookie-based with 1-day validity
- **Session Management**: `maximumSessions(1)`, `maxSessionsPreventsLogin(false)` — new login replaces old session (does not block)
- **Security Headers**: Frame options deny, CSP default-src 'self'
- **Logout**: Session invalidation, JSESSIONID cookie deletion

### Users (In-Memory)

| Username | Password | Roles              |
|----------|----------|--------------------|
| john     | 123      | EMPLOYEE           |
| peter    | 123      | EMPLOYEE, MANAGER  |
| frank    | 123      | EMPLOYEE, ADMIN    |

### Authorization Rules

| Path           | Required Role |
|----------------|---------------|
| `/`            | EMPLOYEE      |
| `/leaders/**`  | MANAGER       |
| `/systems/**`  | ADMIN         |
| Other          | Authenticated |

### Login Configuration

- Login Page: `/my-login/`
- Auth Endpoint: `/my-authenticate`
- Access Denied: `/access-denied`
- Logout Success: `/my-login/?logout`
- Session Expired: `/my-login/?expired`

## Build & Run

```bash
# From project root
./gradlew :security:bootRun

# Run tests
./gradlew :security:test
```

## Tests

### SecurityTests.java (15 tests, `@WebMvcTest` + `@Import(SecurityConfig.class)`)
- `homeRequiresAuthentication` — unauthenticated redirect to `/my-login/`
- `loginPageIsAccessibleWithoutAuthentication` — `/my-login/` → 200, `login/index` view
- `validCredentialsAuthenticateWithUserRoles` — form login as peter → redirect `/`, authenticated with EMPLOYEE + MANAGER
- `invalidCredentialsRedirectToLoginWithError` — wrong password → `/my-login/?error`
- `rememberMeSetsCookieWhenRequested` — `remember-me=on` → `remember-me` cookie, max-age 86400
- `logoutRedirectsToLoginPage` — POST `/logout` → `/my-login/?logout`
- `employeeCanAccessHome` — EMPLOYEE → `/` → 200
- `employeeCannotAccessLeaders` — EMPLOYEE → `/leaders/` → 403
- `employeeCannotAccessSystems` — EMPLOYEE → `/systems/` → 403
- `managerCanAccessLeaders` — MANAGER → `/leaders/` → 200
- `managerCannotAccessSystems` — MANAGER → `/systems/` → 403
- `adminCanAccessSystems` — EMPLOYEE+ADMIN → `/systems/` → 200
- `managerWithEmployeeCanAccessBothHomeAndLeaders` — multi-role access check
- `accessDeniedPageIsAccessible` — unauthenticated → redirect
- `accessDeniedPageIsAccessibleForAuthenticatedUser` — authenticated → 200

### EncryptionTests.java (4 tests, no Spring context)
- `testBCrypt` — `BCrypt.hashpw` + `BCrypt.checkpw`
- `testBCryptPasswordEncoder` — `BCryptPasswordEncoder.encode` + `matches`
- `testKeyGenerator` — `KeyGenerators.string().generateKey()` (16 hex chars, usable as a salt)
- `testEncryptor` — `AesGcmBytesEncryptor.withPassword(password, hexSalt)` (AES-GCM, PBKDF2 key) + `Hex` encode/decode roundtrip; random IV → different ciphertext per call

## Key Files

- `SecurityConfig.java` - All security rules, password encoding, session management
- `home/index.html` - Role-based conditional rendering with `sec:authorize`
- `login/index.html` - Login form with remember-me checkbox
- `SecurityTests.java` - Role-based access control tests
- `EncryptionTests.java` - BCrypt and Spring Crypto API demos

## Missing Demos

- `@PreAuthorize` / `@PostAuthorize` in service layer (only configured, not demonstrated)
- `@PreFilter` / `@PostFilter` for collection filtering
- JWT / OAuth2 resource server (project uses form login only)
- CSRF configuration (currently default — enabled for form login)
- CORS configuration
- Database-backed `UserDetailsService` (currently in-memory)
- Persistent remember-me (`PersistentTokenRepository`)
- `HttpSessionEventPublisher` for session registry cleanup
