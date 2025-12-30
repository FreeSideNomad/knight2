# IndirectClient Refactoring Plan

## Overview

This document outlines the refactoring of the `IndirectClient` entity to simplify its structure and align naming conventions with the rest of the domain model.

## Goals

1. **Replace `id` (UUID) and `indirect_client_urn` with single `client_id`** in format `ind:{UUID}`
2. **Rename `profile_id` to `parent_profile_id`** to clarify relationship
3. **Rename `business_name` to `name`** for consistency with other entities
4. **For indirect profiles, `profile_id` should match `client_id`** (`ind:{UUID}`)
5. **Consolidate all migrations into single V1 file** (drop and recreate)

## Current State

### IndirectClient Entity Fields
| Current Field | Type | Description |
|---------------|------|-------------|
| `id` | UUID | Database primary key |
| `indirect_client_urn` | VARCHAR(200) | Domain identifier: `indirect:{parentClientUrn}:{sequence}` |
| `parent_client_id` | VARCHAR(100) | FK to parent client (e.g., `srf:123456`) |
| `profile_id` | VARCHAR(200) | FK to profile that owns this indirect client |
| `client_type` | VARCHAR(20) | PERSON or BUSINESS |
| `business_name` | VARCHAR(255) | Name of the indirect client |
| `external_reference` | VARCHAR(100) | External system reference |
| `status` | VARCHAR(20) | PENDING, ACTIVE, SUSPENDED |

### Current URN Format
- Indirect Client: `indirect:srf:123456:1` (parent URN + sequence)
- Related Profile: `profile:srf:123456:servicing` (separate from indirect client)

## Target State

### IndirectClient Entity Fields (After Refactoring)
| New Field | Type | Description |
|-----------|------|-------------|
| `client_id` | VARCHAR(50) | Primary key: `ind:{UUID}` |
| `parent_client_id` | VARCHAR(100) | FK to parent client (unchanged) |
| `parent_profile_id` | VARCHAR(200) | FK to profile that owns this (renamed) |
| `client_type` | VARCHAR(20) | PERSON or BUSINESS (unchanged) |
| `name` | VARCHAR(255) | Name of the indirect client (renamed) |
| `external_reference` | VARCHAR(100) | External system reference (unchanged) |
| `status` | VARCHAR(20) | PENDING, ACTIVE, SUSPENDED (unchanged) |

### New ID Format
- Indirect Client ID: `ind:{UUID}` (e.g., `ind:550e8400-e29b-41d4-a716-446655440000`)
- Indirect Client's Profile ID: Same as client_id (`ind:{UUID}`)

### Benefits of New Format
1. **Simpler** - Single field serves as both PK and domain identifier
2. **Consistent** - Matches pattern of `srf:`, `cdr:` prefixes
3. **Profile alignment** - Indirect client profile uses same ID as the client
4. **No sequence tracking** - UUID eliminates need for sequence generation

---

## Files to Modify

### 1. Database Migration

**Action:** Replace all migration files with single consolidated V1

**File:** `application/src/main/resources/db/migration/V1__initial_schema.sql`

```sql
-- indirect_clients table (UPDATED)
CREATE TABLE indirect_clients (
    client_id VARCHAR(50) NOT NULL,           -- Changed from UUID id + indirect_client_urn
    parent_client_id VARCHAR(100) NOT NULL,   -- Unchanged
    parent_profile_id VARCHAR(200) NOT NULL,  -- Renamed from profile_id
    client_type VARCHAR(20) NOT NULL,         -- Unchanged
    name NVARCHAR(255) NOT NULL,              -- Renamed from business_name
    external_reference VARCHAR(100) NULL,     -- Unchanged
    status VARCHAR(20) NOT NULL,              -- Unchanged
    created_at DATETIME2 NOT NULL,            -- Unchanged
    updated_at DATETIME2 NOT NULL,            -- Unchanged
    CONSTRAINT PK_indirect_clients PRIMARY KEY (client_id),
    CONSTRAINT FK_indirect_clients_parent FOREIGN KEY (parent_client_id)
        REFERENCES clients(client_id),
    CONSTRAINT FK_indirect_clients_profile FOREIGN KEY (parent_profile_id)
        REFERENCES profiles(profile_id)
);

-- Indexes
CREATE INDEX idx_indirect_clients_parent ON indirect_clients(parent_client_id);
CREATE INDEX idx_indirect_clients_profile ON indirect_clients(parent_profile_id);

-- indirect_client_persons table (UPDATED FK)
CREATE TABLE indirect_client_persons (
    person_id UNIQUEIDENTIFIER NOT NULL,
    indirect_client_id VARCHAR(50) NOT NULL,  -- Changed from UUID to match new PK
    name NVARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    email VARCHAR(255) NULL,
    phone VARCHAR(20) NULL,
    added_at DATETIME2 NOT NULL,
    CONSTRAINT PK_indirect_client_persons PRIMARY KEY (person_id),
    CONSTRAINT FK_indirect_persons_client FOREIGN KEY (indirect_client_id)
        REFERENCES indirect_clients(client_id) ON DELETE CASCADE
);

-- client_accounts table (UPDATED FK)
-- Change indirect_client_id from UUID to VARCHAR(50)
ALTER TABLE client_accounts
    ALTER COLUMN indirect_client_id VARCHAR(50) NULL;
```

**Files to delete:**
- `V2__batch_processing.sql` - Merge into V1
- `V3__add_external_reference.sql` - Merge into V1

---

### 2. Domain Layer

#### 2.1 IndirectClientId Value Object
**File:** `domain/clients/src/main/java/com/knight/domain/indirectclients/vo/IndirectClientId.java`

```java
public record IndirectClientId(String value) {
    private static final String PREFIX = "ind:";

    public IndirectClientId {
        if (value == null || !value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("IndirectClientId must start with 'ind:'");
        }
    }

    public static IndirectClientId generate() {
        return new IndirectClientId(PREFIX + UUID.randomUUID());
    }

    public static IndirectClientId fromString(String value) {
        return new IndirectClientId(value);
    }

    public String urn() {
        return value;
    }
}
```

#### 2.2 IndirectClient Aggregate
**File:** `domain/clients/src/main/java/com/knight/domain/indirectclients/aggregate/IndirectClient.java`

**Changes:**
- Replace `id` field type from current to new `IndirectClientId`
- Rename `profileId` to `parentProfileId`
- Rename `businessName` to `name`
- Remove sequence-based ID generation logic
- Add method to get profile ID (returns same as client ID)

```java
public class IndirectClient {
    private final IndirectClientId id;              // Now uses ind:{UUID} format
    private final ClientId parentClientId;          // Unchanged
    private final ProfileId parentProfileId;        // Renamed from profileId
    private final ClientType clientType;            // Unchanged
    private String name;                            // Renamed from businessName
    private String externalReference;               // Unchanged
    private Status status;                          // Unchanged
    private List<RelatedPerson> relatedPersons;     // Unchanged

    // Profile ID for this indirect client's profile matches the client ID
    public ProfileId getProfileId() {
        return ProfileId.fromUrn(id.value());  // ind:{UUID}
    }

    public static IndirectClient create(
            ClientId parentClientId,
            ProfileId parentProfileId,  // Renamed parameter
            ClientType clientType,
            String name,                // Renamed parameter
            String externalReference) {

        IndirectClientId id = IndirectClientId.generate();
        // ... validation and creation logic
    }
}
```

#### 2.3 IndirectClientRepository Interface
**File:** `domain/clients/src/main/java/com/knight/domain/indirectclients/repository/IndirectClientRepository.java`

**Changes:**
- Remove `getNextSequenceForClient()` method (no longer needed)
- Update method signatures for renamed fields

```java
public interface IndirectClientRepository {
    void save(IndirectClient client);
    Optional<IndirectClient> findById(IndirectClientId id);
    List<IndirectClient> findByParentClientId(ClientId parentClientId);
    List<IndirectClient> findByParentProfileId(ProfileId parentProfileId);  // Renamed
    boolean existsByParentProfileIdAndName(ProfileId parentProfileId, String name);  // Renamed
}
```

---

### 3. Persistence Layer

#### 3.1 IndirectClientEntity
**File:** `application/src/main/java/com/knight/application/persistence/indirectclients/entity/IndirectClientEntity.java`

```java
@Entity
@Table(name = "indirect_clients")
public class IndirectClientEntity {

    @Id
    @Column(name = "client_id", length = 50, nullable = false)
    private String clientId;  // Changed from UUID id + String indirectClientUrn

    @Column(name = "parent_client_id", length = 100, nullable = false)
    private String parentClientId;  // Unchanged

    @Column(name = "parent_profile_id", length = 200, nullable = false)
    private String parentProfileId;  // Renamed from profileId

    @Column(name = "client_type", length = 20, nullable = false)
    private String clientType;  // Unchanged

    @Column(name = "name", length = 255, nullable = false)
    private String name;  // Renamed from businessName

    @Column(name = "external_reference", length = 100)
    private String externalReference;  // Unchanged

    @Column(name = "status", length = 20, nullable = false)
    private String status;  // Unchanged

    @OneToMany(mappedBy = "indirectClient", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RelatedPersonEntity> relatedPersons = new ArrayList<>();

    // ... timestamps, getters, setters
}
```

#### 3.2 RelatedPersonEntity
**File:** `application/src/main/java/com/knight/application/persistence/indirectclients/entity/RelatedPersonEntity.java`

**Changes:**
- Update `indirect_client_id` column type from UUID to VARCHAR(50)

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "indirect_client_id", nullable = false)
private IndirectClientEntity indirectClient;
```

#### 3.3 ClientAccountEntity
**File:** `application/src/main/java/com/knight/application/persistence/clients/entity/ClientAccountEntity.java`

**Changes:**
- Update `indirect_client_id` column type from UUID to VARCHAR(50)

```java
@Column(name = "indirect_client_id", length = 50)
private String indirectClientId;  // Changed from UUID
```

#### 3.4 IndirectClientJpaRepository
**File:** `application/src/main/java/com/knight/application/persistence/indirectclients/repository/IndirectClientJpaRepository.java`

```java
public interface IndirectClientJpaRepository extends JpaRepository<IndirectClientEntity, String> {
    // Changed from UUID to String for PK type

    List<IndirectClientEntity> findByParentClientId(String parentClientId);

    List<IndirectClientEntity> findByParentProfileId(String parentProfileId);  // Renamed

    // Remove: findByIndirectClientUrn - no longer needed, use findById
    // Remove: countByParentClientId - no longer needed for sequence

    boolean existsByParentProfileIdAndName(String parentProfileId, String name);  // Renamed
}
```

#### 3.5 IndirectClientMapper
**File:** `application/src/main/java/com/knight/application/persistence/indirectclients/mapper/IndirectClientMapper.java`

```java
public class IndirectClientMapper {

    public IndirectClientEntity toEntity(IndirectClient domain) {
        IndirectClientEntity entity = new IndirectClientEntity();
        entity.setClientId(domain.id().value());  // Direct mapping
        entity.setParentClientId(domain.parentClientId().urn());
        entity.setParentProfileId(domain.parentProfileId().urn());  // Renamed
        entity.setClientType(domain.clientType().name());
        entity.setName(domain.name());  // Renamed
        entity.setExternalReference(domain.externalReference());
        entity.setStatus(domain.status().name());
        // ... map related persons
        return entity;
    }

    public IndirectClient toDomain(IndirectClientEntity entity) {
        return IndirectClient.reconstitute(
            IndirectClientId.fromString(entity.getClientId()),
            ClientId.fromUrn(entity.getParentClientId()),
            ProfileId.fromUrn(entity.getParentProfileId()),  // Renamed
            ClientType.valueOf(entity.getClientType()),
            entity.getName(),  // Renamed
            entity.getExternalReference(),
            Status.valueOf(entity.getStatus()),
            mapRelatedPersons(entity.getRelatedPersons()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
```

#### 3.6 IndirectClientRepositoryAdapter
**File:** `application/src/main/java/com/knight/application/persistence/indirectclients/repository/IndirectClientRepositoryAdapter.java`

**Changes:**
- Remove `getNextSequenceForClient()` implementation
- Update method calls for renamed fields
- Simplify `findById()` to use direct PK lookup

---

### 4. REST Layer

#### 4.1 IndirectClientController
**File:** `application/src/main/java/com/knight/application/rest/indirectclients/IndirectClientController.java`

**Changes:**
- Update endpoint `/by-profile` to use `parentProfileId` parameter
- Update `toDto()` and `toDetailDto()` for renamed fields
- Remove sequence generation logic from create endpoint

```java
@GetMapping("/by-profile")
public ResponseEntity<List<IndirectClientDto>> getByParentProfile(
        @RequestParam String parentProfileId) {  // Renamed parameter
    // ...
}
```

#### 4.2 DTOs

**IndirectClientDto:**
```java
public record IndirectClientDto(
    String id,                  // Now returns ind:{UUID}
    String parentClientId,
    String clientType,
    String name,                // Renamed from businessName
    String status,
    int relatedPersonCount,
    Instant createdAt
) {}
```

**IndirectClientDetailDto:**
```java
public record IndirectClientDetailDto(
    String id,                  // Now returns ind:{UUID}
    String parentClientId,
    String parentProfileId,     // Renamed from profileId
    String profileId,           // NEW: The indirect client's own profile (same as id)
    String clientType,
    String name,                // Renamed from businessName
    String status,
    List<RelatedPersonDto> relatedPersons,
    List<OfiAccountDto> ofiAccounts,
    Instant createdAt,
    Instant updatedAt
) {}
```

**CreateIndirectClientRequest:**
```java
public record CreateIndirectClientRequest(
    @NotBlank String parentClientId,
    @NotBlank String parentProfileId,  // Renamed from profileId
    @NotBlank String name,             // Renamed from businessName
    @Valid List<RelatedPersonRequest> relatedPersons
) {}
```

---

### 5. Service Layer

#### 5.1 PayorEnrolmentProcessorImpl
**File:** `application/src/main/java/com/knight/application/service/PayorEnrolmentProcessorImpl.java`

**Changes:**
- Remove sequence generation logic
- Use `IndirectClientId.generate()` for new ID
- Use renamed fields (`parentProfileId`, `name`)
- Create profile with ID matching the indirect client ID

```java
private IndirectClient createIndirectClient(PayorEnrolmentRequest request, Profile sourceProfile) {
    IndirectClientId clientId = IndirectClientId.generate();

    IndirectClient indirectClient = IndirectClient.create(
        sourceProfile.primaryClientId(),
        sourceProfile.id(),           // This becomes parentProfileId
        ClientType.BUSINESS,
        request.businessName(),       // Mapped to name field
        request.externalReference()
    );

    // Create profile for indirect client with matching ID
    Profile indirectProfile = Profile.create(
        ProfileId.fromUrn(clientId.value()),  // Profile ID = Client ID
        request.businessName(),
        ProfileType.INDIRECT,
        // ... other fields
    );

    return indirectClient;
}
```

---

### 6. Test Updates

#### Files to Update:
1. `IndirectClientControllerE2ETest.java` - Update assertions for renamed fields
2. `IndirectClientMapperTest.java` - Update field mappings
3. Any unit tests for IndirectClient domain logic

---

## Migration Strategy

### Option A: Clean Slate (Recommended for Dev/Test)
1. Drop all existing data
2. Delete V2 and V3 migration files
3. Update V1 with new schema
4. Run fresh migration

### Option B: Data Migration (Required for Production)
1. Create V4 migration:
   ```sql
   -- Add new columns
   ALTER TABLE indirect_clients ADD client_id VARCHAR(50);

   -- Migrate data: generate new IDs
   UPDATE indirect_clients
   SET client_id = CONCAT('ind:', CAST(id AS VARCHAR(36)));

   -- Update foreign keys in related tables
   UPDATE indirect_client_persons p
   SET p.indirect_client_id = (
       SELECT CONCAT('ind:', CAST(ic.id AS VARCHAR(36)))
       FROM indirect_clients ic
       WHERE ic.id = p.indirect_client_id
   );

   -- Rename columns
   EXEC sp_rename 'indirect_clients.profile_id', 'parent_profile_id', 'COLUMN';
   EXEC sp_rename 'indirect_clients.business_name', 'name', 'COLUMN';

   -- Drop old columns and constraints
   ALTER TABLE indirect_clients DROP CONSTRAINT PK_indirect_clients;
   ALTER TABLE indirect_clients DROP COLUMN id;
   ALTER TABLE indirect_clients DROP COLUMN indirect_client_urn;

   -- Add new primary key
   ALTER TABLE indirect_clients ADD CONSTRAINT PK_indirect_clients PRIMARY KEY (client_id);
   ```

---

## Implementation Order

1. **Phase 1: Domain Layer**
   - Update `IndirectClientId` value object
   - Update `IndirectClient` aggregate
   - Update repository interface

2. **Phase 2: Persistence Layer**
   - Update entity classes
   - Update JPA repository
   - Update mapper
   - Update repository adapter

3. **Phase 3: REST Layer**
   - Update DTOs
   - Update controller

4. **Phase 4: Service Layer**
   - Update PayorEnrolmentProcessor
   - Update any other services

5. **Phase 5: Database**
   - Consolidate migrations
   - Test with clean database

6. **Phase 6: Tests**
   - Update all affected tests
   - Run full test suite

---

## Validation Checklist

- [ ] All `indirect_client_urn` references replaced with `client_id`
- [ ] All `profile_id` references renamed to `parent_profile_id`
- [ ] All `business_name` references renamed to `name`
- [ ] Indirect client profile uses same ID as client (`ind:{UUID}`)
- [ ] UUID-based ID generation removed (now uses `ind:{UUID}`)
- [ ] Sequence tracking removed from repository
- [ ] All foreign key relationships updated
- [ ] All DTOs updated with new field names
- [ ] All tests passing
- [ ] Migration consolidated to single V1 file
