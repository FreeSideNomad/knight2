# User Management Design Document

## Overview

This document defines the design for managing users associated with Online and Indirect profiles. Users are displayed on the profile page as a tab and can be added for Indirect Profiles with Auth0 identity provisioning.

### Requirements Summary

1. **Display Users**: Online and Indirect profiles have users displayed as a tab on the profile page
2. **Add Users**: New users can be added to Indirect Profiles
3. **Auth0 Provisioning**: Indirect users require Auth0 identity provisioning via Management API
4. **Status Tracking**: User onboarding status is updated via Kafka events when users complete password setup and MFA enrollment

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Knight Platform                                    │
│                                                                                 │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────────────┐  │
│  │   Profile UI    │    │   User Service  │    │   Auth0 Identity Service    │  │
│  │   (Users Tab)   │───▶│   (Domain)      │───▶│   (Anti-corruption Layer)   │  │
│  └─────────────────┘    └─────────────────┘    └──────────────┬──────────────┘  │
│                                │                              │                 │
│                                ▼                              ▼                 │
│                         ┌─────────────┐              ┌─────────────────┐        │
│                         │  Database   │              │   Auth0 API     │        │
│                         │  (Users)    │              │   (Management)  │        │
│                         └─────────────┘              └─────────────────┘        │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ Kafka Events
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Auth0 Gateway (okta-app)                              │
│                                                                                 │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────────────┐  │
│  │  Auth0 Actions  │───▶│  Event Publisher│───▶│   Kafka                     │  │
│  │  (Post-Login)   │    │                 │    │   user.onboarding.completed │  │
│  └─────────────────┘    └─────────────────┘    └─────────────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Domain Model

### User Aggregate (Enhanced)

The existing `User` aggregate needs enhancement to support Auth0 provisioning lifecycle.

```java
package com.knight.domain.users.aggregate;

public class User {

    public enum Status {
        PENDING_CREATION,      // User created locally, not yet in Auth0
        PENDING_VERIFICATION,  // Created in Auth0, password not yet set
        PENDING_MFA,           // Password set, MFA not enrolled
        ACTIVE,                // Fully onboarded
        LOCKED,                // Account locked
        DEACTIVATED            // Account deactivated
    }

    public enum UserType {
        CLIENT_USER,    // Direct client users (ANP)
        INDIRECT_USER   // Indirect client users (Auth0 provisioned)
    }

    public enum IdentityProvider {
        AUTH0,      // For indirect client users
        ANP         // Legacy system
    }

    public enum Role {
        SECURITY_ADMIN,  // Manages users and security settings
        SERVICE_ADMIN,   // Manages service configuration
        READER,          // Read-only access
        CREATOR,         // Can create transactions/records
        APPROVER         // Can approve transactions/records
    }

    // Core fields
    private final UserId id;
    private final String email;
    private String firstName;
    private String lastName;
    private final UserType userType;
    private final IdentityProvider identityProvider;
    private final ProfileId profileId;
    private final Set<Role> roles;  // User can have multiple roles

    private String identityProviderUserId;
    private boolean passwordSet;
    private boolean mfaEnrolled;
    private Instant lastSyncedAt;

    // Status tracking
    private Status status;
    private String lockReason;
    private String deactivationReason;

    // Audit
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;
}
```

### User Lifecycle State Machine

```
                    ┌──────────────────────┐
                    │  PENDING_CREATION    │  User created in Knight DB
                    │  (Initial State)     │  Not yet provisioned to Auth0
                    └──────────┬───────────┘
                               │
                               │ provisionToAuth0()
                               │ - Calls Auth0 Management API
                               │ - Creates user 
                               │ 
                               ▼
                    ┌──────────────────────┐
                    │ PENDING_VERIFICATION │  User exists in Auth0
                    │                      │  
                    └──────────┬───────────┘
                               │
                               │ Kafka: user.email.verified
                               │ (User set password)
                               ▼
                    ┌──────────────────────┐
                    │    PENDING_MFA       │  Password set
                    │                      │  MFA enrollment required
                    └──────────┬───────────┘
                               │
                               │ Kafka: user.mfa.enrolled
                               │ (User completed Guardian push enrollment)
                               ▼
                    ┌──────────────────────┐
                    │       ACTIVE         │  Fully onboarded
                    │                      │  Can access application
                    └──────────────────────┘
                               │
               ┌───────────────┼───────────────┐
               │               │               │
               ▼               ▼               ▼
        ┌──────────┐    ┌───────────┐   ┌──────────────┐
        │  LOCKED  │    │DEACTIVATED│   │   (stays     │
        │          │◄──▶│           │   │   ACTIVE)    │
        └──────────┘    └───────────┘   └──────────────┘
```

---

## Auth0 Integration

### Auth0 Identity Service (Enhanced)

The existing `Auth0IdentityService` interface needs additional methods for the full provisioning workflow.

```java
package com.knight.domain.auth0identity.api;

public interface Auth0IdentityService {

    /**
     * Provisions a new user in Auth0.
     * Creates user with temporary password and triggers password reset email.
     *
     * @param request the provisioning request
     * @return the provisioning result with Auth0 user ID and reset URL
     */
    ProvisionUserResult provisionUser(ProvisionUserRequest request);

    /**
     * Gets the current onboarding status from Auth0.
     * Checks password set and MFA enrollment status.
     */
    OnboardingStatus getOnboardingStatus(String identityProviderUserId);

    /**
     * Resends the password reset email.
     */
    String resendPasswordResetEmail(String identityProviderUserId);

    // Request/Response records

    record ProvisionUserRequest(
        String email,
        String firstName,
        String lastName,
        String internalUserId,
        String profileId
    ) {}

    record ProvisionUserResult(
        String identityProviderUserId,
        String passwordResetUrl,
        Instant provisionedAt
    ) {}

    record OnboardingStatus(
        String identityProviderUserId,
        boolean passwordSet,
        boolean mfaEnrolled,
        String onboardingState,  // pending_verification, pending_mfa, complete
        Instant lastLogin
    ) {}
}
```

### Auth0 User Creation Flow

Based on the `okta-app/scripts/provisioning/create-user.sh` pattern:

```java
@Service
public class Auth0IdentityAdapter implements Auth0IdentityService {

    @Override
    public ProvisionUserResult provisionUser(ProvisionUserRequest request) {
        // 1. Check if user already exists
        Optional<Auth0UserInfo> existing = getUserByEmail(request.email());
        if (existing.isPresent()) {
            throw new UserAlreadyExistsException(request.email());
        }

        // 2. Generate temporary password
        String tempPassword = generateSecurePassword();

        // 3. Create user in Auth0
        // POST https://{domain}/api/v2/users
        String identityProviderUserId = createAuth0User(
            request.email(),
            request.firstName(),
            request.lastName(),
            tempPassword,
            Map.of(
                "internal_user_id", request.internalUserId(),
                "profile_id", request.profileId(),
                "provisioned_by", "knight_platform",
                "provisioned_at", Instant.now().toString(),
                "onboarding_status", "pending"
            )
        );

        // 4. Generate password reset ticket
        // POST https://{domain}/api/v2/tickets/password-change
        String resetUrl = createPasswordChangeTicket(identityProviderUserId);

        // 5. Publish event
        eventPublisher.publishEvent(new Auth0UserProvisioned(
            identityProviderUserId,
            request.email(),
            request.internalUserId(),
            resetUrl,
            Instant.now()
        ));

        return new ProvisionUserResult(identityProviderUserId, resetUrl, Instant.now());
    }
}
```

### Auth0 App Metadata Structure

```json
{
  "app_metadata": {
    "internal_user_id": "uuid-from-knight-db",
    "profile_id": "ONLINE:srf:123456",
    "provisioned_by": "knight_platform",
    "provisioned_at": "2024-01-15T10:30:00Z",
    "onboarding_status": "pending",
    "mfa_enrolled": false
  }
}
```

---

## Kafka Event Integration

### Event Topics

| Topic | Publisher | Consumer | Description |
|-------|-----------|----------|-------------|
| `user.onboarding.password-set` | okta-app | knight | User set their password |
| `user.onboarding.mfa-enrolled` | okta-app | knight | User completed MFA enrollment |
| `user.onboarding.completed` | okta-app | knight | User fully onboarded (first login after MFA) |

### Event Schemas

```java
// Published by okta-app when user sets their password
record UserPasswordSetEvent(
    String identityProviderUserId,
    String email,
    String internalUserId,
    Instant passwordSetAt
) {}

// Published by okta-app when user enrolls in MFA
record UserMfaEnrolledEvent(
    String identityProviderUserId,
    String email,
    String internalUserId,
    String mfaFactorType,  // "guardian", "totp", etc.
    Instant enrolledAt
) {}

// Published by okta-app on first successful login
record UserOnboardingCompletedEvent(
    String identityProviderUserId,
    String email,
    String internalUserId,
    Instant completedAt
) {}
```

### Kafka Consumer

```java
@Component
public class UserOnboardingEventConsumer {

    private final UserApplicationService userService;

    @KafkaListener(topics = "user.onboarding.password-set")
    public void handlePasswordSet(UserPasswordSetEvent event) {
        userService.updateOnboardingStatus(
            event.internalUserId(),
            User.Status.PENDING_MFA,
            true,   // passwordSet
            false   // mfaEnrolled
        );
    }

    @KafkaListener(topics = "user.onboarding.mfa-enrolled")
    public void handleMfaEnrolled(UserMfaEnrolledEvent event) {
        userService.updateOnboardingStatus(
            event.internalUserId(),
            User.Status.ACTIVE,
            true,  // passwordSet
            true   // mfaEnrolled
        );
    }
}
```

---

## API Design

### REST Endpoints

#### Profile Users API

```
GET    /api/profiles/{profileId}/users          # List users for a profile
POST   /api/profiles/{profileId}/users          # Add user to profile (Indirect only)
GET    /api/profiles/{profileId}/users/{userId} # Get user details
DELETE /api/profiles/{profileId}/users/{userId} # Remove user from profile
```

#### User Management API

```
POST   /api/users/{userId}/provision            # Trigger Auth0 provisioning
POST   /api/users/{userId}/resend-invitation    # Resend password reset email
POST   /api/users/{userId}/sync                 # Sync status from Auth0
PUT    /api/users/{userId}/lock                 # Lock user
PUT    /api/users/{userId}/unlock               # Unlock user
PUT    /api/users/{userId}/deactivate           # Deactivate user
```

### DTOs

```java
// Request to add user to profile
record AddUserRequest(
    String email,
    String firstName,
    String lastName,
    Set<String> roles  // e.g. ["SECURITY_ADMIN", "READER"]
) {}

// Response after adding user
record AddUserResponse(
    String userId,
    String email,
    String firstName,
    String lastName,
    String status,
    Set<String> roles,
    String passwordResetUrl,  // URL to send to user
    Instant createdAt
) {}

// User list item for profile page
record ProfileUserDto(
    String userId,
    String email,
    String firstName,
    String lastName,
    String status,
    String statusDisplayName,
    Set<String> roles,
    boolean canResendInvitation,
    boolean canLock,
    boolean canDeactivate,
    Instant createdAt,
    Instant lastLogin
) {}

// Detailed user view
record UserDetailDto(
    String userId,
    String email,
    String firstName,
    String lastName,
    String status,
    String userType,
    String identityProvider,
    String profileId,
    String identityProviderUserId,
    Set<String> roles,
    boolean passwordSet,
    boolean mfaEnrolled,
    Instant createdAt,
    Instant lastSyncedAt,
    String lockReason,
    String deactivationReason
) {}
```

---

## Database Schema

### Users Table (Enhanced)

```sql
-- Enhanced users table for Auth0 integration
CREATE TABLE users (
    user_id UNIQUEIDENTIFIER PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name NVARCHAR(100),
    last_name NVARCHAR(100),
    user_type VARCHAR(20) NOT NULL,           -- DIRECT, INDIRECT
    identity_provider VARCHAR(20) NOT NULL,   -- AUTH0, ENTRA_ID, ANP
    profile_id VARCHAR(200) NOT NULL,

    -- Identity provider fields
    identity_provider_user_id VARCHAR(255) UNIQUE,  -- e.g. auth0|xxx (null until provisioned)
    password_set BIT NOT NULL DEFAULT 0,
    mfa_enrolled BIT NOT NULL DEFAULT 0,
    last_synced_at DATETIME2,

    -- Status
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_CREATION',
    lock_reason NVARCHAR(500),
    deactivation_reason NVARCHAR(500),

    -- Audit
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT CHK_user_status CHECK (status IN (
        'PENDING_CREATION',
        'PENDING_VERIFICATION',
        'PENDING_MFA',
        'ACTIVE',
        'LOCKED',
        'DEACTIVATED'
    )),

    CONSTRAINT FK_users_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_profile ON users(profile_id);
CREATE INDEX idx_users_idp_user_id ON users(identity_provider_user_id);
CREATE INDEX idx_users_status ON users(status);

-- User Roles (many-to-many relationship)
CREATE TABLE user_roles (
    user_id UNIQUEIDENTIFIER NOT NULL,
    role VARCHAR(20) NOT NULL,           -- SECURITY_ADMIN, SERVICE_ADMIN, READER, CREATOR, APPROVER
    assigned_at DATETIME2 NOT NULL,
    assigned_by NVARCHAR(255) NOT NULL,

    PRIMARY KEY (user_id, role),
    CONSTRAINT FK_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT CHK_user_role CHECK (role IN (
        'SECURITY_ADMIN',
        'SERVICE_ADMIN',
        'READER',
        'CREATOR',
        'APPROVER'
    ))
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role);
```

### Migration Script

```sql
-- V2__enhance_users_for_auth0.sql

-- Add new columns to existing users table
ALTER TABLE users ADD first_name NVARCHAR(100);
ALTER TABLE users ADD last_name NVARCHAR(100);
ALTER TABLE users ADD identity_provider_user_id VARCHAR(255);
ALTER TABLE users ADD password_set BIT NOT NULL DEFAULT 0;
ALTER TABLE users ADD mfa_enrolled BIT NOT NULL DEFAULT 0;
ALTER TABLE users ADD last_synced_at DATETIME2;
ALTER TABLE users ADD created_by NVARCHAR(255);

-- Update status enum values
-- Note: Existing PENDING becomes PENDING_CREATION
UPDATE users SET status = 'PENDING_CREATION' WHERE status = 'PENDING';

-- Add new indexes
CREATE UNIQUE INDEX idx_users_idp_user_id ON users(identity_provider_user_id)
    WHERE identity_provider_user_id IS NOT NULL;

-- Create user_roles table
CREATE TABLE user_roles (
    user_id UNIQUEIDENTIFIER NOT NULL,
    role VARCHAR(20) NOT NULL,
    assigned_at DATETIME2 NOT NULL,
    assigned_by NVARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT FK_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT CHK_user_role CHECK (role IN (
        'SECURITY_ADMIN', 'SERVICE_ADMIN', 'READER', 'CREATOR', 'APPROVER'
    ))
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role);
```

---

## UI Components

### Profile Page - Users Tab

The users tab displays on the profile detail page for ONLINE and profiles with IndirectClients.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  Profile: ABC Corporation Online                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│  [Overview] [Accounts] [Services] [Users]                                    │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Users (3)                                          [+ Add User]             │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │ Email                  │ Name           │ Status              │ Actions │ │
│  ├────────────────────────┼────────────────┼─────────────────────┼─────────┤ │
│  │ john@example.com       │ John Smith     │ ● Active            │ ⋮       │ │
│  │ jane@example.com       │ Jane Doe       │ ◐ Pending MFA       │ ⋮       │ │
│  │ bob@example.com        │ Bob Wilson     │ ○ Pending Password  │ ⋮       │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### Status Indicators

| Status | Display | Icon | Color |
|--------|---------|------|-------|
| PENDING_CREATION | Pending Creation | ○ | Gray |
| PENDING_VERIFICATION | Pending Password | ○ | Yellow |
| PENDING_MFA | Pending MFA | ◐ | Orange |
| ACTIVE | Active | ● | Green |
| LOCKED | Locked | 🔒 | Red |
| DEACTIVATED | Deactivated | ○ | Gray |

### Add User Dialog

```
┌─────────────────────────────────────────────────────────┐
│  Add User                                          [X]  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Email *                                                │
│  ┌─────────────────────────────────────────────────┐   │
│  │ user@example.com                                │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  First Name                                             │
│  ┌─────────────────────────────────────────────────┐   │
│  │ John                                            │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  Last Name                                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Smith                                           │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ℹ️ User will receive an email invitation to set       │
│     their password and enroll in MFA.                  │
│                                                         │
│                          [Cancel]  [Add & Send Invite]  │
└─────────────────────────────────────────────────────────┘
```

### User Actions Menu

```
┌─────────────────────────┐
│ Resend Invitation       │  (only for PENDING_VERIFICATION)
│ View Details            │
│ ──────────────────────  │
│ Lock User               │  (only for ACTIVE)
│ Unlock User             │  (only for LOCKED)
│ ──────────────────────  │
│ Deactivate              │  (for ACTIVE/LOCKED)
└─────────────────────────┘
```

---

## Domain Commands & Queries

### User Commands (Enhanced)

```java
public interface UserCommands {

    // Create user (local only, not provisioned)
    UserId createUser(CreateUserCmd cmd);

    record CreateUserCmd(
        String email,
        String firstName,
        String lastName,
        String userType,
        String identityProvider,
        ProfileId profileId,
        Set<String> roles,  // e.g. ["SECURITY_ADMIN", "READER"]
        String createdBy
    ) {}

    // Provision user to Auth0
    ProvisionResult provisionUser(ProvisionUserCmd cmd);

    record ProvisionUserCmd(UserId userId) {}

    record ProvisionResult(
        String identityProviderUserId,
        String passwordResetUrl
    ) {}

    // Update onboarding status (from Kafka events)
    void updateOnboardingStatus(UpdateOnboardingStatusCmd cmd);

    record UpdateOnboardingStatusCmd(
        String identityProviderUserId,
        boolean passwordSet,
        boolean mfaEnrolled
    ) {}

    // Resend invitation
    String resendInvitation(ResendInvitationCmd cmd);

    record ResendInvitationCmd(UserId userId) {}

    // Existing commands...
    void activateUser(ActivateUserCmd cmd);
    void deactivateUser(DeactivateUserCmd cmd);
    void lockUser(LockUserCmd cmd);
    void unlockUser(UnlockUserCmd cmd);
}
```

### User Queries (Enhanced)

```java
public interface UserQueries {

    // List users for a profile
    List<ProfileUserSummary> listUsersByProfile(ProfileId profileId);

    record ProfileUserSummary(
        String userId,
        String email,
        String firstName,
        String lastName,
        String status,
        String statusDisplayName,
        Set<String> roles,
        Instant createdAt,
        Instant lastLogin
    ) {}

    // Get detailed user info
    UserDetail getUserDetail(UserId userId);

    record UserDetail(
        String userId,
        String email,
        String firstName,
        String lastName,
        String status,
        String userType,
        String identityProvider,
        String profileId,
        String identityProviderUserId,
        Set<String> roles,
        boolean passwordSet,
        boolean mfaEnrolled,
        Instant createdAt,
        String createdBy,
        Instant lastSyncedAt,
        String lockReason,
        String deactivationReason
    ) {}

    // Count users by status for profile
    Map<String, Integer> countUsersByStatusForProfile(ProfileId profileId);
}
```

---

## Implementation Phases

### Phase 1: Domain Model Updates
1. Enhance `User` aggregate with Auth0 fields
2. Update `UserCommands` and `UserQueries` interfaces
3. Update `UserApplicationService` with new operations
4. Add database migration for schema changes

### Phase 2: Auth0 Integration
1. Implement `Auth0IdentityAdapter` with actual API calls
2. Add `Auth0TokenAdapter` for management token handling
3. Configure Auth0 connection properties
4. Add HTTP client for Auth0 Management API

### Phase 3: Kafka Integration
1. Define event schemas
2. Implement Kafka consumer for onboarding events
3. Configure Kafka topics and consumer groups
4. Add error handling and retry logic

### Phase 4: REST API
1. Add `ProfileUsersController` for profile-scoped user operations
2. Add `UserManagementController` for user lifecycle operations
3. Implement DTOs and mappers
4. Add validation and error handling

### Phase 5: UI Components
1. Add Users tab to Profile detail page
2. Implement user list with status indicators
3. Add "Add User" dialog with form validation
4. Implement action menu for user operations

---

## Security Considerations

1. **Auth0 Credentials**: Store M2M client credentials securely (environment variables or secrets manager)
2. **Password Reset URLs**: URLs are time-limited (7 days) and single-use
3. **Email Validation**: Validate email format and check for existing users before provisioning
4. **Profile Access**: Only users with access to the profile can view/manage its users
5. **Audit Logging**: Log all user provisioning and status changes
6. **Rate Limiting**: Apply rate limits to invitation resend to prevent abuse

---

## Error Handling

| Scenario | Handling |
|----------|----------|
| User already exists in Auth0 | Return error with existing user info |
| Auth0 API unavailable | Queue provisioning request for retry |
| Invalid email format | Return validation error |
| Profile not found | Return 404 |
| User not found | Return 404 |
| Kafka consumer failure | Retry with exponential backoff, DLQ for failures |

---

## Testing Strategy

### Unit Tests
- User aggregate state transitions
- Command validation
- Status calculation logic

### Integration Tests
- Auth0 API mocking with WireMock
- Kafka consumer with embedded Kafka
- Database repository tests

### E2E Tests
- Complete user provisioning flow
- Onboarding status updates via Kafka
- UI interaction tests

---

## Dependencies

### New Dependencies Required

```xml
<!-- Auth0 Java SDK -->
<dependency>
    <groupId>com.auth0</groupId>
    <artifactId>auth0</artifactId>
    <version>2.x.x</version>
</dependency>

<!-- Kafka -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

### Configuration Properties

```yaml
# application.yml
auth0:
  domain: ${AUTH0_DOMAIN}
  client-id: ${AUTH0_M2M_CLIENT_ID}
  client-secret: ${AUTH0_M2M_CLIENT_SECRET}
  audience: https://${AUTH0_DOMAIN}/api/v2/
  connection: Username-Password-Authentication

kafka:
  bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
  consumer:
    group-id: knight-user-onboarding
    auto-offset-reset: earliest
  topics:
    user-password-set: user.onboarding.password-set
    user-mfa-enrolled: user.onboarding.mfa-enrolled
    user-onboarding-completed: user.onboarding.completed
```

---

## Design Decisions

1. **User-Profile Linking**: Users are always linked to profiles, never directly to clients. The `profileId` is the primary association.

2. **Single Profile Per User**: A user can belong to only one profile. This is a 1:N relationship (profile has many users, user belongs to one profile).

3. **User Roles**: Users can have multiple roles within a profile:
   - `SECURITY_ADMIN` - Manages users and security settings
   - `SERVICE_ADMIN` - Manages service configuration
   - `READER` - Read-only access
   - `CREATOR` - Can create transactions/records
   - `APPROVER` - Can approve transactions/records

   Role-based permissions will be defined in subsequent requirements.

4. **Bulk Import**: Supported for Indirect Clients. The bulk import process will:
   - Create IndirectClient entities
   - Create Indirect profiles
   - Enroll into PayorService
   - Create admin users with `SECURITY_ADMIN` role

   Detailed requirements to be specified separately.

---

## References

- [Auth0 Management API Documentation](https://auth0.com/docs/api/management/v2)
- [Auth0 User Provisioning Best Practices](https://auth0.com/docs/manage-users)
- `okta-app/scripts/provisioning/create-user.sh` - Reference implementation
- `okta-app/docs/spec.md` - Full Auth0 gateway specification
