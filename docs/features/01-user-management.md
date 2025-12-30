# User Management

## Overview

User management encompasses the creation, modification, and lifecycle management of users across the platform. This includes tracking user activity, managing user states, and handling user-related events.

## Data Model Changes

### User Entity Updates

| Field | Type | Description |
|-------|------|-------------|
| `last_logged_in_at` | `TIMESTAMP` | Updated on each successful login event |
| `last_synced_at` | `TIMESTAMP` | Updated on non-login events (password set, MFA enrolled) |
| `lock_type` | `ENUM` | Replaces `lock_reason`. Values: `NONE`, `CLIENT`, `BANK`, `SECURITY` |
| `login_id` | `VARCHAR` | Separate from email, used for authentication |
| `email_verified` | `BOOLEAN` | Tracks if user has verified email ownership |
| `email_verified_at` | `TIMESTAMP` | When email was verified |
| `deleted` | `BOOLEAN` | Soft delete flag |
| `deleted_at` | `TIMESTAMP` | When user was soft deleted |
| `deleted_by` | `VARCHAR` | Who performed the deletion |

### Lock Type Hierarchy

```
NONE < CLIENT < BANK < SECURITY
```

- **NONE**: User is not locked
- **CLIENT**: Locked by Client Administrator (can be unlocked by Client Admin, Bank Admin, or Security Admin)
- **BANK**: Locked by Bank Administrator (can be unlocked by Bank Admin or Security Admin)
- **SECURITY**: Locked by Security Administrator (can only be unlocked by Security Admin)

---

## User Stories

### US-UM-001: Track User Login Time

**As a** system administrator
**I want** to track when users last logged in
**So that** I can identify inactive accounts and monitor user activity

#### Acceptance Criteria

- [ ] `last_logged_in_at` column added to user table
- [ ] Login event updates `last_logged_in_at` timestamp
- [ ] `last_logged_in_at` displayed in user list and detail views
- [ ] Column is nullable (null means never logged in)

#### Technical Notes

- Subscribe to Auth0 login webhook/event
- Update user record on successful authentication

---

### US-UM-002: Separate Sync Events from Login

**As a** system administrator
**I want** to distinguish between login events and other sync events
**So that** I can accurately track different types of user activity

#### Acceptance Criteria

- [ ] `last_synced_at` column tracks non-login events
- [ ] Password change updates `last_synced_at`
- [ ] MFA enrollment updates `last_synced_at`
- [ ] Profile updates from Auth0 update `last_synced_at`

#### Technical Notes

- Create event handlers for: `PASSWORD_SET`, `MFA_ENROLLED`, `PROFILE_UPDATED`
- Consider event sourcing for full audit trail

---

### US-UM-003: Lock Type Implementation

**As a** security administrator
**I want** a hierarchical lock system
**So that** different administrator levels can manage user access appropriately

#### Acceptance Criteria

- [ ] `lock_type` enum replaces `lock_reason` field
- [ ] Client Administrator can set/remove `CLIENT` lock
- [ ] Bank Administrator can set/remove `CLIENT` and `BANK` locks
- [ ] Security Administrator can set/remove all lock types
- [ ] UI shows current lock type and who can unlock
- [ ] Lock prevents user from logging in

#### Technical Notes

- Migration script to convert existing `lock_reason` to `lock_type`
- Add `locked_by` and `locked_at` audit fields

---

### US-UM-004: User Login ID Separate from Email

**As a** system administrator
**I want** users to have a login ID separate from their email
**So that** users can have organizational login names while receiving communications at their email

#### Acceptance Criteria

- [ ] `login_id` field added to user entity
- [ ] `login_id` must be unique within the system
- [ ] Email used for communications and verification
- [ ] Login ID used for authentication
- [ ] Both displayed in user management UI

#### Technical Notes

- Auth0 Configuration: Use `username` field for login ID
- Research Auth0 custom database connections or Actions for mapping

---

### US-UM-005: Create New User

**As a** Client Administrator
**I want** to create new users
**So that** I can grant system access to team members

#### Acceptance Criteria

- [ ] Form captures: login ID, email, name, roles
- [ ] Validation ensures unique login ID and email
- [ ] User created in local database with `PENDING_VERIFICATION` status
- [ ] Registration invitation sent to user's email
- [ ] Email contains link to complete registration

#### Technical Notes

- Auth0: Use passwordless email OTP for initial verification
- Store user in local DB before Auth0 creation
- Consider invitation expiry (e.g., 7 days)

---

### US-UM-006: User Registration Flow

**As a** new user
**I want** to complete my registration after receiving an invitation
**So that** I can access the system securely

#### Acceptance Criteria

- [ ] User clicks invitation link
- [ ] User enters their login ID
- [ ] System sends OTP to registered email
- [ ] User enters OTP to verify email ownership
- [ ] User sets password meeting complexity requirements
- [ ] User enrolls in MFA (Guardian push or passkey)
- [ ] User status updated to `ACTIVE`
- [ ] `email_verified` flag set to true

#### Flow

```
1. Click invitation link
2. Enter Login ID → Verify matches pending user
3. Send OTP to email
4. Enter OTP → Verify email ownership
5. Set password
6. Enroll MFA (Guardian/Passkey)
7. Complete → Status: ACTIVE
```

---

### US-UM-007: Soft Delete User

**As a** Client Administrator
**I want** to delete users
**So that** I can remove access for departed team members while maintaining audit history

#### Acceptance Criteria

- [ ] Delete action sets `deleted = true`
- [ ] `deleted_at` and `deleted_by` captured
- [ ] Deleted users hidden from UI lists
- [ ] Deleted users cannot log in
- [ ] User data retained in database for audit
- [ ] Auth0 user blocked/deleted

#### Technical Notes

- Add `deleted` filter to all user queries by default
- Consider data retention policy for hard delete after X years

---

### US-UM-008: Re-enroll MFA

**As a** Client Administrator
**I want** to force MFA re-enrollment for a user
**So that** I can help users who lost their MFA device

#### Acceptance Criteria

- [ ] "Re-enroll MFA" action available in user management
- [ ] Action clears user's MFA enrollment in Auth0
- [ ] User prompted to enroll MFA on next login
- [ ] Event logged with administrator who initiated
- [ ] User notified via email

#### Technical Notes

- Auth0 Management API: Delete MFA enrollments
- Set flag in user metadata for re-enrollment required

---

### US-UM-009: View User Activity

**As a** Client Administrator
**I want** to view user activity history
**So that** I can audit user access and troubleshoot issues

#### Acceptance Criteria

- [ ] User detail page shows activity timeline
- [ ] Activities include: logins, password changes, MFA events, lock/unlock
- [ ] Activities show timestamp and IP address where applicable
- [ ] Filter by activity type and date range

---

### US-UM-010: User List with Filters

**As a** Client Administrator
**I want** to view and filter the user list
**So that** I can efficiently manage users

#### Acceptance Criteria

- [ ] Grid displays: Email, Name, Login ID, Status, Lock Type, Last Login, Roles
- [ ] Filter by status (Active, Pending, Locked, Deleted)
- [ ] Filter by role
- [ ] Search by name, email, or login ID
- [ ] Sort by any column
- [ ] Export to CSV

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/users` | List users with filters |
| `POST` | `/api/users` | Create new user |
| `GET` | `/api/users/{id}` | Get user details |
| `PUT` | `/api/users/{id}` | Update user |
| `DELETE` | `/api/users/{id}` | Soft delete user |
| `POST` | `/api/users/{id}/lock` | Lock user |
| `POST` | `/api/users/{id}/unlock` | Unlock user |
| `POST` | `/api/users/{id}/re-enroll-mfa` | Force MFA re-enrollment |
| `GET` | `/api/users/{id}/activity` | Get user activity |

---

## Events

| Event | Trigger | Updates |
|-------|---------|---------|
| `USER_CREATED` | New user created | - |
| `USER_LOGIN` | Successful login | `last_logged_in_at` |
| `USER_PASSWORD_SET` | Password changed | `last_synced_at` |
| `USER_MFA_ENROLLED` | MFA setup complete | `last_synced_at`, `email_verified` |
| `USER_EMAIL_VERIFIED` | OTP verified | `email_verified`, `email_verified_at` |
| `USER_LOCKED` | User locked | `lock_type`, `locked_at`, `locked_by` |
| `USER_UNLOCKED` | User unlocked | `lock_type = NONE` |
| `USER_DELETED` | User soft deleted | `deleted`, `deleted_at`, `deleted_by` |
| `USER_MFA_RESET` | MFA re-enrollment triggered | `last_synced_at` |
