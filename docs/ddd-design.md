# DDD Modulith Architecture for Spring Boot (Maven Multi-Module) — Design Document

## 1. Scope and Goals

This document defines an opinionated, implementation-ready architecture for a **Domain-Driven Design (DDD) modulith** built with **Spring Boot** and **Maven multi-module** packaging.

### Goals
- Package each bounded context as a **domain library module** with an explicit public API.
- Provide a single **deployable Spring Boot application module** that binds infrastructure:
  - JPA persistence
  - in-JVM and/or Kafka messaging
  - REST API ingress
- Enforce **bounded context isolation** with automated rules (ArchUnit).
- Support:
  - deterministic audit and crash recovery via DB-backed messaging (optional but recommended)
  - evolution path to microservices (swap transport/adapters, keep domain and API stable)

### Non-Goals
- This is not a domain model document; it describes the platform architecture pattern.
- It does not prescribe a particular UI or authentication solution.

---

## 2. High-Level Architecture

### 2.1 Layers (Clean / Hexagonal)
Each bounded context follows hexagonal architecture:

- **Domain layer** (pure Java):
  - aggregates, entities, value objects
  - domain services
  - repository interfaces (ports)
  - published API types (commands/queries/events DTOs) if you choose contract-first
- **Application layer** (orchestration, still domain-centric):
  - transactional command execution
  - idempotency / deduplication
  - mapping between DTOs and domain types
  - persistence coordination
  - event emission to outbox / event bus
- **Adapters / Infrastructure** (Spring Boot application module):
  - REST controllers
  - Kafka consumers/producers
  - JPA repositories and entities
  - outbox publisher, recovery runners
  - integration clients (Okta, email, etc.)

### 2.2 Deployment Model
- Single Spring Boot deployable (“modulith”)
- Multiple Maven modules:
  - many **domain modules** (one per bounded context)
  - one **application module** (Spring Boot)
  - optional **shared kernel** module for cross-domain value types (kept minimal)

---

## 3. Maven Multi-Module Structure (Opinionated)

Recommended root structure:

```
/pom.xml
/shared-kernel/
/domain/
  /users/
  /identity/
  /profiles/
  /approvals/
  ...
/application/
```

### 3.1 Domain module responsibilities
Each `/domain/{bc}` builds a Java library (jar) containing:
- the domain model
- published public API for that BC (commands/queries/events records)
- ports (repository interfaces, integration ports if you need them)

Domain modules must not depend on Spring Boot.

### 3.2 Application module responsibilities
`/application` is the single Spring Boot app that:
- depends on domain modules
- provides infrastructure adapters:
  - JPA entities + Spring Data repos implementing domain repository interfaces
  - Kafka/JVM messaging adapters
  - REST controllers

---

## 4. Package Structure per Bounded Context (Contract-First)

For each domain module (example base package: `com.knight.domain.{context}`):

```
domain/{module}/src/main/java/com/knight/domain/{context}/
├── aggregate/           # Rich aggregate root with nested entities
├── repository/          # Repository interfaces (ports)
├── service/             # Domain services (pure business logic)
└── api/
    ├── commands/        # Command DTO records
    ├── queries/         # Query DTO + response DTO records
    └── events/          # Event DTO records (published)
```

### Notes
- `api/*` is the **published contract** of the module.
- Domain services may depend on repository interfaces when required for invariants (within BC). Keep orchestration (tx, idempotency, outbox) outside domain services.
- Repositories in domain modules are interfaces only.

---

## 5. Shared Kernel (Minimal and Strict)

The shared kernel should contain only stable, cross-domain primitives:
- strongly typed IDs (aggregate identifiers)
- generic domain primitives (`CorrelationId`, `CommandId`, `EventId`, `TenantId`)
- maybe `Money` if truly universal

Rules:
- shared kernel must not depend on any domain module
- domain modules may depend on shared kernel
- avoid putting domain concepts in shared kernel (prevents coupling)

---

## 6. Communication Model: Commands, Queries, Events

### 6.1 Commands (async by default)
- Represent intent: imperative requests to perform business actions.
- Delivered via:
  - in-memory bus (internal modulith)
  - Kafka (external and/or later microservices)
  - REST ingress translating to internal command

### 6.2 Events (async facts)
- Represent facts: past tense, immutable.
- Delivered internally via:
  - in-JVM application events (fast path)
  - DB-backed outbox/event log for durability (recommended)
- Optionally externalized via Kafka for reporting/monitoring/downstream consumers.

### 6.3 Queries (sync)
- Read-only requests with immediate response.
- Implemented against:
  - the BC’s local DB schema (preferred)
  - projections/read models where appropriate

---

## 7. Spring Boot Application Module: Infrastructure Binding

Recommended structure inside `application/src/main/java/com/knight/application/`:

```
com.knight.application/
├── KnightApplication.java
├── config/
│   ├── JpaConfiguration.java
│   ├── MessagingConfiguration.java
│   └── WebConfiguration.java
├── messaging/                    # shared messaging infrastructure (outbox, bus, checkpoints)
├── users/                        # per-BC infrastructure bindings
│   ├── runtime/                  # command executors, query handlers
│   ├── persistence/              # JPA entities + adapters implementing domain repos
│   ├── rest/                     # controllers
│   └── messaging/                # consumers/producers, internal event listeners
├── identity/
│   └── ... same ...
└── approvals/
    └── ... same ...
```

### 7.1 Implementation roles (per BC)
- **runtime/**: transactional use-case execution (thin but essential)
  - `@Transactional`
  - idempotency check
  - call domain services / aggregates
  - persist via repo adapters
  - append events to outbox/event log
  - publish fast-path JVM events
- **persistence/**: Spring Data + mapping
- **messaging/**: Kafka integration, recovery runners, event listeners
- **rest/**: controllers mapping HTTP to commands/queries

---

## 8. Messaging Options (Opinionated) — Do we need Spring Modulith?

### 8.1 Do you need Spring Modulith?
**Not required**, but useful if you want:
- automated module boundary verification beyond ArchUnit
- module interaction documentation
- a cohesive set of conventions for a modulith

If you already enforce isolation via ArchUnit, you can proceed without Spring Modulith.

**Recommendation**
- Use **ArchUnit as the primary enforcement mechanism**.
- Adopt Spring Modulith optionally for:
  - module verification tests (extra safety)
  - documentation and visualization
  - future event externalization patterns

### 8.2 Recommended messaging approach (hybrid)
Use:
- in-JVM application events for low-latency internal delivery
- DB-backed outbox/event log for audit and crash recovery
- Kafka for external subscribers and later service extraction

This avoids sweep latency in the steady state while preserving durability.

---

## 9. DB-Backed Messaging (Outbox/Event Log) as Audit + Recovery

### 9.1 Why DB-backed messaging even in a modulith
- process can crash after DB commit but before in-memory delivery
- you want audit and replay of facts
- you want deterministic recovery

### 9.2 Shared messaging schema
Use a single schema, e.g. `msg`.

Tables (minimal):
- `msg.command_log` (idempotency + audit)
- `msg.event_log` (append-only event stream)
- `msg.subscription_checkpoint` (replay positions per subscriber)
- optional: `msg.dead_letter`

### 9.3 Fast path + durable path pattern
- During command handling, write state + events to DB in one transaction.
- After commit, publish in-memory events immediately for internal listeners.
- On restart, run a catch-up that reads `msg.event_log` from checkpoint.

---

## 10. Idempotency (Embedded in Command Log)

Use `msg.command_log` as the single idempotency mechanism:
- `command_id` is globally unique
- first insert wins
- duplicates short-circuit

Command lifecycle fields:
- `status`: received | processed | failed
- `error`: optional

Recommended behavior:
- already processed: return stored result (optional) or no-op
- failed: policy-driven (retry allowed vs reject)

---

## 11. Transactions and BC Boundaries

### 11.1 One BC per transaction
Do not span transactions across bounded contexts. A single command execution:
- mutates one BC’s state
- appends events to messaging schema
- commits

Coordination across BCs happens via events.

### 11.2 Same DB, different schemas
Acceptable modulith compromise:
- each BC owns its schema
- messaging schema is shared for audit/replay

Avoid:
- foreign keys across BC schemas
- shared tables for domain state

---

## 12. REST API Design (External Access)

REST controllers should be thin:
- validate request and authz
- map HTTP payload to command or query record
- dispatch to runtime executor/query handler
- return 202 for async commands, 200 for sync queries

Pattern:
- command endpoints return `command_id`, `correlation_id`, and optionally a resource link
- clients query status via read models/query endpoints

---

## 13. Kafka Integration (External Bus)

Kafka is used for:
- external consumers (reporting, monitoring, alerting)
- external producers (commands originating outside the modulith)
- future microservice extraction

Recommended pattern:
- outbox publisher reads `msg.event_log` and publishes to Kafka
- Kafka consumers translate inbound messages into command records

---

## 14. Example (Reference Only): Identity Pattern

Identity use case illustrates the general approach:
- async command
- domain events recorded durably
- sync query

The implementation pattern is the same for any BC:
- runtime executor writes `command_log` + domain changes + `event_log`
- after-commit publishes JVM events
- Kafka externalization optional

---

## 15. Testing and Enforcement

### 15.1 ArchUnit
Recommended rules:
- API isolation
- Aggregate isolation
- Service isolation
- Bounded context isolation
- domain must not depend on Spring/JPA/Kafka
- cross-BC references allowed only to shared kernel and other BC `api/*` packages (if permitted)

### 15.2 Integration testing
Use Testcontainers:
- MSSQL
- Kafka (if Kafka is part of runtime)

Use Awaitility for async verification.

---

## 16. Operational Considerations

- correlation_id propagation across commands/events
- structured logging with command_id/event_id
- retention strategy for event_log (partition/archive)
- indexes on `event_log.sequence` for replay scans

---

## 17. Summary of Opinionated Choices

- DDD modulith using Maven multi-module
- domain modules are pure Java libraries with published `api/*` records
- one Spring Boot application module binds infrastructure
- hybrid messaging:
  - JVM events for fast internal delivery
  - DB event_log for audit/replay/crash recovery
  - Kafka for external subscribers and future extraction
- idempotency embedded in `command_log`
- one BC per transaction; event-driven coordination across BCs
- ArchUnit primary enforcement; Spring Modulith optional

---

## 18. Implementation Checklist (Minimum)

- Maven modules: shared-kernel, domain/*, application
- Application module:
  - per-BC runtime executors and query handlers
  - per-BC JPA adapters implementing domain repositories
  - shared messaging infrastructure (command_log, event_log, checkpoints)
  - after-commit JVM publisher
  - startup recovery runner to replay missed events
- ArchUnit rules enforced in CI
- Testcontainers coverage for MSSQL + Kafka (if used)
