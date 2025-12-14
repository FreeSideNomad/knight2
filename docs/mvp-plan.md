# MVP Implementation Plan

## Overview

Employee-facing Vaadin portal interacting with platform domain through REST API (BFF pattern). Focus on Client management with accounts and profiles display.

### Architecture Summary
- **employee-portal**: Vaadin app (new Maven module at root level)
- **application**: Spring Boot backend with REST API (BFF)
- **domain/clients**: Client and ClientAccount aggregates
- **domain/profiles**: ServicingProfile and OnlineProfile (existing)
- **kernel**: Shared value objects (ClientId, ClientAccountId, etc.)
- **employee-gateway**: nginx gateway (copied from entra-auth)
- **Infrastructure**: Docker Compose (nginx, portal, platform, SQL Server, Kafka)

---

## Implementation Points

### Phase 1: Shared Kernel Extensions (Points 1-3)

#### 1. Create ClientAccountId Value Object
**Location**: `kernel/src/main/java/com/knight/platform/sharedkernel/`

Create `ClientAccountId` value object following URN scheme: `{accountSystem}:{accountType}:{accountNumberSegments}`

**Tasks**:
- Create `AccountSystem` enum: `CAN_DDA`, `CAN_FCA`, `CAN_LOC`, `CAN_MTG`, `US_FIN`, `US_FIS`, `OFI`
- Create `AccountType` enum: `DDA`, `CC`, `LOC`, `MTG`
- Create `OfiAccountType` enum: `CAN`, `IBAN`, `US`, `SWIFT`
- Create `ClientAccountId` record with validation rules per system/type combination
- Implement segment validation (e.g., CAN_DDA:DDA requires transit(5) + accountNumber(12))
- Add URN parsing and formatting methods

**Validation Rules**:

| System | Type | Segments |
|--------|------|----------|
| CAN_DDA | DDA | transit(5):accountNumber(12) |
| OFI | CAN | bank(3):transit(5):accountNumber(12) |
| OFI | IBAN | bic(8-11):ibanAccountNumber(34) |
| OFI | US | abaRouting(9):accountNumber(17) |
| OFI | SWIFT | bic(8-11):bban(34) |

---

#### 2. Create Address Value Object
**Location**: `kernel/src/main/java/com/knight/platform/sharedkernel/`

Create `Address` value object for client addresses.

**Tasks**:
- Create `Address` record with fields:
  - `addressLine1` (required)
  - `addressLine2` (optional)
  - `city` (required)
  - `stateProvince` (required)
  - `zipPostalCode` (required)
  - `countryCode` (required, ISO 3166-1 alpha-2)
- Add validation for required fields
- Add `CountryCode` validation or enum

---

#### 3. Create AccountStatus Enum and Currency Value Object
**Location**: `kernel/src/main/java/com/knight/platform/sharedkernel/`

**Tasks**:
- Create `AccountStatus` enum: `ACTIVE`, `CLOSED`
- Create `Currency` value object with ISO 4217 3-letter code validation
  - Validate against standard currency codes (CAD, USD, EUR, GBP, etc.)
  - Immutable record with `code()` accessor
  - Factory method `Currency.of(String code)` with validation

---

### Phase 2: Domain Layer - Clients (Points 4-6)

#### 4. Create Client Aggregate
**Location**: `domain/clients/src/main/java/com/knight/domain/clients/`

Create new `Client` aggregate (distinct from existing `IndirectClient`).

**Tasks**:
- Create package structure: `aggregate/`, `repository/`, `api/commands/`, `api/queries/`, `api/events/`
- Create `Client` aggregate root with:
  - `ClientId clientId` (SrfClientId or CdrClientId)
  - `String name`
  - `Address address`
  - `Instant createdAt`
  - `Instant updatedAt`
- Create `ClientRepository` interface with methods:
  - `Optional<Client> findById(ClientId id)`
  - `List<Client> searchByTypeAndName(String clientType, String nameQuery)`
  - `void save(Client client)`

---

#### 5. Create ClientAccount Aggregate
**Location**: `domain/clients/src/main/java/com/knight/domain/clients/`

**Tasks**:
- Create `ClientAccount` aggregate in `aggregate/` package:
  - `ClientAccountId accountId`
  - `ClientId clientId`
  - `String currency` (ISO 4217 3-letter code)
  - `AccountStatus status`
  - `Instant createdAt`
- Create `ClientAccountRepository` interface:
  - `Optional<ClientAccount> findById(ClientAccountId id)`
  - `List<ClientAccount> findByClientId(ClientId clientId)`
  - `void save(ClientAccount account)`

---

#### 6. Create Client Query DTOs
**Location**: `domain/clients/src/main/java/com/knight/domain/clients/api/queries/`

**Tasks**:
- Create `ClientSearchQuery` record: `(String clientType, String nameQuery)`
- Create `ClientSearchResult` record: `(ClientId clientId, String name, String clientType)`
- Create `ClientDetailResponse` record with full client details
- Create `ClientAccountResponse` record for account display

---

### Phase 3: Application Layer - Persistence (Points 7-9)

#### 7. Create Client JPA Entities
**Location**: `application/src/main/java/com/knight/application/persistence/clients/`

**Tasks**:
- Create `ClientEntity` JPA entity mapping to `clients` table:
  - `client_id` (URN string, primary key)
  - `client_type` (srf/cdr discriminator)
  - `name`
  - Address fields (embedded or separate columns)
  - Audit timestamps
- Create `ClientJpaRepository` (Spring Data)
- Create `ClientRepositoryAdapter` implementing domain `ClientRepository`
- Create `ClientMapper` (MapStruct) for entity <-> domain conversion

---

#### 8. Create ClientAccount JPA Entities
**Location**: `application/src/main/java/com/knight/application/persistence/clients/`

**Tasks**:
- Create `ClientAccountEntity` JPA entity mapping to `client_accounts` table:
  - `account_id` (URN string, primary key)
  - `client_id` (foreign key)
  - `account_system`
  - `account_type`
  - `currency`
  - `status`
  - Audit timestamps
- Create `ClientAccountJpaRepository` (Spring Data)
- Create `ClientAccountRepositoryAdapter` implementing domain repository
- Create `ClientAccountMapper` (MapStruct)

---

#### 9. Create Flyway Migrations
**Location**: `application/src/main/resources/db/migration/`

**Tasks**:
- Create `V1__create_clients_schema.sql`:
  - Create `clients` table
  - Create `client_accounts` table with FK to clients
  - Add indexes for search (client_type, name)
- Ensure SQL Server compatibility (schema: `dbo` or custom)

---

### Phase 4: Application Layer - REST API (Points 10-12)

#### 10. Create Client REST Controller
**Location**: `application/src/main/java/com/knight/application/rest/clients/`

**Tasks**:
- Create `ClientController` with endpoints:
  - `GET /api/clients?type={srf|cdr}&name={search}` - search clients
  - `GET /api/clients/{clientId}` - get client details
  - `GET /api/clients/{clientId}/accounts` - get client accounts
  - `GET /api/clients/{clientId}/profiles` - get client profiles
- Create request/response DTOs for REST layer
- Add input validation (@Valid, etc.)
- Wire to domain repositories/services

---

#### 11. Create Profile REST Endpoints
**Location**: `application/src/main/java/com/knight/application/rest/serviceprofiles/`

**Tasks**:
- Create `ProfileController` with endpoints:
  - `GET /api/clients/{clientId}/profiles/servicing` - get servicing profiles
  - `GET /api/clients/{clientId}/profiles/online` - get online profiles
- Return empty list for MVP (profiles tab shows buttons only)
- Prepare response DTOs for future profile data

---

#### 12. Configure REST API
**Location**: `application/src/main/java/com/knight/application/config/`

**Tasks**:
- Create `WebConfiguration` for REST setup:
  - CORS configuration (allow employee-portal origin)
  - JSON serialization settings (dates, enums)
  - Exception handling (@ControllerAdvice)
- Create `SecurityConfiguration` for OAuth2/OIDC resource server
- Add OpenAPI/Swagger documentation (optional but recommended)

---

### Phase 5: Employee Portal - Vaadin App (Points 13-16)

#### 13. Create Employee Portal Maven Module
**Location**: `/employee-portal/` (root level)

**Tasks**:
- Create `employee-portal/pom.xml` with:
  - Parent: commercial-platform
  - Dependencies: Vaadin 24, Spring Boot, OAuth2 client
  - Vaadin production build configuration
- Add module to root `pom.xml`
- Create `EmployeePortalApplication.java` Spring Boot main class
- Create `application.yml` with:
  - Server port (e.g., 8081)
  - OAuth2/OIDC client configuration (Azure AD/Entra ID)
  - Backend API URL

---

#### 14. Create Authentication Integration
**Location**: `employee-portal/src/main/java/.../security/`

**Tasks**:
- Configure Spring Security OAuth2 login (Azure AD/Entra ID)
- Create authenticated user display component (top-right corner menu)
- Display user name from OIDC claims
- Add logout functionality
- Reference entra-auth security model implementation

---

#### 15. Create Client Search View
**Location**: `employee-portal/src/main/java/.../views/`

**Tasks**:
- Create `ClientSearchView` as main landing page:
  - Client type selector (SRF/CDR dropdown or radio)
  - Name search text field
  - Search button
  - Results grid showing: ClientId, Name, Type
  - Click row to navigate to client detail
- Create `ClientService` to call backend REST API
- Use Vaadin's `Grid` component with lazy loading

---

#### 16. Create Client Detail View
**Location**: `employee-portal/src/main/java/.../views/`

**Tasks**:
- Create `ClientDetailView` layout:
  ```
  ClientId: {value}
  Name: {value}
  Address: {formatted address}

  [Profiles Tab] [Accounts Tab]
  {Tab content area}
  ```
- **Profiles Tab**:
  - Two buttons: [Service Profile] [Online Profile]
  - Clicking navigates to empty placeholder form view
  - Empty data grid (placeholder for future)
- **Accounts Tab**:
  - Grid showing client accounts
  - Columns: Account ID, Type, Currency, Status
- Create `ProfilePlaceholderView` for button navigation targets

---

### Phase 6: Test Data Generator (Points 17-18)

#### 17. Create Faker-Based Test Data Generator
**Location**: `application/src/test/java/com/knight/application/testdata/`

**Tasks**:
- Add JavaFaker dependency to application module (test scope)
- Create `TestDataGenerator` class:
  - `generateClients(int count, String clientType)` - generate SRF or CDR clients
  - `generateAccountsForClient(ClientId clientId, int count)` - generate accounts
  - Configurable via command-line args or properties
- Generate realistic data:
  - Canadian/US business names
  - Valid addresses
  - Proper account number formats per system/type

---

#### 18. Create Test Data CLI Runner
**Location**: `application/src/test/java/com/knight/application/testdata/`

**Tasks**:
- Create `TestDataRunner` as Spring Boot test with CLI support
- Create shell script `scripts/generate-test-data.sh`:
  ```bash
  #!/bin/bash
  ./mvnw test -pl application -Dtest=TestDataRunner \
    -DclientCount=50 -DaccountsPerClient=5
  ```
- Support parameters:
  - `--client-count` (default: 50)
  - `--accounts-per-client` (default: 3)
  - `--client-types` (default: both srf,cdr)
- Output summary of generated data

---

### Phase 7: Infrastructure (Points 19-20)

#### 19. Setup nginx Gateway
**Location**: `/employee-gateway/`

**Tasks**:
- Copy nginx configuration from `/Users/igormusic/code/entra-auth`
- Adapt for knight2 project:
  - Route `/api/*` to platform application (port 8080)
  - Route `/` to employee-portal (port 8081)
  - Configure SSL/TLS (self-signed for dev)
  - OIDC integration with Azure AD
- Create `employee-gateway/nginx.conf`
- Create `employee-gateway/Dockerfile`

---

#### 20. Create Docker Compose
**Location**: `/docker-compose.yml`

**Tasks**:
- Create `docker-compose.yml` with services:
  ```yaml
  services:
    gateway:
      build: ./employee-gateway
      ports: ["443:443", "80:80"]
      depends_on: [portal, platform]

    portal:
      build: ./employee-portal
      ports: ["8081:8081"]
      environment:
        - SPRING_PROFILES_ACTIVE=docker
        - API_URL=http://platform:8080

    platform:
      build: ./application
      ports: ["8080:8080"]
      depends_on: [sqlserver, kafka]
      environment:
        - SPRING_PROFILES_ACTIVE=docker
        - SPRING_DATASOURCE_URL=jdbc:sqlserver://sqlserver:1433;...

    sqlserver:
      image: mcr.microsoft.com/mssql/server:2022-latest
      ports: ["1433:1433"]
      environment:
        - ACCEPT_EULA=Y
        - SA_PASSWORD=...

    kafka:
      image: confluentinc/cp-kafka:7.5.0
      ports: ["9092:9092"]
      # Kafka configuration...
  ```
- Create Dockerfiles for portal and platform if not existing
- Create `.env.example` with required environment variables
- Create `scripts/docker-up.sh` and `scripts/docker-down.sh`

---

## Implementation Order

Recommended execution sequence:

| Order | Points | Phase | Dependencies |
|-------|--------|-------|--------------|
| 1 | 1-3 | Shared Kernel | None |
| 2 | 4-6 | Domain Layer | Phase 1 |
| 3 | 7-9 | Persistence | Phase 2 |
| 4 | 10-12 | REST API | Phase 3 |
| 5 | 13-16 | Vaadin Portal | Phase 4 |
| 6 | 17-18 | Test Data | Phase 3 |
| 7 | 19-20 | Infrastructure | All above |

---

## Success Criteria

MVP is complete when:
1. Employee can authenticate via Azure AD
2. Employee can search clients by type (SRF/CDR) and name
3. Employee can view client details (ID, Name, Address)
4. Employee can view client accounts in Accounts tab
5. Employee can see Profiles tab with Service Profile and Online Profile buttons
6. Clicking profile buttons navigates to placeholder forms
7. Test data can be generated via shell script
8. All services run via Docker Compose

---

## Out of Scope (Post-MVP)

- Profile creation functionality
- Profile data display
- RBAC/permissions
- Kafka event publishing
- Real external data integration (SRF, Express)
- Indirect client management
- Approval workflows