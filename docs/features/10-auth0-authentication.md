# Auth0 Authentication - Feature Specification

## Overview

This document defines the user stories for Auth0-based authentication, including First-Time Registration (FTR), normal login, password reset, MFA enrollment/re-enrollment, and cleanup tasks for removing deprecated passkey functionality.

**Related Document**: [Auth0 Authentication Requirements](../auth0-authentication-requirements.md)

---

## Epic 1: First-Time Registration (FTR)

### US-AUTH-001: Email Verification via OTP

**As a** new user completing registration
**I want** to verify my email using an OTP sent outside Auth0
**So that** I can confirm my email ownership before setting up my account

#### Acceptance Criteria

- [ ] OTP sent to user's email via AhaSend (not Auth0)
- [ ] 6-digit numeric code generated
- [ ] Code expires after 120 seconds
- [ ] Maximum 3 verification attempts per code
- [ ] Rate limited to 3 OTP requests per 60 seconds
- [ ] On successful verification, `email_verified` flag set to `true`
- [ ] User proceeds to password setup

#### Technical Notes

- OTP stored in Redis with TTL
- Purpose code: `ftr_email_verify`
- Endpoint: `POST /api/v1/ftr/send-otp`, `POST /api/v1/ftr/verify-otp`

#### Test Scenarios

1. Send OTP - success, returns `expires_in_seconds`
2. Verify correct OTP - success, `email_verified` updated
3. Verify incorrect OTP - failure, `attempts_remaining` decremented
4. Verify expired OTP - failure, must request new OTP
5. Rate limit exceeded - 429 response

---

### US-AUTH-002: Password Setup in Auth0

**As a** new user completing FTR
**I want** to set my password after email verification
**So that** I can secure my account with credentials stored in Auth0

#### Acceptance Criteria

- [ ] Password form displayed after email verification
- [ ] Password requirements validated:
  - Minimum 12 characters
  - At least 1 uppercase letter
  - At least 1 lowercase letter
  - At least 1 number
  - At least 1 special character
- [ ] Real-time password strength indicator
- [ ] Confirm password field with match validation
- [ ] Auth0 user created with password
- [ ] `password_set` flag set to `true`
- [ ] `identity_provider_user_id` stored in local database

#### Technical Notes

- Auth0 API: `POST /api/v2/users` (create user with password)
- Endpoint: `POST /api/v1/ftr/set-password`
- Auth0 stores login_id as `email` field

#### Test Scenarios

1. Set valid password - Auth0 user created
2. Password too short - validation error
3. Password missing special char - validation error
4. Passwords don't match - validation error
5. Auth0 API failure - graceful error handling

---

### US-AUTH-003: MFA Method Selection (Guardian OR TOTP)

**As a** new user completing FTR
**I want** to choose ONE MFA method (Guardian or TOTP)
**So that** I can secure my account with my preferred second factor

#### Acceptance Criteria

- [ ] MFA selection screen shown after password setup
- [ ] Two options presented: Guardian Push and TOTP
- [ ] User can only select ONE method
- [ ] Clear description of each method provided
- [ ] Selection proceeds to enrollment for chosen method
- [ ] `mfa_preference` stored as `GUARDIAN` or `TOTP`

#### Technical Notes

- MFA preference is immutable after initial enrollment (during FTR)
- Changing MFA method requires admin-initiated reset

#### UI Wireframe

```
┌─────────────────────────────────────────────────────────┐
│ Choose Your MFA Method                                   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│ Select how you'll verify your identity when logging in:  │
│                                                          │
│ ○ Guardian Push                                          │
│   Receive push notifications on your mobile device.      │
│   Tap to approve login requests.                         │
│                                                          │
│ ○ Authenticator App (TOTP)                               │
│   Use Google Authenticator, Authy, or similar apps.      │
│   Enter 6-digit codes when logging in.                   │
│                                                          │
│                                    [Continue]            │
└─────────────────────────────────────────────────────────┘
```

---

### US-AUTH-004: MFA Enrollment Completion

**As a** new user completing FTR
**I want** to complete MFA enrollment for my chosen method
**So that** my account is protected with two-factor authentication

#### Acceptance Criteria

**Guardian Enrollment:**
- [ ] QR code displayed for Guardian app scanning
- [ ] Instructions for downloading Auth0 Guardian app
- [ ] Test push notification sent after QR scan
- [ ] User approves test push to confirm enrollment

**TOTP Enrollment:**
- [ ] QR code displayed for authenticator app scanning
- [ ] Manual entry code provided as backup
- [ ] User enters 6-digit code to verify setup
- [ ] Code validated against Auth0

**Both Methods:**
- [ ] `mfa_enrolled` flag set to `true`
- [ ] `mfa_preference` matches selected method
- [ ] User proceeds to FTR completion

#### Technical Notes

- Guardian: Auth0 MFA Association API
- TOTP: Auth0 Enrollment endpoint with `otp` authenticator type
- Endpoints: `POST /api/v1/mfa/enroll-guardian`, `POST /api/v1/mfa/enroll-totp`

---

### US-AUTH-005: FTR Abandonment Handling

**As a** user who abandoned FTR mid-flow
**I want** the system to handle my return gracefully
**So that** I can resume registration without starting completely over

#### Acceptance Criteria

- [ ] FTR check endpoint determines current state
- [ ] If `email_verified=true` but `password_set=false`: Reset `email_verified` to `false`, restart from OTP
- [ ] If `password_set=true` but `mfa_enrolled=false`: Skip to MFA enrollment
- [ ] `password_set` is never reset (password exists in Auth0)
- [ ] User must re-verify email each fresh start
- [ ] FTR token remains valid for configurable period

#### State Machine

```
┌─────────────────────────────────────────────────────────────────┐
│                      FTR State Machine                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Initial State                                                   │
│  └─▶ email_verified=false, password_set=false, mfa_enrolled=false│
│                                                                  │
│  After Email Verify                                              │
│  └─▶ email_verified=true, password_set=false, mfa_enrolled=false │
│                                                                  │
│  After Abandonment (email verified but no password)              │
│  └─▶ RESET: email_verified=false                                 │
│                                                                  │
│  After Password Set                                              │
│  └─▶ email_verified=true, password_set=true, mfa_enrolled=false  │
│                                                                  │
│  After MFA Enrollment                                            │
│  └─▶ email_verified=true, password_set=true, mfa_enrolled=true   │
│  └─▶ status = ACTIVE                                             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### Technical Notes

- Check endpoint: `GET /api/v1/ftr/check?token={token}`
- Returns next required step based on state

---

## Epic 2: Authentication

### US-AUTH-010: Password Login

**As a** registered user
**I want** to log in with my login_id and password
**So that** I can access my account

#### Acceptance Criteria

- [ ] Login form accepts `login_id` (email format) and password
- [ ] Password validated against Auth0
- [ ] Failed attempts tracked
- [ ] Account lockout after 5 failed attempts
- [ ] "Forgot Password" link available
- [ ] On success, proceed to MFA challenge if enrolled

#### Technical Notes

- Auth0 Resource Owner Password Grant
- Endpoint: `POST /api/v1/auth/login`
- Returns `mfa_required: true` if MFA enrolled

---

### US-AUTH-011: MFA Challenge After Password

**As a** user with MFA enrolled
**I want** to complete MFA challenge after password
**So that** my login is protected with two-factor authentication

#### Acceptance Criteria

- [ ] Check `mfa_enrolled` flag after password auth
- [ ] If enrolled, require MFA challenge
- [ ] Challenge type based on `mfa_preference`
- [ ] Guardian: Send push notification, poll for approval
- [ ] TOTP: Prompt for 6-digit code
- [ ] MFA timeout after 2 minutes

#### Technical Notes

- Guardian challenge: Auth0 MFA Challenge API
- TOTP verification: Auth0 OOB verification
- Endpoints: `POST /api/v1/mfa/challenge/guardian`, `POST /api/v1/mfa/challenge/totp`

---

### US-AUTH-012: Token Issuance After Successful MFA

**As a** user completing login
**I want** to receive authentication tokens
**So that** I can access the application and perform step-up operations

#### Acceptance Criteria

- [ ] `access_token` issued (15-minute validity)
- [ ] `refresh_token` issued (8-hour validity)
- [ ] `mfa_token` issued (10-minute validity)
- [ ] `last_logged_in_at` updated
- [ ] Login event logged: `USER_LOGIN`

#### Token Purposes

| Token | Purpose |
|-------|---------|
| `access_token` | API authorization |
| `refresh_token` | Session renewal |
| `mfa_token` | Step-up authentication |

---

### US-AUTH-013: Session Management

**As a** security administrator
**I want** proper session management
**So that** user sessions are secure

#### Acceptance Criteria

- [ ] Session timeout after 30 minutes of inactivity
- [ ] Absolute session timeout after 8 hours
- [ ] Logout terminates session and clears tokens
- [ ] Sessions invalidated on password change
- [ ] Sessions invalidated on MFA reset
- [ ] Concurrent session limit (configurable)

---

## Epic 3: Password Reset

### US-AUTH-020: Forgot Password OTP Request

**As a** user who forgot my password
**I want** to request a password reset via email OTP
**So that** I can regain access to my account

#### Acceptance Criteria

- [ ] "Forgot Password" link on login screen
- [ ] User enters `login_id` or email
- [ ] OTP sent to registered email via AhaSend
- [ ] Same response returned whether user exists or not (security)
- [ ] Rate limited to 3 requests per 60 seconds

#### Technical Notes

- OTP purpose code: `password_reset`
- Endpoint: `POST /api/v1/auth/forgot-password`

---

### US-AUTH-021: Password Reset OTP Verification

**As a** user resetting my password
**I want** to verify my identity via OTP
**So that** I can securely reset my password

#### Acceptance Criteria

- [ ] OTP verification form displayed
- [ ] 6-digit code validated
- [ ] Maximum 3 attempts per code
- [ ] On success, `password_reset_token` issued
- [ ] Token valid for 5 minutes

#### Technical Notes

- Endpoint: `POST /api/v1/auth/verify-reset-otp`

---

### US-AUTH-022: New Password Setup in Auth0

**As a** user resetting my password
**I want** to set a new password
**So that** I can access my account with new credentials

#### Acceptance Criteria

- [ ] Password form requires `password_reset_token`
- [ ] Same password requirements as initial setup
- [ ] Password updated in Auth0
- [ ] All existing sessions invalidated
- [ ] Event logged: `USER_PASSWORD_RESET`
- [ ] User redirected to login

#### Technical Notes

- Auth0 API: `PATCH /api/v2/users/{user_id}`
- Endpoint: `POST /api/v1/auth/reset-password`

---

## Epic 4: MFA Re-Enrollment

### US-AUTH-030: Add allowMfaReenrollment Flag to User

**As a** developer
**I want** to add `allowMfaReenrollment` flag to User aggregate
**So that** admins can enable MFA re-enrollment for users

#### Acceptance Criteria

- [ ] `allowMfaReenrollment` boolean field added to User aggregate
- [ ] Default value: `false`
- [ ] Field persisted to database
- [ ] Migration: `V{N}__add_allow_mfa_reenrollment.sql`

#### Database Migration

```sql
ALTER TABLE users ADD COLUMN allow_mfa_reenrollment BOOLEAN DEFAULT false;
```

---

### US-AUTH-031: Admin Endpoint for Indirect Client MFA Reset

**As an** indirect client administrator
**I want** to reset MFA for users in my indirect client
**So that** I can help users who lost their MFA device

#### Acceptance Criteria

- [ ] Endpoint: `PUT /api/v1/indirect/users/{userId}/reset-mfa`
- [ ] Authorization: User must be admin of the indirect client
- [ ] Target user must belong to same indirect client
- [ ] Sets `allowMfaReenrollment = true`
- [ ] Deletes existing MFA enrollments from Auth0
- [ ] Sets `mfaEnrolled = false`
- [ ] Event logged: `USER_MFA_RESET`
- [ ] Email notification sent to user

#### Authorization Check

```java
// User must have ADMIN role for the indirect client
// Target user must be in the same indirect client
```

---

### US-AUTH-032: Admin Endpoint for Direct Client MFA Reset

**As a** direct client administrator
**I want** to reset MFA for users in my profile
**So that** I can help users who lost their MFA device

#### Acceptance Criteria

- [ ] Endpoint: `PUT /api/v1/client/users/{userId}/reset-mfa`
- [ ] Authorization: User must have ADMIN role for the profile
- [ ] Target user must belong to same profile
- [ ] Sets `allowMfaReenrollment = true`
- [ ] Deletes existing MFA enrollments from Auth0
- [ ] Sets `mfaEnrolled = false`
- [ ] Event logged: `USER_MFA_RESET`
- [ ] Email notification sent to user

#### Important Note

Bank employees (Employee Portal) are NOT permitted to reset MFA. Users must contact their client administrator.

---

### US-AUTH-033: MFA Re-Enrollment Flow During Login

**As a** user with `allowMfaReenrollment` flag set
**I want** to re-enroll MFA on my next login
**So that** I can regain access after losing my device

#### Acceptance Criteria

- [ ] After password authentication, check `allowMfaReenrollment` flag
- [ ] If `true`, redirect to MFA enrollment flow
- [ ] User selects Guardian OR TOTP
- [ ] User completes enrollment
- [ ] Flag NOT cleared until enrollment successful

#### Flow

```
Password Auth → Check Flag → MFA Enrollment → Clear Flag → Issue Tokens
```

---

### US-AUTH-034: Clear Flag After Successful Re-Enrollment

**As a** system
**I want** to clear `allowMfaReenrollment` after successful enrollment
**So that** the flag doesn't persist unnecessarily

#### Acceptance Criteria

- [ ] Flag cleared ONLY after successful MFA enrollment
- [ ] `mfaEnrolled` set to `true`
- [ ] `mfaPreference` updated to new method
- [ ] Event logged: `USER_MFA_ENROLLED`

---

## Epic 5: Step-Up Authentication

### US-AUTH-040: Step-Up Using mfa_token

**As a** user performing a sensitive operation
**I want** to use my `mfa_token` for step-up
**So that** I can complete operations without re-authenticating

#### Acceptance Criteria

- [ ] `mfa_token` validated for sensitive operations
- [ ] Token contains timestamp of MFA completion
- [ ] Operations proceed if token valid
- [ ] 401 returned if token missing or invalid

---

### US-AUTH-041: mfa_token Expiry Handling (10 min)

**As a** system
**I want** to enforce `mfa_token` expiry
**So that** step-up authentication remains secure

#### Acceptance Criteria

- [ ] `mfa_token` validity: 10 minutes
- [ ] Expired token returns 401 with `mfa_required: true`
- [ ] Client prompted to re-authenticate

---

### US-AUTH-042: Password Re-Entry to Refresh mfa_token

**As a** user with expired `mfa_token`
**I want** to re-enter my password and complete MFA
**So that** I can get a new `mfa_token` for step-up operations

#### Acceptance Criteria

- [ ] Step-up dialog prompts for password
- [ ] Password validated against Auth0
- [ ] MFA challenge triggered (Guardian or TOTP)
- [ ] New `mfa_token` issued on success
- [ ] Original operation can proceed

---

## Epic 6: Cleanup (Passkey & Guardian Reset Removal)

### US-AUTH-050: Remove PasskeyController

**As a** developer
**I want** to remove PasskeyController
**So that** we don't have dead code for unsupported features

#### Acceptance Criteria

- [ ] Delete `PasskeyController.java`
- [ ] Delete related test `PasskeyControllerTest.java`
- [ ] Remove from Spring context scanning
- [ ] Verify no compile errors

#### Files to Delete

- `application/src/main/java/com/knight/application/rest/login/PasskeyController.java`
- `application/src/test/java/com/knight/application/rest/login/PasskeyControllerTest.java`

---

### US-AUTH-051: Remove PasskeyFallbackController

**As a** developer
**I want** to remove PasskeyFallbackController
**So that** we don't have dead code for unsupported features

#### Acceptance Criteria

- [ ] Delete `PasskeyFallbackController.java`
- [ ] Delete related tests
- [ ] Verify no compile errors

#### Files to Delete

- `application/src/main/java/com/knight/application/rest/login/PasskeyFallbackController.java`

---

### US-AUTH-052: Remove Passkey Database Columns and Entities

**As a** developer
**I want** to remove passkey-related database columns
**So that** the schema doesn't have unused columns

#### Acceptance Criteria

- [ ] Create migration to drop passkey columns from users table
- [ ] Remove `passkeyOffered`, `passkeyEnrolled`, `passkeyHasUv` from User aggregate
- [ ] Remove from UserEntity
- [ ] Delete PasskeyEntity and repository
- [ ] Delete passkey DTOs

#### Files to Delete

- `application/src/main/java/com/knight/application/persistence/passkey/*`
- `application/src/main/java/com/knight/application/rest/login/dto/Passkey*.java`

#### Migration

```sql
ALTER TABLE users DROP COLUMN IF EXISTS passkey_offered;
ALTER TABLE users DROP COLUMN IF EXISTS passkey_enrolled;
ALTER TABLE users DROP COLUMN IF EXISTS passkey_has_uv;
```

---

### US-AUTH-053: Remove GuardianResetController

**As a** developer
**I want** to remove self-service GuardianResetController
**So that** MFA reset is admin-only

#### Acceptance Criteria

- [ ] Delete `GuardianResetController.java`
- [ ] Delete related test `GuardianResetControllerTest.java`
- [ ] Delete GuardianReset DTOs
- [ ] Verify no compile errors

#### Files to Delete

- `application/src/main/java/com/knight/application/rest/login/GuardianResetController.java`
- `application/src/test/java/com/knight/application/rest/login/GuardianResetControllerTest.java`
- `application/src/main/java/com/knight/application/rest/login/dto/GuardianResetSendOtpRequest.java`
- `application/src/main/java/com/knight/application/rest/login/dto/GuardianResetVerifyOtpRequest.java`

---

### US-AUTH-054: Update MfaPreference Enum

**As a** developer
**I want** to remove PASSKEY from MfaPreference enum
**So that** the enum only contains supported methods

#### Acceptance Criteria

- [ ] Remove `PASSKEY` value from `User.MfaPreference` enum
- [ ] Only `GUARDIAN` and `TOTP` remain
- [ ] Update any code referencing PASSKEY
- [ ] Verify no compile errors

#### Code Change

```java
public enum MfaPreference {
    GUARDIAN,
    TOTP
    // PASSKEY removed
}
```

---

## Implementation Priority

### Phase 1: Cleanup (Remove Dead Code)
1. US-AUTH-050: Remove PasskeyController
2. US-AUTH-051: Remove PasskeyFallbackController
3. US-AUTH-052: Remove Passkey Database Columns
4. US-AUTH-053: Remove GuardianResetController
5. US-AUTH-054: Update MfaPreference Enum

### Phase 2: MFA Re-Enrollment
1. US-AUTH-030: Add allowMfaReenrollment Flag
2. US-AUTH-031: Indirect Client Admin MFA Reset Endpoint
3. US-AUTH-032: Direct Client Admin MFA Reset Endpoint
4. US-AUTH-033: MFA Re-Enrollment Flow During Login
5. US-AUTH-034: Clear Flag After Re-Enrollment

### Phase 3: FTR Improvements
1. US-AUTH-003: MFA Method Selection (enforce single method)
2. US-AUTH-005: FTR Abandonment Handling

### Phase 4: Step-Up Authentication
1. US-AUTH-040: Step-Up Using mfa_token
2. US-AUTH-041: mfa_token Expiry Handling
3. US-AUTH-042: Password Re-Entry to Refresh mfa_token

---

## Dependencies

```
US-AUTH-050..054 (Cleanup)
       │
       ▼
US-AUTH-030 (Add Flag)
       │
       ├──▶ US-AUTH-031 (Indirect Admin Endpoint)
       │
       ├──▶ US-AUTH-032 (Direct Admin Endpoint)
       │
       └──▶ US-AUTH-033 (Re-Enrollment Flow)
                │
                └──▶ US-AUTH-034 (Clear Flag)

US-AUTH-003, US-AUTH-005 (FTR) - Can be done in parallel

US-AUTH-040..042 (Step-Up) - Depends on MFA being functional
```

---

## Events

| Event | Trigger | Data |
|-------|---------|------|
| `USER_EMAIL_VERIFIED` | FTR email OTP verified | user_id, timestamp |
| `USER_PASSWORD_SET` | Password created in Auth0 | user_id, timestamp |
| `USER_PASSWORD_RESET` | Password changed via reset flow | user_id, timestamp |
| `USER_MFA_ENROLLED` | MFA enrollment completed | user_id, method, timestamp |
| `USER_MFA_RESET` | Admin reset MFA flag | user_id, admin_id, timestamp |
| `USER_LOGIN` | Successful login | user_id, timestamp, ip |
| `USER_LOGIN_FAILED` | Failed login attempt | user_id, timestamp, reason |
| `USER_STEP_UP` | Step-up authentication completed | user_id, timestamp, operation |
