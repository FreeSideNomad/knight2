# Identity and Modulith Design (DB-backed Messaging, Okta as System of Record)

This document consolidates the architectural decisions discussed for the **Identity bounded context** within a **Spring Modulith**, assuming:
- **DB is the source of truth for messaging, audit, and replay**
- **Okta is the source of truth for identity state**
- **Commands and events are durable via DB logs**
- **Fast-path delivery uses in-JVM application events**
- **No multi–bounded-context DB transactions**

---

## 1. Core Principles

### 1.1 Systems of Record
- **Okta** is the system of record for identity lifecycle state (exists, onboarded, MFA, enabled/disabled).
- **Application DB** is the system of record for:
  - audit trail
  - command idempotency
  - event history
  - recovery and replay

The Identity BC DB **does not model identity state as authoritative**, only:
- references (email, okta_user_id)
- correlation
- progress markers (created, onboarded) for workflow coordination

---

## 2. Modulith Architecture Overview

- Single Spring Boot application
- Multiple bounded contexts packaged as domain libraries
- One application module binds infrastructure

Communication semantics:
- **Async commands in**
- **Async events out**
- **Sync queries allowed (read-only)**

---

## 3. Package and Module Structure

### 3.1 Domain library: Identity BC

```
domain/identity/src/main/java/com/knight/domain/identity/
├── aggregate/
│   └── agg_identity
├── repository/
│   └── rep_identity_repository
├── service/
│   └── dom_svc_identity
└── api/
    ├── commands/
    │   └── cmd_create_new_identity
    ├── queries/
    │   ├── qry_identity_exists_by_email
    │   └── qry_identity_exists_by_email_result
    └── events/
        ├── evt_identity_created
        └── evt_identity_onboarded
```

Characteristics:
- `api/*` are **published contracts** (records, validation, serialization)
- domain services implement **business decisions only**
- repository is a port, not a Spring dependency

---

### 3.2 Application module: Identity infrastructure

```
application/src/main/java/com/knight/application/identity/
├── runtime/
│   ├── IdentityCommandExecutor
│   ├── IdentityQueryHandler
│   └── IdentityRecoveryRunner
├── persistence/
│   ├── entity/
│   ├── repository/
│   └── mapper/
├── inbound/
│   └── okta/
│       └── OktaWebhookController
├── messaging/
│   ├── EventLogWriter
│   └── InJvmEventPublisher
└── config/
```

Responsibilities:
- transactions
- idempotency
- DB-backed messaging
- JVM fast-path delivery
- recovery on restart

---

## 4. Messaging Model (DB-backed Bus)

### 4.1 Shared Messaging Schema

Single schema, e.g. `msg`.

#### 4.1.1 `msg.command_log`
Used for **idempotency and audit**.

Key fields:
- `command_id` (UUID, PK)
- `command_type`
- `target_context`
- `correlation_id`
- `received_at`
- `payload_json`
- `status` (received | processed | failed)

Semantics:
- first insert wins
- duplicate command_id ⇒ command is ignored or short-circuited
- this replaces a separate idempotency table

---

#### 4.1.2 `msg.event_log`
Append-only durable event stream.

Key fields:
- `event_id` (UUID, PK)
- `sequence` (monotonic bigint identity)
- `event_type`
- `aggregate_type`
- `aggregate_id`
- `correlation_id`
- `causation_id` (command_id)
- `occurred_at`
- `payload_json`

Properties:
- written in the **same transaction** as domain work
- never updated, only appended
- retained for audit and replay

---

#### 4.1.3 `msg.subscription_checkpoint`
Tracks replay position per subscriber.

- `subscriber_id` (PK)
- `last_sequence_processed`
- `updated_at`

---

## 5. Fast Path vs Durable Path

### 5.1 Fast Path (steady state)
- After DB commit, events are published as **JVM application events**
- Internal subscribers react immediately
- No polling, no sweep latency

Implementation:
- `TransactionSynchronization`
- or `@TransactionalEventListener(AFTER_COMMIT)`

---

### 5.2 Durable Path (recovery + replay)
- On startup (or on demand), subscribers:
  - read `msg.event_log` from `last_sequence_processed + 1`
  - process events in order
  - advance checkpoint

This guarantees:
- no event loss on crash
- deterministic replay

In-memory delivery is an **optimization**, DB log is the **guarantee**.

---

## 6. Identity MVP Semantics

### 6.1 Command: Create New Identity

**Command**
- `cmd_create_new_identity(email, profile, correlation_id, command_id)`

Properties:
- async
- idempotent via `msg.command_log`
- does not assume identity state in DB

---

### 6.2 Processing Flow (Stage 1)

Transactional steps in `IdentityCommandExecutor`:

1. Insert into `msg.command_log`
   - if duplicate `command_id`, short-circuit
2. Call Okta to create user
   - Okta assigns `okta_user_id`
3. Persist minimal Identity reference
   - `identity_id`
   - `email`
   - `okta_user_id`
4. Append `evt_identity_created` to `msg.event_log`
5. Commit
6. After-commit: publish JVM event

**No identity lifecycle truth is stored locally**; DB stores correlation only.

---

### 6.3 Event: Identity Created

`evt_identity_created` represents:
> “An Okta identity has been created and is known to the system.”

Payload:
- `identity_id`
- `email`
- `okta_user_id`
- `occurred_at`
- `correlation_id`
- `causation_id`

---

### 6.4 Processing Flow (Stage 2: Onboarding)

Trigger:
- Okta webhook signals enrollment completion

Transactional steps:

1. Deduplicate webhook (Okta event id)
2. Resolve `okta_user_id → identity_id`
3. Append `evt_identity_onboarded`
4. Commit
5. After-commit: publish JVM event

---

### 6.5 Event: Identity Onboarded

`evt_identity_onboarded` represents:
> “The user completed enrollment (password + MFA) in Okta.”

Payload:
- `identity_id`
- `okta_user_id`
- `enrolled_at`
- optional `mfa_enrolled`
- correlation/causation

---

## 7. Synchronous Query

### 7.1 Query: Identity Exists by Email

**Query**
- `qry_identity_exists_by_email(email)`

**Response**
- `qry_identity_exists_by_email_result(exists, identity_id?)`

Properties:
- synchronous
- served from Identity DB
- used for UX validation and admin flows
- not relied on for correctness alone (Okta remains authoritative)

---

## 8. Domain vs Application Responsibilities

### 8.1 Domain (`dom_svc_identity`)
- decide which events must be emitted
- validate business rules that do not require infrastructure
- work with repository interfaces only
- no Spring, no Kafka, no DB transaction control

### 8.2 Application (runtime layer)
- transaction boundaries
- command idempotency (via `command_log`)
- persistence
- append to `event_log`
- JVM fast-path publish
- recovery and replay

---

## 9. Cross-BC Coordination

- Each command updates **one BC only**
- Coordination happens via events (even in modulith)
- No cross-BC DB transactions
- Same DB, different schemas is acceptable **only** for messaging tables

---

## 10. Replay and Audit

### 10.1 Replay
- reset `subscription_checkpoint`
- re-run subscriber
- deterministic, ordered, auditable

### 10.2 Audit
- `command_log` shows intent
- `event_log` shows facts
- Okta is external truth; DB is internal evidence

---

## 11. Migration Path (Future)

If the modulith is split:
- each BC keeps the same API contracts
- `event_log` publisher is replaced by Kafka producer
- DB remains local per service
- replay moves from DB-based to Kafka offset–based

No domain code changes required.

---

## 12. Summary

- DB-backed messaging gives audit, replay, and crash safety
- JVM events give speed
- Okta owns identity truth; DB owns messaging truth
- Identity BC stays clean, isolated, and future-proof
- Modulith today, distributable tomorrow
