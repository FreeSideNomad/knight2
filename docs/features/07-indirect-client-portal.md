# Indirect Client Portal

## Overview

The Indirect Client Portal provides access for indirect clients (companies that are clients of the bank's direct clients) to manage their users, view their accounts, and access services. This portal uses Auth0 for authentication and provides user management capabilities.

## Current State

### Existing Features
- Auth0 authentication with JWT
- Dashboard view
- OFI Accounts view
- Users list view
- Security Info dialog

### Known Issues
- Import Indirect Clients not working on Client Portal (need sample data fix)

---

## User Stories

### User Management

#### US-ICP-001: View Users

**As an** Indirect Client Administrator
**I want** to view all users in my organization
**So that** I can manage access effectively

#### Acceptance Criteria

- [ ] Grid displays: Email, Name, Login ID, Status, Lock Type, Last Login, Roles
- [ ] Filter by status (Active, Pending, Locked, Deleted)
- [ ] Filter by role
- [ ] Search by name, email, or login ID
- [ ] Sort by any column
- [ ] Pagination for large user lists

---

#### US-ICP-002: Add User

**As an** Indirect Client Administrator
**I want** to create new users
**So that** I can grant system access to team members

#### Acceptance Criteria

- [ ] "Add User" button on users page
- [ ] Form captures: login ID, email, name, roles
- [ ] Validation ensures unique login ID and email
- [ ] User created with `PENDING_VERIFICATION` status
- [ ] Registration invitation sent to user's email
- [ ] New user appears in grid

#### UI Flow

```
┌─────────────────────────────────────────────────────────┐
│ Add New User                                            │
├─────────────────────────────────────────────────────────┤
│ Login ID*:    [jsmith                           ]       │
│               (Used for signing in)                     │
│                                                         │
│ Email*:       [john.smith@company.com           ]       │
│               (Used for communications)                 │
│                                                         │
│ First Name*:  [John                             ]       │
│                                                         │
│ Last Name*:   [Smith                            ]       │
│                                                         │
│ Roles:        □ Viewer                                  │
│               □ Creator                                 │
│               ☑ Approver                                │
│               □ Admin                                   │
│                                                         │
│              [Cancel]  [Send Invitation]                │
└─────────────────────────────────────────────────────────┘
```

---

#### US-ICP-003: Delete User

**As an** Indirect Client Administrator
**I want** to delete users who no longer need access
**So that** I maintain security and compliance

#### Acceptance Criteria

- [ ] Delete action in user row/detail
- [ ] Confirmation dialog with user name
- [ ] Soft delete (marks as deleted, retains data)
- [ ] User hidden from default list view
- [ ] User blocked from logging in
- [ ] Audit trail of deletion

#### Confirmation Dialog

```
┌─────────────────────────────────────────────────────────┐
│ Delete User                                             │
├─────────────────────────────────────────────────────────┤
│ Are you sure you want to delete this user?             │
│                                                         │
│ Name: John Smith                                        │
│ Email: john.smith@company.com                           │
│                                                         │
│ This will:                                              │
│ • Prevent the user from signing in                      │
│ • Remove the user from the active users list           │
│ • Retain the user data for audit purposes              │
│                                                         │
│                    [Cancel]  [Delete User]              │
└─────────────────────────────────────────────────────────┘
```

---

#### US-ICP-004: Manage User Permissions

**As an** Indirect Client Administrator
**I want** to manage user permissions
**So that** I can control what each user can access

#### Acceptance Criteria

- [ ] Permission management on user detail page
- [ ] Assign/remove roles
- [ ] Grant/revoke individual permissions
- [ ] Set account scope (All or Specific)
- [ ] Use Account Groups for scoping
- [ ] View effective permissions

---

#### US-ICP-005: Lock User

**As an** Indirect Client Administrator
**I want** to lock a user account
**So that** I can temporarily prevent access

#### Acceptance Criteria

- [ ] Lock action in user row/detail
- [ ] Lock type: CLIENT (can be set by indirect client admin)
- [ ] Locked user cannot sign in
- [ ] Lock reason/note optional
- [ ] Locked status shown in user list

---

#### US-ICP-006: Unlock User

**As an** Indirect Client Administrator
**I want** to unlock a user account
**So that** they can regain access

#### Acceptance Criteria

- [ ] Unlock action for locked users
- [ ] Can only unlock CLIENT-level locks
- [ ] Cannot unlock BANK or SECURITY locks
- [ ] Clear messaging if unable to unlock
- [ ] User can sign in after unlock

#### Lock Hierarchy Display

```
┌─────────────────────────────────────────────────────────┐
│ User Locked                                             │
├─────────────────────────────────────────────────────────┤
│ This user is locked by: BANK                            │
│                                                         │
│ You cannot unlock this user because the lock was       │
│ applied by a Bank Administrator.                        │
│                                                         │
│ Contact your bank representative to unlock this user.  │
│                                                         │
│                                        [OK]             │
└─────────────────────────────────────────────────────────┘
```

---

#### US-ICP-007: Re-enroll MFA

**As an** Indirect Client Administrator
**I want** to force a user to re-enroll MFA
**So that** I can help users who lost their MFA device

#### Acceptance Criteria

- [ ] "Re-enroll MFA" action in user detail
- [ ] Confirmation dialog explains implications
- [ ] Clears user's MFA enrollment in Auth0
- [ ] User prompted to enroll MFA on next login
- [ ] User notified via email

---

### Authentication

#### US-ICP-008: Forgot Password

**As an** Indirect Client Portal user
**I want** to reset my password if I forget it
**So that** I can regain access to my account

#### Acceptance Criteria

- [ ] "Forgot Password" link on login screen
- [ ] User enters login ID or email
- [ ] OTP sent to registered email
- [ ] User enters OTP to verify identity
- [ ] User sets new password
- [ ] Redirected to login on success

---

#### US-ICP-009: Passkey MFA Option

**As an** Indirect Client Portal user
**I want** to use a passkey for MFA
**So that** I can authenticate with biometrics or a security key

#### Acceptance Criteria

- [ ] Passkey option during MFA enrollment
- [ ] WebAuthn flow initiated
- [ ] User can enroll fingerprint, face, or security key
- [ ] Passkey usable for MFA challenge
- [ ] Multiple passkeys can be enrolled

---

### User Detail View

#### US-ICP-010: User Detail Page

**As an** Indirect Client Administrator
**I want** to view detailed user information
**So that** I can manage the user effectively

#### Acceptance Criteria

- [ ] Click user row to open detail
- [ ] Display: Profile info, status, roles, permissions
- [ ] Show: Last login, MFA status, email verified
- [ ] Actions: Edit, Lock/Unlock, Re-enroll MFA, Delete
- [ ] Activity log section

#### UI Layout

```
┌─────────────────────────────────────────────────────────────┐
│ ← Back to Users                                             │
├─────────────────────────────────────────────────────────────┤
│ John Smith                                    [Edit] [...]  │
│ john.smith@company.com                                      │
│ Login ID: jsmith                                            │
│                                                             │
│ Status: ● Active           Last Login: 2024-01-15 09:30    │
│ MFA: Enrolled (Guardian)   Email Verified: Yes             │
├─────────────────────────────────────────────────────────────┤
│ [Roles] [Permissions] [Activity]                            │
├─────────────────────────────────────────────────────────────┤
│ Roles                                                       │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ☑ Viewer                                                │ │
│ │ ☑ Creator                                               │ │
│ │ □ Approver                                              │ │
│ │ □ Admin                                                 │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

### User Groups in Indirect Portal

#### US-ICP-011: Manage User Groups

**As an** Indirect Client Administrator
**I want** to create and manage user groups
**So that** I can efficiently assign permissions to teams

#### Acceptance Criteria

- [ ] User Groups section in navigation
- [ ] Create, edit, delete groups
- [ ] Add/remove users from groups
- [ ] Assign permissions to groups
- [ ] View group members and permissions

---

## Navigation Structure

```
Indirect Client Portal
├── Dashboard
├── OFI Accounts
├── Users
│   ├── User List
│   └── User Detail
├── User Groups
│   ├── Group List
│   └── Group Detail
└── (User Menu)
    ├── Security Info
    └── Sign Out
```

---

## API Integration

### User Management APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/indirect/users` | List users for indirect client |
| `POST` | `/api/indirect/users` | Create user |
| `GET` | `/api/indirect/users/{id}` | Get user details |
| `PUT` | `/api/indirect/users/{id}` | Update user |
| `DELETE` | `/api/indirect/users/{id}` | Soft delete user |
| `POST` | `/api/indirect/users/{id}/lock` | Lock user (CLIENT level) |
| `POST` | `/api/indirect/users/{id}/unlock` | Unlock user |
| `POST` | `/api/indirect/users/{id}/re-enroll-mfa` | Force MFA re-enrollment |
| `GET` | `/api/indirect/users/{id}/permissions` | Get user permissions |
| `POST` | `/api/indirect/users/{id}/permissions` | Grant permission |

### User Group APIs

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/indirect/user-groups` | List user groups |
| `POST` | `/api/indirect/user-groups` | Create group |
| `GET` | `/api/indirect/user-groups/{id}` | Get group details |
| `PUT` | `/api/indirect/user-groups/{id}` | Update group |
| `DELETE` | `/api/indirect/user-groups/{id}` | Delete group |
| `POST` | `/api/indirect/user-groups/{id}/members` | Add members |
| `DELETE` | `/api/indirect/user-groups/{id}/members/{userId}` | Remove member |

---

## Permission Model

### Indirect Client Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| `VIEWER` | Read-only access | `*:view` |
| `CREATOR` | Create and edit | `*:create`, `*:update` |
| `APPROVER` | Approve actions | `*:approve` |
| `ADMIN` | Full access | `*` |

### Indirect Client Specific Permissions

```
indirect:users:view
indirect:users:create
indirect:users:update
indirect:users:delete
indirect:users:lock
indirect:users:unlock
indirect:users:re-enroll-mfa

indirect:user-groups:view
indirect:user-groups:create
indirect:user-groups:update
indirect:user-groups:delete

indirect:accounts:view

indirect:permissions:view
indirect:permissions:manage
```

---

## Security Considerations

1. **Tenant Isolation**: Indirect clients only see their own users
2. **Lock Hierarchy**: Indirect clients can only manage CLIENT-level locks
3. **Audit Trail**: All user management actions logged
4. **Permission Boundaries**: Cannot grant permissions beyond own scope
5. **MFA Required**: All users must have MFA enabled
