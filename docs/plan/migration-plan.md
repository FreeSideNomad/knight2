# Knight Platform: Micronaut to Spring Boot Migration Plan

## Executive Summary

This document outlines a detailed 20-step migration plan to migrate the Knight Platform from **Micronaut 4.9.3** to **Spring Boot 3.5.8** (latest). The migration will establish a multi-module Maven project structure using Spring Boot Initializr as the foundation, with a **unified single API** consolidating all bounded contexts.

### Current State
- **Framework**: Micronaut 4.9.3
- **Java Version**: 17
- **Build System**: Maven (multi-module with 28 modules)
- **Database**: PostgreSQL 16 with Flyway migrations
- **Messaging**: Apache Kafka 3.6.0
- **Architecture**: DDD Modular Monolith with 5 separate Bounded Contexts (each with own API)
- **Layer Enforcement**: ArchUnit tests per bounded context (not Spring Modulith)

### Target State
- **Framework**: Spring Boot 3.5.8 (Latest)
- **Java Version**: 17 (LTS)
- **Build System**: Maven (multi-module with parent POM)
- **Database**: SQL Server with Flyway migrations
- **Messaging**: Spring Kafka
- **Architecture**: DDD Modular Monolith with **Single Unified API**
- **Layer Enforcement**: ArchUnit with centralized architecture tests

### Key Architectural Change

The original architecture has separate deployable services per bounded context. The new architecture consolidates into a **single Spring Boot application** with:
- One unified API module exposing all REST endpoints
- Domain modules per bounded context (isolated business logic)
- Shared infrastructure module
- ArchUnit enforcement of layer boundaries

---

## Original vs Target Architecture

### Original (Micronaut) - Separate Services
```
contexts/
├── service-profiles/
│   ├── management/          → Port 9500 (separate service)
│   └── indirect-clients/    → Port 9501 (separate service)
├── users/
│   ├── users/               → Port 9502 (separate service)
│   └── policy/              → Port 9503 (separate service)
└── approval-workflows/
    └── engine/              → Port 9504 (separate service)
```

### Target (Spring Boot) - Single Unified Application
```
knight-platform/
├── pom.xml                           # Root aggregator
├── platform/
│   └── shared-kernel/                # Shared value objects
├── knight-api/                       # SINGLE unified REST API (Port 8080)
├── knight-services/                  # Application services (orchestration)
├── knight-domain/                    # All domain modules
│   ├── service-profiles/             # Service profile aggregates
│   ├── indirect-clients/             # Indirect client aggregates
│   ├── users/                        # User aggregates
│   ├── policy/                       # Policy aggregates
│   └── approval-workflows/           # Approval workflow aggregates
└── knight-application/               # Spring Boot Application (deployable JAR)
```

---

## Modules to Migrate

### Will Migrate (Consolidated)
| Original Module(s) | Target Module | Description |
|-------------------|---------------|-------------|
| `platform/shared-kernel` | `platform/shared-kernel` | Shared value objects (unchanged) |
| All `*-api` modules | `knight-api` | Unified REST API contracts |
| All `*-domain` modules | `knight-domain/*` | Domain logic per bounded context |
| All `*-app` modules | `knight-services` | Application services |
| All `*-infra` modules | `knight-application` | Single Spring Boot application (deployable JAR) |

### Will NOT Migrate (Out of Scope)
| Component | Reason |
|-----------|--------|
| `e2e-tests/` | Python-based, framework independent |
| `model/` | YAML specifications, no code changes needed |
| `domains/` | Reference examples only |
| `docker/` | Infrastructure config, update for single service |
| `bff/web` | Replaced by unified API |

---

## Original Layer Enforcement (ArchUnit)

The original codebase uses **ArchUnit** (not Spring Modulith) to enforce DDD layer rules:

```java
// Original: DddArchitectureTest.java (per bounded context)
@Test
void layeredArchitectureShouldBeRespected() {
    ArchRule rule = layeredArchitecture()
        .consideringAllDependencies()
        .layer("API").definedBy("..api..")
        .layer("Domain").definedBy("..domain..")
        .layer("Application").definedBy("..app..")
        .layer("Infrastructure").definedBy("..infra..")
        .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Infrastructure")
        .whereLayer("Application").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Domain", "Application", "Infrastructure")
        .whereLayer("API").mayOnlyBeAccessedByLayers("API", "Domain", "Application", "Infrastructure");

    rule.check(importedClasses);
}

@Test
void domainShouldNotDependOnInfrastructure() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("..infra..");

    rule.check(importedClasses);
}

@Test
void apiShouldNotDependOnDomainOrAppOrInfra() {
    ArchRule rule = noClasses()
        .that().resideInAPackage("..api..")
        .should().dependOnClassesThat().resideInAnyPackage("..domain..", "..app..", "..infra..");

    rule.check(importedClasses);
}
```

---

## Migration Steps

### Phase 1: Foundation Setup (Steps 1-5)

---

### Step 1: Create Spring Boot Parent POM via Initializr

**Objective**: Generate base Spring Boot project structure using Spring Initializr

**Actions**:
1. Go to [https://start.spring.io](https://start.spring.io)
2. Configure project settings:
   - **Project**: Maven
   - **Language**: Java
   - **Spring Boot**: 3.5.8 (latest stable)
   - **Group**: `com.knight`
   - **Artifact**: `knight-platform`
   - **Name**: Knight Platform
   - **Description**: DDD Modular Monolith for Commercial Banking
   - **Package name**: `com.knight`
   - **Packaging**: JAR
   - **Java**: 17

3. Select Dependencies:
   - Spring Web
   - Spring Data JPA
   - MS SQL Server Driver
   - Flyway Migration
   - Spring for Apache Kafka
   - Lombok
   - Spring Boot Actuator
   - Validation

4. Generate and download the project
5. Extract parent POM structure to use as template

**Deliverables**:
- Base `pom.xml` with Spring Boot parent
- Dependency management section
- Plugin management section

**Micronaut to Spring Boot Mapping**:
```
Micronaut                    → Spring Boot
─────────────────────────────────────────────
micronaut-bom 4.9.3         → spring-boot-starter-parent 3.5.8
micronaut-http-server-netty → spring-boot-starter-web
micronaut-data-hibernate-jpa→ spring-boot-starter-data-jpa
micronaut-jdbc-hikari       → spring-boot-starter-jdbc (HikariCP included)
micronaut-kafka             → spring-kafka
micronaut-serde-jackson     → (Jackson included in spring-web)
micronaut-flyway            → flyway-core + flyway-database-sqlserver
micronaut-inject            → spring-context (auto)
```

---

### Step 2: Design Unified Multi-Module Maven Structure

**Objective**: Define the consolidated multi-module project hierarchy

**Target Structure**:
```
knight-platform/
├── pom.xml                              # Root aggregator + dependency management
│
├── platform/
│   ├── pom.xml                          # Platform modules aggregator
│   └── shared-kernel/
│       └── pom.xml                      # Shared value objects (no dependencies)
│
├── knight-api/
│   └── pom.xml                          # Unified API contracts (DTOs, interfaces)
│
├── knight-domain/
│   ├── pom.xml                          # Domain aggregator
│   ├── service-profiles/
│   │   └── pom.xml                      # Service profile domain logic
│   ├── indirect-clients/
│   │   └── pom.xml                      # Indirect clients domain logic
│   ├── users/
│   │   └── pom.xml                      # Users domain logic
│   ├── policy/
│   │   └── pom.xml                      # Policy domain logic
│   └── approval-workflows/
│       └── pom.xml                      # Approval workflows domain logic
│
├── knight-services/
│   └── pom.xml                          # Application services (orchestration)
│
└── knight-application/
    └── pom.xml                          # Spring Boot Application (deployable JAR)
```

**Module Responsibilities**:

| Module | Purpose | Dependencies |
|--------|---------|--------------|
| `shared-kernel` | Value objects, IDs | None (pure Java) |
| `knight-api` | REST contracts, DTOs, command/query interfaces | `shared-kernel` |
| `knight-domain/*` | Aggregates, entities, domain services | `shared-kernel`, `knight-api` |
| `knight-services` | Application services, @Transactional boundaries | `knight-domain/*`, `knight-api` |
| `knight-application` | REST controllers, JPA, Kafka, Spring Boot main | All modules |

---

### Step 3: Create Root POM with Spring Boot Parent

**Objective**: Implement the root POM with all dependency and plugin management

**Root POM** (`pom.xml`):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.8</version>
        <relativePath/>
    </parent>

    <groupId>com.knight</groupId>
    <artifactId>knight-platform</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Knight Platform</name>
    <description>DDD Modular Monolith for Commercial Banking</description>

    <modules>
        <module>platform</module>
        <module>knight-api</module>
        <module>knight-domain</module>
        <module>knight-services</module>
        <module>knight-application</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.5.8</spring-boot.version>
        <mssql-jdbc.version>12.8.1.jre11</mssql-jdbc.version>
        <flyway.version>11.1.0</flyway.version>
        <mapstruct.version>1.6.3</mapstruct.version>
        <lombok.version>1.18.36</lombok.version>
        <archunit.version>1.4.1</archunit.version>
        <testcontainers.version>1.20.4</testcontainers.version>
        <awaitility.version>4.2.2</awaitility.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Testcontainers BOM -->
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Internal modules -->
            <dependency>
                <groupId>com.knight.platform</groupId>
                <artifactId>shared-kernel</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.knight</groupId>
                <artifactId>knight-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.knight</groupId>
                <artifactId>knight-services</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- Domain modules -->
            <dependency>
                <groupId>com.knight.domain</groupId>
                <artifactId>service-profiles</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.knight.domain</groupId>
                <artifactId>indirect-clients</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.knight.domain</groupId>
                <artifactId>users</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.knight.domain</groupId>
                <artifactId>policy</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.knight.domain</groupId>
                <artifactId>approval-workflows</artifactId>
                <version>${project.version}</version>
            </dependency>

            <!-- MapStruct -->
            <dependency>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct</artifactId>
                <version>${mapstruct.version}</version>
            </dependency>

            <!-- ArchUnit -->
            <dependency>
                <groupId>com.tngtech.archunit</groupId>
                <artifactId>archunit-junit5</artifactId>
                <version>${archunit.version}</version>
                <scope>test</scope>
            </dependency>

            <!-- Awaitility -->
            <dependency>
                <groupId>org.awaitility</groupId>
                <artifactId>awaitility</artifactId>
                <version>${awaitility.version}</version>
                <scope>test</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <configuration>
                        <excludes>
                            <exclude>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                            </exclude>
                        </excludes>
                    </configuration>
                </plugin>

                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <source>${java.version}</source>
                        <target>${java.version}</target>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                                <version>${lombok.version}</version>
                            </path>
                            <path>
                                <groupId>org.mapstruct</groupId>
                                <artifactId>mapstruct-processor</artifactId>
                                <version>${mapstruct.version}</version>
                            </path>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok-mapstruct-binding</artifactId>
                                <version>0.2.0</version>
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>

                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <version>0.8.12</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

---

### Step 4: Migrate Shared Kernel Module

**Objective**: Migrate the `shared-kernel` module (foundation for all bounded contexts)

**No code changes required** - pure Java value objects with no framework dependencies.

**POM** (`platform/shared-kernel/pom.xml`):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.knight</groupId>
        <artifactId>platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <groupId>com.knight.platform</groupId>
    <artifactId>shared-kernel</artifactId>
    <name>Shared Kernel</name>
    <description>Cross-context value objects and shared types</description>

    <dependencies>
        <!-- Test only -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

**Value Objects to copy**:
- `ClientId`
- `ServicingProfileId`
- `OnlineProfileId`
- `IndirectProfileId`
- `UserId`
- `UserGroupId`
- `BankClientId`
- `IndirectClientId`

---

### Step 5: Create Module POM Templates

**Objective**: Create POM files for each layer module

#### 5.1 API Module (`knight-api/pom.xml`)
```xml
<dependencies>
    <dependency>
        <groupId>com.knight.platform</groupId>
        <artifactId>shared-kernel</artifactId>
    </dependency>
    <!-- No Spring dependencies - pure contracts -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### 5.2 Domain Module Example (`knight-domain/service-profiles/pom.xml`)
```xml
<dependencies>
    <dependency>
        <groupId>com.knight.platform</groupId>
        <artifactId>shared-kernel</artifactId>
    </dependency>
    <dependency>
        <groupId>com.knight</groupId>
        <artifactId>knight-api</artifactId>
    </dependency>
    <!-- No Spring dependencies - pure domain logic -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### 5.3 Services Module (`knight-services/pom.xml`)
```xml
<dependencies>
    <!-- API -->
    <dependency>
        <groupId>com.knight</groupId>
        <artifactId>knight-api</artifactId>
    </dependency>

    <!-- All domain modules -->
    <dependency>
        <groupId>com.knight.domain</groupId>
        <artifactId>service-profiles</artifactId>
    </dependency>
    <dependency>
        <groupId>com.knight.domain</groupId>
        <artifactId>indirect-clients</artifactId>
    </dependency>
    <dependency>
        <groupId>com.knight.domain</groupId>
        <artifactId>users</artifactId>
    </dependency>
    <dependency>
        <groupId>com.knight.domain</groupId>
        <artifactId>policy</artifactId>
    </dependency>
    <dependency>
        <groupId>com.knight.domain</groupId>
        <artifactId>approval-workflows</artifactId>
    </dependency>

    <!-- Spring (minimal) -->
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-tx</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework</groupId>
        <artifactId>spring-context</artifactId>
    </dependency>
</dependencies>
```

#### 5.4 Application Module (`knight-application/pom.xml`)
```xml
<dependencies>
    <!-- Internal modules -->
    <dependency>
        <groupId>com.knight</groupId>
        <artifactId>knight-services</artifactId>
    </dependency>
    <dependency>
        <groupId>com.knight</groupId>
        <artifactId>knight-api</artifactId>
    </dependency>

    <!-- Spring Boot starters -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Database -->
    <dependency>
        <groupId>com.microsoft.sqlserver</groupId>
        <artifactId>mssql-jdbc</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-sqlserver</artifactId>
    </dependency>

    <!-- Development -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mssqlserver</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>kafka</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.tngtech.archunit</groupId>
        <artifactId>archunit-junit5</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

---

### Phase 2: Unified API Migration (Steps 6-10)

---

### Step 6: Create Unified API Module

**Objective**: Consolidate all API contracts into single `knight-api` module

**Package Structure** (`knight-api/src/main/java/com/knight/api/`):
```
com.knight.api/
├── serviceprofiles/
│   ├── commands/
│   │   └── ServiceProfileCommands.java
│   └── queries/
│       └── ServiceProfileQueries.java
├── indirectclients/
│   ├── commands/
│   │   └── IndirectClientCommands.java
│   └── queries/
│       └── IndirectClientQueries.java
├── users/
│   ├── commands/
│   │   └── UserCommands.java
│   └── queries/
│       └── UserQueries.java
├── policy/
│   ├── commands/
│   │   └── PolicyCommands.java
│   └── queries/
│       └── PolicyQueries.java
└── approvalworkflows/
    ├── commands/
    │   └── ApprovalWorkflowCommands.java
    └── queries/
        └── ApprovalWorkflowQueries.java
```

**Migration Actions**:
1. Copy all command/query interfaces from original `*-api` modules
2. Remove Micronaut `@Serdeable` annotations (Jackson handles records automatically)
3. Organize by bounded context package

**Example** - Before (Micronaut):
```java
@Serdeable
public record CreateProfileRequest(String clientUrn, String createdBy) {}
```

**After** (Spring Boot):
```java
public record CreateProfileRequest(String clientUrn, String createdBy) {}
```

---

### Step 7: Create Domain Modules

**Objective**: Create separate domain modules per bounded context

**Package Structure**:
```
knight-domain/
├── service-profiles/src/main/java/com/knight/domain/serviceprofiles/
│   ├── aggregate/
│   │   └── ServicingProfile.java
│   ├── entity/
│   │   ├── ServiceEnrollment.java
│   │   └── AccountEnrollment.java
│   ├── valueobject/
│   └── service/
│
├── indirect-clients/src/main/java/com/knight/domain/indirectclients/
│   ├── aggregate/
│   │   └── IndirectClient.java
│   └── ...
│
├── users/src/main/java/com/knight/domain/users/
│   ├── aggregate/
│   │   └── User.java
│   └── ...
│
├── policy/src/main/java/com/knight/domain/policy/
│   ├── aggregate/
│   │   └── PermissionStatement.java
│   └── ...
│
└── approval-workflows/src/main/java/com/knight/domain/approvalworkflows/
    ├── aggregate/
    │   └── ApprovalWorkflow.java
    └── ...
```

**Migration Actions**:
1. Copy domain classes from original modules
2. No code changes expected (pure Java)
3. Update package names to new structure

---

### Step 8: Create Services Module

**Objective**: Consolidate all application services

**Package Structure** (`knight-services/src/main/java/com/knight/services/`):
```
com.knight.services/
├── serviceprofiles/
│   └── ServiceProfileService.java
├── indirectclients/
│   └── IndirectClientService.java
├── users/
│   └── UserService.java
├── policy/
│   └── PolicyService.java
└── approvalworkflows/
    └── ApprovalWorkflowService.java
```

**Migration Actions**:

1. **Update annotations**:
```java
// Before (Micronaut)
@Singleton
public class ServiceProfileService implements ServiceProfileCommands {
    @Inject
    private ServicingProfileRepository repository;
}

// After (Spring)
@Service
@RequiredArgsConstructor
public class ServiceProfileService implements ServiceProfileCommands {
    private final ServicingProfileRepository repository;
}
```

2. **Update transaction annotation**:
```java
// Before (Jakarta)
import jakarta.transaction.Transactional;

// After (Spring - preferred)
import org.springframework.transaction.annotation.Transactional;
```

**Annotation Mapping**:
```
Micronaut              → Spring
────────────────────────────────
@Singleton             → @Service
@Inject                → Constructor injection (via @RequiredArgsConstructor)
@Named                 → @Qualifier
@Transactional         → @Transactional (Spring version)
```

---

### Step 9: Create Application Module

**Objective**: Create single Spring Boot application with all infrastructure

**Package Structure** (`knight-application/src/main/java/com/knight/application/`):
```
com.knight.application/
├── KnightApplication.java                    # Spring Boot main
├── config/
│   ├── JpaConfig.java
│   ├── KafkaConfig.java
│   └── WebConfig.java
├── rest/
│   ├── serviceprofiles/
│   │   └── ServiceProfileController.java
│   ├── indirectclients/
│   │   └── IndirectClientController.java
│   ├── users/
│   │   └── UserController.java
│   ├── policy/
│   │   └── PolicyController.java
│   └── approvalworkflows/
│       └── ApprovalWorkflowController.java
├── persistence/
│   ├── serviceprofiles/
│   │   ├── entity/
│   │   ├── repository/
│   │   └── mapper/
│   ├── indirectclients/
│   ├── users/
│   ├── policy/
│   └── approvalworkflows/
└── messaging/
    ├── producer/
    └── consumer/
```

#### 9.1 Application Entry Point

```java
@SpringBootApplication
public class KnightApplication {
    public static void main(String[] args) {
        SpringApplication.run(KnightApplication.class, args);
    }
}
```

#### 9.2 REST Controller Migration

```java
// Before (Micronaut)
@Controller("/commands/service-profiles/servicing")
@ExecuteOn(TaskExecutors.BLOCKING)
public class SpmCommandController {
    @Inject
    SpmCommands commands;

    @Post("/create")
    public CreateProfileResult createProfile(@Body CreateProfileRequest req) {
        // ...
    }
}

// After (Spring Boot)
@RestController
@RequestMapping("/api/v1/service-profiles")
@RequiredArgsConstructor
public class ServiceProfileController {

    private final ServiceProfileCommands commands;

    @PostMapping
    public ResponseEntity<CreateProfileResult> createProfile(
            @RequestBody @Valid CreateProfileRequest req) {
        return ResponseEntity.ok(commands.createProfile(req));
    }
}
```

#### 9.3 Repository Migration

```java
// Before (Micronaut Data)
@Repository
public interface ServicingProfileRepository
    extends CrudRepository<ServicingProfileJpaEntity, String> {
}

// After (Spring Data JPA)
@Repository
public interface ServicingProfileRepository
    extends JpaRepository<ServicingProfileJpaEntity, String> {
}
```

#### 9.4 Kafka Migration

```java
// Before (Micronaut Kafka)
@KafkaClient
public interface EventProducer {
    @Topic("events.profile-created")
    void send(@KafkaKey String key, ProfileCreatedEvent event);
}

// After (Spring Kafka)
@Component
@RequiredArgsConstructor
public class EventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(String key, ProfileCreatedEvent event) {
        kafkaTemplate.send("events.profile-created", key, event);
    }
}
```

---

### Step 10: Configure Unified Application

**Objective**: Create Spring Boot configuration for single application

**application.yml**:
```yaml
spring:
  application:
    name: knight-platform

  datasource:
    url: jdbc:sqlserver://${DB_HOST:localhost}:${DB_PORT:1433};databaseName=${DB_NAME:knight};encrypt=true;trustServerCertificate=true
    username: ${DB_USER:sa}
    password: ${DB_PASSWORD:YourStrong@Passw0rd}
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: none
    properties:
      hibernate:
        dialect: org.hibernate.dialect.SQLServerDialect
        jdbc:
          batch_size: 20
    open-in-view: false

  flyway:
    enabled: true
    locations: classpath:db/migration
    # Multiple schemas for bounded contexts
    schemas: service_profiles,indirect_clients,users,policy,approval_workflows

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: knight-platform
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.knight.*

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.knight: DEBUG
    org.springframework: INFO
    org.hibernate.SQL: DEBUG
```

---

### Phase 3: Domain Module Migration (Steps 11-15)

---

### Step 11: Migrate Service Profiles Domain

**Location**: `knight-domain/service-profiles/`

**Actions**:
1. Copy `ServicingProfile` aggregate and related classes
2. Update package names
3. No framework dependencies (pure Java)
4. Run domain tests

---

### Step 12: Migrate Indirect Clients Domain

**Location**: `knight-domain/indirect-clients/`

**Actions**:
1. Copy `IndirectClient` aggregate and related classes
2. Update package names
3. Run domain tests

---

### Step 13: Migrate Users Domain

**Location**: `knight-domain/users/`

**Actions**:
1. Copy `User` aggregate and related classes
2. Update package names
3. Run domain tests

---

### Step 14: Migrate Policy Domain

**Location**: `knight-domain/policy/`

**Actions**:
1. Copy `PermissionStatement`, `ApprovalStatement` and related classes
2. Update package names
3. Run domain tests

---

### Step 15: Migrate Approval Workflows Domain

**Location**: `knight-domain/approval-workflows/`

**Actions**:
1. Copy `ApprovalWorkflow` aggregate and related classes
2. Update package names
3. Run domain tests

---

### Phase 4: Testing & Architecture Enforcement (Steps 16-18)

---

### Step 16: Create Centralized ArchUnit Tests

**Objective**: Single architecture test class enforcing DDD rules across all modules

**Location**: `knight-application/src/test/java/com/knight/application/archunit/`

```java
package com.knight.application.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.knight",
                importOptions = ImportOption.DoNotIncludeTests.class)
public class DddArchitectureTest {

    @ArchTest
    static final ArchRule layered_architecture = layeredArchitecture()
        .consideringAllDependencies()
        .layer("API").definedBy("com.knight.api..")
        .layer("Domain").definedBy("com.knight.domain..")
        .layer("Services").definedBy("com.knight.services..")
        .layer("Application").definedBy("com.knight.application..")

        .whereLayer("API").mayNotAccessAnyLayer()
        .whereLayer("Domain").mayOnlyAccessLayers("API")
        .whereLayer("Services").mayOnlyAccessLayers("Domain", "API")
        .whereLayer("Application").mayOnlyAccessLayers("Services", "Domain", "API");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring =
        noClasses()
            .that().resideInAPackage("com.knight.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule api_should_not_depend_on_spring =
        noClasses()
            .that().resideInAPackage("com.knight.api..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_application =
        noClasses()
            .that().resideInAPackage("com.knight.domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.knight.application..");

    @ArchTest
    static final ArchRule api_should_not_depend_on_other_layers =
        noClasses()
            .that().resideInAPackage("com.knight.api..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.knight.domain..", "com.knight.services..", "com.knight.application..");

    @ArchTest
    static final ArchRule bounded_contexts_should_not_depend_on_each_other =
        noClasses()
            .that().resideInAPackage("com.knight.domain.serviceprofiles..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "com.knight.domain.indirectclients..",
                "com.knight.domain.users..",
                "com.knight.domain.policy..",
                "com.knight.domain.approvalworkflows.."
            );
}
```

---

### Step 17: Create Integration Tests

**Objective**: Set up integration tests with Testcontainers

```java
@SpringBootTest
@Testcontainers
class ServiceProfileIntegrationTest {

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

    @Autowired
    private ServiceProfileCommands commands;

    @Test
    void shouldCreateServiceProfile() {
        // Given
        var request = new CreateProfileRequest("urn:client:123", "user@example.com");

        // When
        var result = commands.createProfile(request);

        // Then
        assertThat(result.profileId()).isNotNull();
    }
}
```

---

### Step 18: Verify E2E Tests Pass

**Objective**: Update and run E2E tests against unified application

**Actions**:
1. Update E2E test configuration for single endpoint (port 8080)
2. Update API paths if changed
3. Run test suite:

```bash
cd e2e-tests
./run.sh
```

---

### Phase 5: Finalization (Steps 19-20)

---

### Step 19: Update Documentation and Configuration

**Objective**: Update all documentation for new architecture

**Files to Update**:

1. **README.md** - New architecture diagram
2. **docker-compose.yml** - Single service instead of multiple
3. **Startup scripts** - Simplified for single application

**New docker-compose.yml**:
```yaml
services:
  knight-platform:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DB_HOST=sqlserver
      - DB_PORT=1433
      - DB_NAME=knight
      - DB_USER=sa
      - DB_PASSWORD=YourStrong@Passw0rd
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
    depends_on:
      - sqlserver
      - kafka

  sqlserver:
    image: mcr.microsoft.com/mssql/server:2022-latest
    ports:
      - "1433:1433"
    environment:
      - ACCEPT_EULA=Y
      - SA_PASSWORD=YourStrong@Passw0rd

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    # ... kafka configuration
```

---

### Step 20: Final Validation and Cleanup

**Objective**: Comprehensive validation

**Checklist**:

- [ ] All modules compile: `mvn clean install`
- [ ] All unit tests pass: `mvn test`
- [ ] Integration tests pass
- [ ] ArchUnit architecture tests pass
- [ ] E2E tests pass
- [ ] Application starts on port 8080
- [ ] All REST endpoints accessible
- [ ] Database migrations run successfully
- [ ] Kafka events work correctly
- [ ] Actuator health endpoint responds
- [ ] Remove all Micronaut dependencies

**Final Build Command**:
```bash
mvn clean verify
```

---

## API Endpoint Mapping

### Original (Multiple Services)
| Service | Port | Endpoint |
|---------|------|----------|
| Service Profile Management | 9500 | `/commands/service-profiles/servicing/*` |
| Indirect Clients | 9501 | `/commands/service-profiles/indirect/*` |
| Users | 9502 | `/commands/users/*` |
| Policy | 9503 | `/commands/policy/*` |
| Approval Workflows | 9504 | `/commands/approval-workflows/*` |

### Target (Unified API)
| Endpoint | Description |
|----------|-------------|
| `GET/POST /api/v1/service-profiles` | Service profile operations |
| `GET/POST /api/v1/indirect-clients` | Indirect client operations |
| `GET/POST /api/v1/users` | User operations |
| `GET/POST /api/v1/policies` | Policy operations |
| `GET/POST /api/v1/approval-workflows` | Approval workflow operations |

---

## Dependency Version Reference

| Dependency | Current (Micronaut) | Target (Spring Boot 3.5.8) |
|------------|---------------------|----------------------------|
| Framework | Micronaut 4.9.3 | Spring Boot 3.5.8 |
| Java | 17 | 17 |
| Database Driver | PostgreSQL 42.7.5 | MS SQL Server 12.8.1.jre11 |
| Flyway | 11.3.3 | 11.1.0 |
| Kafka | 3.6.0 | 3.9.0 (via Spring Kafka 3.3.x) |
| Jackson | 2.18.2 | 2.18.x (via Spring Boot) |
| JUnit | 5.11.4 | 5.11.x (via Spring Boot) |
| Testcontainers | 1.20.4 | 1.20.4 |
| Lombok | 1.18.40 | 1.18.36 |
| MapStruct | 1.6.3 | 1.6.3 |
| ArchUnit | 1.4.1 | 1.4.1 |
| HikariCP | (Micronaut default) | 6.2.x (via Spring Boot) |

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| API path changes | High | Document mapping, update E2E tests |
| Single point of failure | Medium | Proper health checks, horizontal scaling |
| Transaction scope changes | Medium | Thorough integration testing |
| Kafka serialization | Medium | Use Spring's JsonSerializer |
| Migration complexity | Medium | Phased approach, validate each step |

---

## Success Criteria

1. **Build Success**: All modules compile with `mvn clean install`
2. **Test Success**: All unit, integration, and architecture tests pass
3. **E2E Success**: Updated E2E tests pass
4. **Single Deployment**: One JAR, one port (8080)
5. **Layer Enforcement**: ArchUnit tests enforce DDD rules
6. **Performance**: Startup time < 30s
7. **Observability**: Health, metrics, and logging functional

---

## Summary of Key Changes

| Aspect | Original | Target |
|--------|----------|--------|
| Deployable Units | 6 services (ports 8080, 9500-9504) | 1 service (port 8080) |
| Maven Modules | 28 | 11 |
| API Modules | 5 separate | 1 unified |
| Layer Enforcement | ArchUnit per BC | Centralized ArchUnit |
| Database | PostgreSQL | SQL Server |
| Framework | Micronaut 4.9.3 | Spring Boot 3.5.8 |

---

*Document Version: 2.0*
*Created: 2025-12-09*
*Author: Claude Code Assistant*
