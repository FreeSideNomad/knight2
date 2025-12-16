# Create Profile Implementation Plan

## Objective

Implement a profile creation workflow that allows users to:
1. Create a profile (SERVICING or ONLINE) for a primary client
2. Select accounts to enroll (all/automatic or specific/manual)
3. Add secondary clients with their accounts
4. Execute everything in a single command

---

## Execution Steps

### Step 1: Domain Cleanup - Remove Fields from Client

Remove unnecessary fields from `Client` aggregate (keep address fields):

**File:** `domain/clients/src/main/java/com/knight/domain/clients/aggregate/Client.java`
- Remove `taxId` field
- Remove `taxNumber` field
- Remove `phoneNumber` field
- Remove `emailAddress` field
- **KEEP** address fields (street, city, state, postalCode, country)
- Update constructor and factory methods
- Remove getters for removed fields

**File:** `application/src/main/java/com/knight/application/persistence/clients/entity/ClientEntity.java`
- Remove corresponding JPA columns for taxId, taxNumber, phoneNumber, emailAddress
- **KEEP** address columns

**File:** `application/src/main/java/com/knight/application/persistence/clients/mapper/ClientMapper.java`
- Update mapping logic (keep address mapping)

---

### Step 2: Domain Cleanup - Remove Users from Profile

Users are managed separately via the `User` aggregate (linked by `ProfileId`). Remove user-related code from Profile:

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/aggregate/Profile.java`
- Remove `users` field (`Set<UserId>`)
- Remove `assignUser(UserId)` method
- Remove `removeUser(UserId)` method
- Remove `hasUser(UserId)` method
- Remove `users()` getter
- Remove `HashSet` and `Set` imports if unused

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/api/commands/ProfileCommands.java`
- Remove `AssignUserCmd` record
- Remove `RemoveUserCmd` record
- Remove `assignUser(AssignUserCmd)` method
- Remove `removeUser(RemoveUserCmd)` method
- Remove `UserId` import

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/service/ProfileApplicationService.java`
- Remove `assignUser()` implementation
- Remove `removeUser()` implementation

**File:** `application/src/main/java/com/knight/application/persistence/profiles/entity/ProfileEntity.java`
- Remove `users` collection field
- Remove `@ElementCollection` for profile_users

**File:** `application/src/main/java/com/knight/application/persistence/profiles/mapper/ProfileMapper.java`
- Remove user mapping in `toEntity()`
- Remove user reconstruction in `toDomain()`
- Remove `UserId` import

---

### Step 3: Remove clientId from Profile Aggregate

The `clientId` field on Profile is now redundant - the primary client is determined by `ClientEnrollment.isPrimary = true`.

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/aggregate/Profile.java`
- Remove `private final ClientId clientId` field
- Remove `clientId()` getter
- Add `primaryClientId()` derived getter:

```java
/**
 * Returns the primary client ID (derived from clientEnrollments).
 * There is always exactly one primary client.
 */
public ClientId primaryClientId() {
    return clientEnrollments.stream()
        .filter(ClientEnrollment::isPrimary)
        .findFirst()
        .map(ClientEnrollment::clientId)
        .orElseThrow(() -> new IllegalStateException("Profile must have a primary client"));
}
```

- Update constructor to not require clientId parameter
- Update `ProfileId.of()` to use primary client from enrollments

---

### Step 4: Add AccountEnrollmentType Enum

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/aggregate/Profile.java`

```java
public enum AccountEnrollmentType {
    MANUAL,     // User explicitly selected specific accounts
    AUTOMATIC   // System enrolled all accounts for the client
}
```

---

### Step 5: Add ClientEnrollment Entity to Profile

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/aggregate/Profile.java`

```java
/**
 * Tracks a client's enrollment in the profile.
 * Exactly one client must be marked as primary.
 */
public static class ClientEnrollment {
    private final ClientId clientId;
    private final boolean isPrimary;
    private final AccountEnrollmentType accountEnrollmentType;
    private final Instant enrolledAt;

    public ClientEnrollment(ClientId clientId, boolean isPrimary, AccountEnrollmentType accountEnrollmentType) {
        this.clientId = Objects.requireNonNull(clientId);
        this.isPrimary = isPrimary;
        this.accountEnrollmentType = Objects.requireNonNull(accountEnrollmentType);
        this.enrolledAt = Instant.now();
    }

    // Getters
    public ClientId clientId() { return clientId; }
    public boolean isPrimary() { return isPrimary; }
    public AccountEnrollmentType accountEnrollmentType() { return accountEnrollmentType; }
    public Instant enrolledAt() { return enrolledAt; }
}
```

Add to Profile class:
```java
private final List<ClientEnrollment> clientEnrollments;

// Getter
public List<ClientEnrollment> clientEnrollments() { return List.copyOf(clientEnrollments); }
```

---

### Step 6: Update AccountEnrollment to Include ClientId

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/aggregate/Profile.java`

```java
public static class AccountEnrollment {
    private final EnrollmentId enrollmentId;
    private final EnrollmentId serviceEnrollmentId; // null = profile-level
    private final ClientId clientId;                // which client owns this account
    private final ClientAccountId accountId;
    private Status status;
    private final Instant enrolledAt;

    public AccountEnrollment(EnrollmentId serviceEnrollmentId, ClientId clientId, ClientAccountId accountId) {
        this.enrollmentId = EnrollmentId.generate();
        this.serviceEnrollmentId = serviceEnrollmentId;
        this.clientId = Objects.requireNonNull(clientId);
        this.accountId = Objects.requireNonNull(accountId);
        this.status = Status.ACTIVE;
        this.enrolledAt = Instant.now();
    }

    // Getters (add clientId)
    public ClientId clientId() { return clientId; }
    // ... existing getters
}
```

---

### Step 7: Create Composite Command

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/api/commands/ProfileCommands.java`

```java
/**
 * Client account selection for profile creation.
 */
record ClientAccountSelection(
    ClientId clientId,
    boolean isPrimary,
    AccountEnrollmentType enrollmentType,
    List<ClientAccountId> accountIds  // for MANUAL: specific accounts; for AUTOMATIC: ignored (resolved by service)
) {}

/**
 * Create a profile with initial client and account enrollments in a single operation.
 * Exactly one client must be marked as primary.
 *
 * Validation rules:
 * - For SERVICING profiles: primary client cannot already be primary in another servicing profile
 * - For ONLINE profiles: no restriction on primary client
 */
record CreateProfileWithAccountsCmd(
    ProfileType profileType,
    String name,  // Profile name (defaults to primary client name if null)
    List<ClientAccountSelection> clientSelections,
    String createdBy
) {}

ProfileId createProfileWithAccounts(CreateProfileWithAccountsCmd cmd);
```

---

### Step 8: Update Profile Factory Method

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/aggregate/Profile.java`

Add `name` field to Profile:
```java
private final String name;  // Profile name (defaults to primary client name)

// Getter
public String name() { return name; }
```

```java
public record ClientEnrollmentRequest(
    ClientId clientId,
    boolean isPrimary,
    AccountEnrollmentType enrollmentType,
    List<ClientAccountId> accountIds
) {}

public static Profile createWithAccounts(
    ProfileType profileType,
    String name,  // Profile name
    List<ClientEnrollmentRequest> clientEnrollments,
    String createdBy
) {
    // Validate exactly one primary client
    long primaryCount = clientEnrollments.stream().filter(ClientEnrollmentRequest::isPrimary).count();
    if (primaryCount == 0) {
        throw new IllegalArgumentException("Exactly one primary client is required");
    }
    if (primaryCount > 1) {
        throw new IllegalArgumentException("Only one primary client is allowed");
    }

    // Get primary client ID for profile ID generation
    ClientId primaryClientId = clientEnrollments.stream()
        .filter(ClientEnrollmentRequest::isPrimary)
        .findFirst()
        .map(ClientEnrollmentRequest::clientId)
        .orElseThrow();

    ProfileId profileId = ProfileId.of(primaryClientId);
    Profile profile = new Profile(profileId, profileType, name, createdBy);

    // Add client enrollments and account enrollments
    for (ClientEnrollmentRequest request : clientEnrollments) {
        profile.addClientEnrollment(request.clientId(), request.isPrimary(), request.enrollmentType());
        for (ClientAccountId accountId : request.accountIds()) {
            profile.addAccountEnrollment(request.clientId(), accountId);
        }
    }

    return profile;
}

private Profile(ProfileId profileId, ProfileType profileType, String name, String createdBy) {
    this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
    this.profileType = Objects.requireNonNull(profileType, "profileType cannot be null");
    this.name = Objects.requireNonNull(name, "name cannot be null");
    this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
    this.status = Status.PENDING;
    this.clientEnrollments = new ArrayList<>();
    this.serviceEnrollments = new ArrayList<>();
    this.accountEnrollments = new ArrayList<>();
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
}

private void addClientEnrollment(ClientId clientId, boolean isPrimary, AccountEnrollmentType enrollmentType) {
    ClientEnrollment enrollment = new ClientEnrollment(clientId, isPrimary, enrollmentType);
    this.clientEnrollments.add(enrollment);
}

private void addAccountEnrollment(ClientId clientId, ClientAccountId accountId) {
    AccountEnrollment enrollment = new AccountEnrollment(null, clientId, accountId);
    this.accountEnrollments.add(enrollment);
    this.updatedAt = Instant.now();
}
```

---

### Step 9: Update Profile Repository with New Query Methods

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/repository/ProfileRepository.java`

```java
public interface ProfileRepository {
    void save(Profile profile);
    Optional<Profile> findById(ProfileId profileId);

    /**
     * Find all profiles where the given client is the primary client.
     */
    List<Profile> findByPrimaryClient(ClientId clientId);

    /**
     * Find all profiles where the given client is a secondary client (not primary).
     */
    List<Profile> findBySecondaryClient(ClientId clientId);

    /**
     * Find all profiles where the given client is enrolled (either primary or secondary).
     */
    List<Profile> findByClient(ClientId clientId);

    /**
     * Check if client is already primary in a SERVICING profile.
     * Used to enforce: "A client can be primary in only ONE servicing profile"
     */
    boolean existsServicingProfileWithPrimaryClient(ClientId clientId);
}
```

---

### Step 10: Update Profile Queries Interface

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/api/queries/ProfileQueries.java`

```java
public interface ProfileQueries {

    record ProfileSummary(
        String profileId,
        String name,
        String profileType,
        String status,
        String primaryClientId,
        int clientCount,
        int serviceEnrollmentCount,
        int accountEnrollmentCount
    ) {}

    ProfileSummary getProfileSummary(ProfileId profileId);

    /**
     * Get all profiles where the client acts as primary client.
     */
    List<ProfileSummary> findPrimaryProfiles(ClientId clientId);

    /**
     * Get all profiles where the client acts as secondary client.
     */
    List<ProfileSummary> findSecondaryProfiles(ClientId clientId);

    /**
     * Get all profiles where the client is enrolled (primary or secondary).
     */
    List<ProfileSummary> findAllProfilesForClient(ClientId clientId);
}
```

---

### Step 11: Implement Composite Command Handler

**File:** `domain/profiles/src/main/java/com/knight/domain/profiles/service/ProfileApplicationService.java`

```java
@Override
@Transactional
public ProfileId createProfileWithAccounts(CreateProfileWithAccountsCmd cmd) {
    // Validate exactly one primary
    long primaryCount = cmd.clientSelections().stream().filter(ClientAccountSelection::isPrimary).count();
    if (primaryCount != 1) {
        throw new IllegalArgumentException("Exactly one primary client is required");
    }

    // Get primary client
    ClientId primaryClientId = cmd.clientSelections().stream()
        .filter(ClientAccountSelection::isPrimary)
        .findFirst()
        .map(ClientAccountSelection::clientId)
        .orElseThrow();

    // For SERVICING profiles: validate primary client is not already primary in another servicing profile
    if (cmd.profileType() == ProfileType.SERVICING) {
        if (repository.existsServicingProfileWithPrimaryClient(primaryClientId)) {
            throw new IllegalArgumentException(
                "Client " + primaryClientId.urn() + " is already primary in another servicing profile");
        }
    }

    // Resolve profile name (default to primary client name if not provided)
    String profileName = cmd.name();
    if (profileName == null || profileName.isBlank()) {
        Client primaryClient = clientRepository.findById(primaryClientId)
            .orElseThrow(() -> new IllegalArgumentException("Primary client not found: " + primaryClientId.urn()));
        profileName = primaryClient.name();
    }

    List<Profile.ClientEnrollmentRequest> enrollmentRequests = new ArrayList<>();

    for (ClientAccountSelection selection : cmd.clientSelections()) {
        List<ClientAccountId> accountIds;

        if (selection.enrollmentType() == AccountEnrollmentType.AUTOMATIC) {
            // Fetch all active accounts for client
            accountIds = clientAccountRepository.findByClientId(selection.clientId())
                .stream()
                .filter(acc -> acc.status() == AccountStatus.ACTIVE)
                .map(ClientAccount::accountId)
                .toList();
        } else {
            // Use provided account IDs (MANUAL)
            accountIds = selection.accountIds();
        }

        enrollmentRequests.add(new Profile.ClientEnrollmentRequest(
            selection.clientId(),
            selection.isPrimary(),
            selection.enrollmentType(),
            accountIds
        ));
    }

    Profile profile = Profile.createWithAccounts(
        cmd.profileType(),
        profileName,
        enrollmentRequests,
        cmd.createdBy()
    );

    repository.save(profile);

    eventPublisher.publishEvent(new ProfileCreated(
        profile.profileId().urn(),
        profile.name(),
        profile.primaryClientId().urn(),
        profile.profileType().name(),
        profile.status().name(),
        cmd.createdBy(),
        Instant.now()
    ));

    return profile.profileId();
}

@Override
public List<ProfileSummary> findPrimaryProfiles(ClientId clientId) {
    return repository.findByPrimaryClient(clientId).stream()
        .map(this::toSummary)
        .toList();
}

@Override
public List<ProfileSummary> findSecondaryProfiles(ClientId clientId) {
    return repository.findBySecondaryClient(clientId).stream()
        .map(this::toSummary)
        .toList();
}

@Override
public List<ProfileSummary> findAllProfilesForClient(ClientId clientId) {
    return repository.findByClient(clientId).stream()
        .map(this::toSummary)
        .toList();
}

private ProfileSummary toSummary(Profile profile) {
    return new ProfileSummary(
        profile.profileId().urn(),
        profile.name(),
        profile.profileType().name(),
        profile.status().name(),
        profile.primaryClientId().urn(),
        profile.clientEnrollments().size(),
        profile.serviceEnrollments().size(),
        profile.accountEnrollments().size()
    );
}
```

---

### Step 12: Create ClientEnrollmentEntity

**File:** `application/src/main/java/com/knight/application/persistence/profiles/entity/ClientEnrollmentEntity.java`

```java
@Entity
@Table(name = "profile_client_enrollments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientEnrollmentEntity {

    @Id
    @Column(name = "id", nullable = false, length = 100)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private ProfileEntity profile;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "account_enrollment_type", nullable = false, length = 20)
    private String accountEnrollmentType;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;
}
```

---

### Step 13: Update ProfileEntity

**File:** `application/src/main/java/com/knight/application/persistence/profiles/entity/ProfileEntity.java`

- Remove `clientId` field (now derived from client enrollments)
- Add client enrollments collection:

```java
@Entity
@Table(name = "profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileEntity {

    @Id
    @Column(name = "profile_id", nullable = false, length = 200)
    private String profileId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // REMOVED: clientId field - now derived from clientEnrollments where isPrimary=true

    @Column(name = "profile_type", nullable = false, length = 20)
    private String profileType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ClientEnrollmentEntity> clientEnrollments = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ServiceEnrollmentEntity> serviceEnrollments = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<AccountEnrollmentEntity> accountEnrollments = new ArrayList<>();

    /**
     * Get primary client ID from client enrollments.
     */
    public String getPrimaryClientId() {
        return clientEnrollments.stream()
            .filter(ClientEnrollmentEntity::isPrimary)
            .findFirst()
            .map(ClientEnrollmentEntity::getClientId)
            .orElse(null);
    }
}
```

---

### Step 14: Update AccountEnrollmentEntity

**File:** `application/src/main/java/com/knight/application/persistence/profiles/entity/AccountEnrollmentEntity.java`

Add clientId column:
```java
@Column(name = "client_id", nullable = false, length = 100)
private String clientId;
```

---

### Step 15: Implement Profile Repository Adapter

**File:** `application/src/main/java/com/knight/application/persistence/profiles/repository/ProfileRepositoryAdapter.java`

```java
@Override
public List<Profile> findByPrimaryClient(ClientId clientId) {
    return jpaRepository.findByPrimaryClientId(clientId.urn()).stream()
        .map(mapper::toDomain)
        .toList();
}

@Override
public List<Profile> findBySecondaryClient(ClientId clientId) {
    return jpaRepository.findBySecondaryClientId(clientId.urn()).stream()
        .map(mapper::toDomain)
        .toList();
}

@Override
public List<Profile> findByClient(ClientId clientId) {
    return jpaRepository.findByClientId(clientId.urn()).stream()
        .map(mapper::toDomain)
        .toList();
}

@Override
public boolean existsServicingProfileWithPrimaryClient(ClientId clientId) {
    return jpaRepository.existsServicingProfileWithPrimaryClient(clientId.urn());
}
```

**File:** `application/src/main/java/com/knight/application/persistence/profiles/repository/ProfileJpaRepository.java`

```java
public interface ProfileJpaRepository extends JpaRepository<ProfileEntity, String> {

    @Query("SELECT p FROM ProfileEntity p JOIN p.clientEnrollments ce WHERE ce.clientId = :clientId AND ce.isPrimary = true")
    List<ProfileEntity> findByPrimaryClientId(@Param("clientId") String clientId);

    @Query("SELECT p FROM ProfileEntity p JOIN p.clientEnrollments ce WHERE ce.clientId = :clientId AND ce.isPrimary = false")
    List<ProfileEntity> findBySecondaryClientId(@Param("clientId") String clientId);

    @Query("SELECT p FROM ProfileEntity p JOIN p.clientEnrollments ce WHERE ce.clientId = :clientId")
    List<ProfileEntity> findByClientId(@Param("clientId") String clientId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM ProfileEntity p " +
           "JOIN p.clientEnrollments ce " +
           "WHERE ce.clientId = :clientId AND ce.isPrimary = true AND p.profileType = 'SERVICING'")
    boolean existsServicingProfileWithPrimaryClient(@Param("clientId") String clientId);
}
```

---

### Step 16: Update Profile Mapper

**File:** `application/src/main/java/com/knight/application/persistence/profiles/mapper/ProfileMapper.java`

Update `toEntity()`:
- Remove clientId mapping (derived from enrollments)
- Map clientEnrollments to ClientEnrollmentEntity list
- Include clientId in AccountEnrollmentEntity mapping

Update `toDomain()`:
- Remove clientId reconstruction
- Reconstruct clientEnrollments
- Include clientId when reconstructing AccountEnrollment

---

### Step 17: Consolidated Database Migration

Delete all existing migration files and create single file:

**File:** `application/src/main/resources/db/migration/V1__initial_schema.sql`

```sql
-- =====================================================
-- KNIGHT PLATFORM - CONSOLIDATED SCHEMA
-- Local development only - clean slate migration
-- =====================================================

-- Clients (with address, no taxId/phoneNumber/emailAddress)
CREATE TABLE clients (
    client_id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    client_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    -- Address fields
    address_street VARCHAR(255),
    address_city VARCHAR(100),
    address_state VARCHAR(100),
    address_postal_code VARCHAR(20),
    address_country VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_clients_name ON clients(name);
CREATE INDEX idx_clients_type ON clients(client_type);

-- Client Accounts
CREATE TABLE client_accounts (
    account_id VARCHAR(100) PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL REFERENCES clients(client_id),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_client_accounts_client ON client_accounts(client_id);

-- Profiles (no client_id column - derived from profile_client_enrollments)
CREATE TABLE profiles (
    profile_id VARCHAR(200) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    profile_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_profiles_type ON profiles(profile_type);
CREATE INDEX idx_profiles_name ON profiles(name);

-- Profile Client Enrollments (exactly one must have is_primary=true per profile)
CREATE TABLE profile_client_enrollments (
    id VARCHAR(100) PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL REFERENCES profiles(profile_id),
    client_id VARCHAR(100) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    account_enrollment_type VARCHAR(20) NOT NULL,
    enrolled_at TIMESTAMP NOT NULL,
    UNIQUE(profile_id, client_id)
);

CREATE INDEX idx_profile_client_enrollments_profile ON profile_client_enrollments(profile_id);
CREATE INDEX idx_profile_client_enrollments_client ON profile_client_enrollments(client_id);
CREATE INDEX idx_profile_client_enrollments_primary ON profile_client_enrollments(client_id, is_primary);

-- Service Enrollments
CREATE TABLE service_enrollments (
    enrollment_id VARCHAR(100) PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL REFERENCES profiles(profile_id),
    service_type VARCHAR(100) NOT NULL,
    configuration TEXT,
    status VARCHAR(20) NOT NULL,
    enrolled_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_service_enrollments_profile ON service_enrollments(profile_id);

-- Account Enrollments
CREATE TABLE account_enrollments (
    enrollment_id VARCHAR(100) PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL REFERENCES profiles(profile_id),
    service_enrollment_id VARCHAR(100) REFERENCES service_enrollments(enrollment_id),
    client_id VARCHAR(100) NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    enrolled_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_account_enrollments_profile ON account_enrollments(profile_id);
CREATE INDEX idx_account_enrollments_service ON account_enrollments(service_enrollment_id);
CREATE INDEX idx_account_enrollments_account ON account_enrollments(account_id);
CREATE INDEX idx_account_enrollments_client ON account_enrollments(client_id);

-- Users (linked to Profile via profile_id)
CREATE TABLE users (
    user_id VARCHAR(100) PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    user_type VARCHAR(20) NOT NULL,
    identity_provider VARCHAR(20) NOT NULL,
    profile_id VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    lock_reason VARCHAR(500),
    deactivation_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_profile ON users(profile_id);
```

---

### Step 18: REST API - Create Profile Endpoint

**File:** `application/src/main/java/com/knight/application/rest/profiles/ProfileController.java`

```java
@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileCommands profileCommands;
    private final ProfileQueries profileQueries;

    @PostMapping
    public ResponseEntity<CreateProfileResponse> createProfile(
        @RequestBody CreateProfileRequest request
    ) {
        // Map DTO to command
        // Execute createProfileWithAccounts
        // Return profile ID
    }

    @GetMapping("/by-primary-client/{clientId}")
    public ResponseEntity<List<ProfileSummaryDto>> findPrimaryProfiles(
        @PathVariable String clientId
    ) {
        // Call profileQueries.findPrimaryProfiles
    }

    @GetMapping("/by-secondary-client/{clientId}")
    public ResponseEntity<List<ProfileSummaryDto>> findSecondaryProfiles(
        @PathVariable String clientId
    ) {
        // Call profileQueries.findSecondaryProfiles
    }
}
```

**File:** `application/src/main/java/com/knight/application/rest/profiles/dto/CreateProfileRequest.java`

```java
public record CreateProfileRequest(
    String profileType,
    String name,  // Optional - defaults to primary client name
    List<ClientAccountSelectionDto> clients
) {}

public record ClientAccountSelectionDto(
    String clientId,
    boolean isPrimary,
    String accountEnrollmentType,
    List<String> accountIds
) {}

public record CreateProfileResponse(
    String profileId,
    String name
) {}

public record ProfileSummaryDto(
    String profileId,
    String name,
    String profileType,
    String status,
    String primaryClientId,
    int clientCount,
    int serviceEnrollmentCount,
    int accountEnrollmentCount
) {}
```

---

### Step 19: Portal Service - Profile API Client

**File:** `employee-portal/src/main/java/com/knight/portal/services/ProfileService.java`

```java
@Service
public class ProfileService {

    private final WebClient webClient;

    public String createProfile(CreateProfileRequest request) {
        // POST to /api/profiles
    }

    public List<ProfileSummary> findPrimaryProfiles(String clientId) {
        // GET /api/profiles/by-primary-client/{clientId}
    }

    public List<ProfileSummary> findSecondaryProfiles(String clientId) {
        // GET /api/profiles/by-secondary-client/{clientId}
    }

    public List<ClientAccount> getClientAccounts(String clientId) {
        // GET /api/clients/{clientId}/accounts
    }
}
```

---

### Step 20: Portal UI - Create Profile Wizard

**File:** `employee-portal/src/main/java/com/knight/portal/views/CreateProfileView.java`

Multi-step wizard:
1. **Profile Type** - Radio: SERVICING / ONLINE
2. **Primary Client Accounts** - Toggle: Automatic (all) / Manual (select)
3. **Secondary Clients** - Add button → search dialog → account selection
4. **Review & Confirm** - Summary table → Create button

---

### Step 21: Portal UI - Reusable Components

**File:** `employee-portal/src/main/java/com/knight/portal/components/ClientSearchDialog.java`
- Search by name or client number
- Grid with results
- Select and return

**File:** `employee-portal/src/main/java/com/knight/portal/components/AccountSelectionPanel.java`
- Toggle: Automatic / Manual
- When Manual: multi-select grid of ACTIVE accounts
- Shows account number, currency, status

---

### Step 22: Update Client Detail View - Profiles Tab

**File:** `employee-portal/src/main/java/com/knight/portal/views/ClientDetailView.java`

Update profiles tab to show:
- **Primary Profiles** - Profiles where this client is the primary client
- **Secondary Profiles** - Profiles where this client is a secondary client
- **Create Profile** button that navigates to wizard with primary client pre-selected

---

## Testing Checklist

- [ ] Client aggregate without taxId, taxNumber, phoneNumber, emailAddress (keeps address)
- [ ] Profile aggregate without clientId field (uses primaryClientId() derived getter)
- [ ] Profile aggregate without users collection
- [ ] Profile has name field (defaults to primary client name)
- [ ] Exactly one primary client validation works
- [ ] SERVICING profile: primary client cannot be primary in another servicing profile
- [ ] ONLINE profile: no restriction on primary client (can have multiple)
- [ ] AccountEnrollmentType enum works correctly
- [ ] ClientEnrollment entity persists correctly
- [ ] AccountEnrollment includes clientId
- [ ] createProfileWithAccounts with AUTOMATIC resolves only ACTIVE accounts
- [ ] createProfileWithAccounts with MANUAL uses provided accounts
- [ ] UI shows only ACTIVE accounts for selection
- [ ] findPrimaryProfiles returns correct profiles
- [ ] findSecondaryProfiles returns correct profiles
- [ ] REST endpoints work correctly
- [ ] Portal wizard completes full flow
- [ ] Database schema is correct after migration

---

## Design Decisions

1. **Post-creation modifications** - Yes, secondary clients can be added/removed after profile creation.
   - *Not in scope for initial implementation - future feature.*

2. **Account validation** - Only ACTIVE accounts can be enrolled.
   - UI should only display ACTIVE accounts for selection.
   - If account is closed after enrollment, enrollment remains but account won't be processed.

3. **Profile naming** - Yes, profiles have a `name` field.
   - Default: primary client's name
   - Can be overridden by user during creation

4. **Duplicate prevention** - Same account CAN be in multiple profiles, with one restriction:
   - A client can be primary in only ONE servicing profile
   - A client can be primary in MANY online profiles
   - Secondary client enrollments have no restrictions

5. **AUTOMATIC enrollment refresh** - Yes, when new accounts are added to a client with AUTOMATIC enrollment, they should auto-enroll.
   - *Not in scope for initial implementation - future batch process.*
