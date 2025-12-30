# Knight Platform: SQL Server to PostgreSQL Migration Plan

## Executive Summary

This document outlines a detailed migration plan to replace **SQL Server** with **PostgreSQL 18** in the Knight Platform. The migration is relatively low-risk because the codebase uses JPA/Hibernate with JPQL queries (no native SQL), and SQL Server-specific features are limited to data type definitions.

### Current State
- **Database**: SQL Server 2022
- **ORM**: Hibernate with Spring Data JPA
- **Migrations**: Flyway with SQL Server dialect
- **Queries**: 100% JPQL (no native SQL)

### Target State
- **Database**: PostgreSQL 18 (released September 25, 2025)
- **ORM**: Hibernate with Spring Data JPA (unchanged)
- **Migrations**: Flyway with PostgreSQL dialect
- **Queries**: JPQL (unchanged)

### PostgreSQL 18 Key Features
- **Asynchronous I/O (AIO)**: Up to 2-3x performance improvements for read-heavy workloads
- **`uuidv7()` function**: Generate timestamp-ordered UUIDs natively
- **Virtual generated columns**: Compute values during read operations (new default)
- **OAuth authentication support**: Native OAuth integration
- **Wire protocol 3.2**: First new protocol version since PostgreSQL 7.4 (2003)
- **Faster upgrades**: Preserves planner statistics through major version upgrades

### Library Versions

| Library | Version | Notes |
|---------|---------|-------|
| PostgreSQL | 18.1 | Latest stable (Nov 2025) |
| PostgreSQL JDBC Driver | 42.7.7 | Includes CVE-2025-49146 security fix |
| Testcontainers | 1.21.4 | Latest 1.x series |
| Flyway | 11.1.0 | Current in project |

> **Sources**: [PostgreSQL 18 Released](https://www.postgresql.org/about/news/postgresql-18-released-3142/), [pgJDBC Downloads](https://jdbc.postgresql.org/download/), [Testcontainers PostgreSQL](https://mvnrepository.com/artifact/org.testcontainers/postgresql)

---

## Impact Analysis

### Files Requiring Modification

| Category | File Count | Risk Level |
|----------|------------|------------|
| Configuration Files | 7 | Low |
| Maven Dependencies | 2 | Low |
| SQL Migration Files | 1 | Medium |
| Java Entity Files | 4 | Low |
| Docker Configuration | 2 | Low |
| Utility Scripts | 1 | Low |

### SQL Server Features Currently Used

| Feature | SQL Server | PostgreSQL Equivalent | Auto-handled by Hibernate |
|---------|-----------|----------------------|---------------------------|
| NVARCHAR(n) | Unicode string | VARCHAR(n) | Yes |
| NVARCHAR(MAX) | Unlimited Unicode | TEXT | Yes |
| UNIQUEIDENTIFIER | UUID type | UUID | No (manual fix needed) |
| BIT | Boolean | BOOLEAN | Yes |
| DATETIME2 | Timestamp | TIMESTAMP | Yes |
| IDENTITY | Auto-increment | SERIAL/IDENTITY | Yes |

---

## Migration Steps

### Phase 1: Dependency and Configuration Updates

---

#### Step 1: Update Maven Dependencies

**File**: `/Users/igormusic/code/knight2/pom.xml` (Parent POM)

**Changes**:
```xml
<!-- REMOVE -->
<mssql-jdbc.version>12.8.1.jre11</mssql-jdbc.version>

<!-- ADD -->
<postgresql.version>42.7.7</postgresql.version>
<testcontainers.version>1.21.4</testcontainers.version>
```

**File**: `/Users/igormusic/code/knight2/application/pom.xml`

**Changes**:
```xml
<!-- REMOVE -->
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-sqlserver</artifactId>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

**Testcontainers Update**:
```xml
<!-- REMOVE -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mssqlserver</artifactId>
    <scope>test</scope>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

---

#### Step 2: Update Application Configuration

**File**: `/Users/igormusic/code/knight2/application/src/main/resources/application.yml`

**Changes**:
```yaml
# BEFORE (SQL Server)
spring:
  datasource:
    url: jdbc:sqlserver://${DB_HOST:localhost}:${DB_PORT:1433};databaseName=${DB_NAME:knight};encrypt=true;trustServerCertificate=true
    username: ${DB_USER:sa}
    password: ${DB_PASSWORD:YourStrong@Passw0rd}
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver

  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.SQLServerDialect

# AFTER (PostgreSQL)
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:knight}
    username: ${DB_USER:knight}
    password: ${DB_PASSWORD:knight123}
    driver-class-name: org.postgresql.Driver

  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

---

#### Step 3: Update Test Configuration Files

**File**: `/Users/igormusic/code/knight2/application/src/test/resources/application-test.yml`

**Changes**:
```yaml
# Update to PostgreSQL 18 testcontainers configuration
spring:
  datasource:
    url: jdbc:tc:postgresql:18:///knight
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

**File**: `/Users/igormusic/code/knight2/application/src/test/resources/application-e2e.yml`

Apply same changes as above.

---

### Phase 2: Schema Migration

---

#### Step 4: Convert Flyway Migration Script

**File**: `/Users/igormusic/code/knight2/application/src/main/resources/db/migration/V1__initial_schema.sql`

**Data Type Conversions**:

| SQL Server | PostgreSQL | Notes |
|-----------|------------|-------|
| `NVARCHAR(n)` | `VARCHAR(n)` | Direct replacement |
| `NVARCHAR(MAX)` | `TEXT` | For large text fields |
| `UNIQUEIDENTIFIER` | `UUID` | UUID type |
| `BIT` | `BOOLEAN` | Boolean type |
| `DATETIME2` | `TIMESTAMP` | Timestamp type |
| `INT IDENTITY(1,1)` | `SERIAL` or `INT GENERATED ALWAYS AS IDENTITY` | Auto-increment |

**Example Conversions**:

```sql
-- BEFORE (SQL Server)
CREATE TABLE clients (
    client_id NVARCHAR(50) PRIMARY KEY,
    name NVARCHAR(255) NOT NULL,
    is_active BIT DEFAULT 1,
    created_at DATETIME2 DEFAULT GETDATE()
);

-- AFTER (PostgreSQL)
CREATE TABLE clients (
    client_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Index Syntax** (Filtered Indexes):
```sql
-- BEFORE (SQL Server)
CREATE INDEX idx_profiles_status ON profiles(status) WHERE status = 'ACTIVE';

-- AFTER (PostgreSQL) - Same syntax works!
CREATE INDEX idx_profiles_status ON profiles(status) WHERE status = 'ACTIVE';
```

**CHECK Constraints** - No changes needed, same syntax.

---

#### Step 5: Update Java Entity Column Definitions

**Files to modify**:

1. **BatchItemEntity.java**
   - Location: `/Users/igormusic/code/knight2/application/src/main/java/com/knight/application/persistence/batch/entity/BatchItemEntity.java`

```java
// BEFORE
@Column(columnDefinition = "NVARCHAR(MAX)")
private String inputData;

@Column(columnDefinition = "NVARCHAR(MAX)")
private String outputData;

// AFTER
@Column(columnDefinition = "TEXT")
private String inputData;

@Column(columnDefinition = "TEXT")
private String outputData;
```

2. **ServiceEnrollmentEntity.java**
   - Location: `/Users/igormusic/code/knight2/application/src/main/java/com/knight/application/persistence/profiles/entity/ServiceEnrollmentEntity.java`

```java
// BEFORE
@Column(columnDefinition = "UNIQUEIDENTIFIER")
private UUID enrollmentId;

// AFTER
@Column(columnDefinition = "UUID")
private UUID enrollmentId;
```

3. **AccountEnrollmentEntity.java**
   - Location: `/Users/igormusic/code/knight2/application/src/main/java/com/knight/application/persistence/profiles/entity/AccountEnrollmentEntity.java`

```java
// BEFORE
@Column(columnDefinition = "UNIQUEIDENTIFIER")
private UUID enrollmentId;

// AFTER
@Column(columnDefinition = "UUID")
private UUID enrollmentId;
```

4. **ClientEnrollmentEntity.java**
   - Location: `/Users/igormusic/code/knight2/application/src/main/java/com/knight/application/persistence/profiles/entity/ClientEnrollmentEntity.java`

```java
// BEFORE
@Column(columnDefinition = "UNIQUEIDENTIFIER")
private UUID enrollmentId;

// AFTER
@Column(columnDefinition = "UUID")
private UUID enrollmentId;
```

**Alternative**: Remove `columnDefinition` entirely and let Hibernate handle the mapping automatically.

---

### Phase 3: Docker Configuration

---

#### Step 6: Update Docker Compose

**File**: `/Users/igormusic/code/knight2/docker-compose.yml`

**Changes**:

```yaml
# REMOVE SQL Server service
  sqlserver:
    image: mcr.microsoft.com/mssql/server:2022-latest
    container_name: knight2-sqlserver-1
    environment:
      - ACCEPT_EULA=Y
      - MSSQL_SA_PASSWORD=YourStrong@Passw0rd
    ports:
      - "1433:1433"
    volumes:
      - sqlserver-data:/var/opt/mssql
    healthcheck:
      test: /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "YourStrong@Passw0rd" -C -Q "SELECT 1"
      interval: 10s
      timeout: 5s
      retries: 5

  sql-init:
    image: mcr.microsoft.com/mssql-tools18
    depends_on:
      sqlserver:
        condition: service_healthy
    volumes:
      - ./docker/sqlserver:/scripts
    command: /scripts/init-db.sql
    # ...

# ADD PostgreSQL 18 service
  postgres:
    image: postgres:18-alpine
    container_name: knight2-postgres-1
    environment:
      - POSTGRES_USER=knight
      - POSTGRES_PASSWORD=knight123
      - POSTGRES_DB=knight
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./docker/postgres:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U knight -d knight"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - knight-network

# Update volume definition
volumes:
  # REMOVE
  sqlserver-data:
  # ADD
  postgres-data:
```

**Update platform service environment**:
```yaml
  platform:
    # ...
    environment:
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=knight
      - DB_USER=knight
      - DB_PASSWORD=knight123
    depends_on:
      postgres:
        condition: service_healthy
```

---

#### Step 7: Create PostgreSQL Init Script

**File**: `/Users/igormusic/code/knight2/docker/postgres/init-db.sql`

```sql
-- PostgreSQL initialization script
-- The database is already created via POSTGRES_DB environment variable

-- Enable UUID extension (if needed)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Grant permissions (if additional users needed)
-- GRANT ALL PRIVILEGES ON DATABASE knight TO knight;
```

**Remove**: `/Users/igormusic/code/knight2/docker/sqlserver/` directory (or archive it).

---

### Phase 4: Utility Scripts

---

#### Step 8: Convert Utility Scripts

**File**: `/Users/igormusic/code/knight2/scripts/delete_batch.sql`

**BEFORE (T-SQL)**:
```sql
USE knight;
GO

SET QUOTED_IDENTIFIER ON;
GO

BEGIN TRANSACTION;

PRINT 'Deleting batch items...';
DELETE bi FROM batch_items bi
INNER JOIN batches b ON bi.batch_id = b.batch_id
WHERE b.batch_id = @BatchId;

PRINT 'Deleting batch...';
DELETE FROM batches WHERE batch_id = @BatchId;

COMMIT TRANSACTION;
GO
```

**AFTER (PostgreSQL)**:
```sql
-- PostgreSQL batch deletion script
-- Usage: psql -d knight -v batch_id="'your-batch-id'" -f delete_batch.sql

BEGIN;

-- Delete batch items first (foreign key constraint)
DELETE FROM batch_items
WHERE batch_id = :batch_id;

-- Delete the batch
DELETE FROM batches
WHERE batch_id = :batch_id;

COMMIT;
```

---

### Phase 5: Testing and Validation

---

#### Step 9: Update Test Infrastructure

**File**: Any test class using `@Testcontainers`

```java
// BEFORE
@Container
static MSSQLServerContainer<?> sqlServer = new MSSQLServerContainer<>(
        "mcr.microsoft.com/mssql/server:2022-latest")
    .acceptLicense();

@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", sqlServer::getJdbcUrl);
    registry.add("spring.datasource.username", sqlServer::getUsername);
    registry.add("spring.datasource.password", sqlServer::getPassword);
}

// AFTER
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
    .withDatabaseName("knight")
    .withUsername("knight")
    .withPassword("knight123");

@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
}
```

---

#### Step 10: Run Full Test Suite

```bash
# Clean and rebuild
cd /Users/igormusic/code/knight2
./mvnw clean install

# Run all tests
./mvnw test

# Run integration tests
./mvnw verify

# Start Docker environment
docker-compose down -v
docker-compose up -d

# Verify database
docker exec knight2-postgres-1 psql -U knight -d knight -c "SELECT COUNT(*) FROM clients;"
```

---

## Complete File Change List

### Configuration Files
| File | Change Type | Description |
|------|-------------|-------------|
| `application/src/main/resources/application.yml` | Modify | JDBC URL, driver, dialect |
| `application/src/test/resources/application-test.yml` | Modify | Test database config |
| `application/src/test/resources/application-e2e.yml` | Modify | E2E test database config |
| `docker-compose.yml` | Modify | Replace SQL Server with PostgreSQL |

### Maven Files
| File | Change Type | Description |
|------|-------------|-------------|
| `pom.xml` | Modify | Update version properties |
| `application/pom.xml` | Modify | Replace JDBC driver, Flyway module, Testcontainers |

### SQL Files
| File | Change Type | Description |
|------|-------------|-------------|
| `application/src/main/resources/db/migration/V1__initial_schema.sql` | Modify | Convert to PostgreSQL syntax |

### Java Entity Files
| File | Change Type | Description |
|------|-------------|-------------|
| `BatchItemEntity.java` | Modify | NVARCHAR(MAX) -> TEXT |
| `ServiceEnrollmentEntity.java` | Modify | UNIQUEIDENTIFIER -> UUID |
| `AccountEnrollmentEntity.java` | Modify | UNIQUEIDENTIFIER -> UUID |
| `ClientEnrollmentEntity.java` | Modify | UNIQUEIDENTIFIER -> UUID |

### Docker Files
| File | Change Type | Description |
|------|-------------|-------------|
| `docker/postgres/init-db.sql` | Create | PostgreSQL init script |
| `docker/sqlserver/` | Delete | No longer needed |

### Utility Scripts
| File | Change Type | Description |
|------|-------------|-------------|
| `scripts/delete_batch.sql` | Modify | Convert T-SQL to PostgreSQL |

---

## Rollback Plan

If issues arise during migration:

1. **Revert Git changes**: `git checkout .`
2. **Restore Docker volumes**: Keep SQL Server volume backup
3. **Database backup**: Before migration, run:
   ```bash
   docker exec knight2-sqlserver-1 /opt/mssql-tools18/bin/sqlcmd \
     -S localhost -U sa -P "YourStrong@Passw0rd" -C \
     -Q "BACKUP DATABASE knight TO DISK = '/var/opt/mssql/backup/knight.bak'"
   ```

---

## Verification Checklist

- [ ] Maven build succeeds: `./mvnw clean install`
- [ ] All unit tests pass: `./mvnw test`
- [ ] All integration tests pass with PostgreSQL container
- [ ] Docker Compose starts successfully
- [ ] Flyway migrations run without errors
- [ ] Application starts and responds to health checks
- [ ] All API endpoints function correctly
- [ ] Test data generation works
- [ ] Portal and Platform services connect to PostgreSQL

---

## Benefits of PostgreSQL 18

| Aspect | SQL Server 2022 | PostgreSQL 18 |
|--------|-----------------|---------------|
| Licensing | Commercial (paid) | Open Source (free) |
| Docker Image Size | ~1.5GB | ~80MB (Alpine) |
| ARM Support | Limited (Rosetta) | Native |
| Startup Time | ~30s | ~5s |
| Memory Usage | High | Lower |
| JSON Support | Good | Excellent |
| Community | Enterprise | Large OSS community |
| I/O Performance | Standard | 2-3x faster with AIO |
| UUID Generation | NEWID() | Native `uuidv7()` |
| OAuth Support | External | Native in v18 |

### PostgreSQL 18 Specific Benefits

1. **Asynchronous I/O**: Major performance boost for read-heavy workloads
2. **UUIDv7**: Timestamp-ordered UUIDs for better index performance
3. **Virtual Generated Columns**: Compute values at read time, reducing storage
4. **Faster Major Upgrades**: Statistics preserved through upgrades
5. **Wire Protocol 3.2**: Modern protocol for improved client communication

---

## Dependency Version Summary

```xml
<!-- Parent POM versions -->
<properties>
    <postgresql.version>42.7.7</postgresql.version>
    <testcontainers.version>1.21.4</testcontainers.version>
    <flyway.version>11.1.0</flyway.version>
</properties>
```

| Dependency | Artifact | Version |
|------------|----------|---------|
| PostgreSQL JDBC | `org.postgresql:postgresql` | 42.7.7 |
| Testcontainers BOM | `org.testcontainers:testcontainers-bom` | 1.21.4 |
| Testcontainers PostgreSQL | `org.testcontainers:postgresql` | 1.21.4 |
| Flyway Core | `org.flywaydb:flyway-core` | 11.1.0 |
| Docker Image | `postgres:18-alpine` | 18.1 |

---

*Document Version: 1.1*
*Updated: 2025-12-23*
*Author: Claude Code Assistant*

**Sources:**
- [PostgreSQL 18 Released](https://www.postgresql.org/about/news/postgresql-18-released-3142/)
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/download/)
- [Testcontainers PostgreSQL](https://mvnrepository.com/artifact/org.testcontainers/postgresql)
