# Permissions & Authorization

## Overview

The permissions system controls user access to resources and actions across the platform. It uses a URN-based action scheme combined with role-based and user-specific permissions, with support for account-level granularity.

## Action URN Scheme

Actions follow this URN pattern:

```
[service_type]:[service]:[resource_type]:[action_type]
```

Where:
- **service_type**: Top-level category (e.g., `reporting`, `payments`, `security`)
- **service**: Specific service within the category (e.g., `bnt`, `ach`, `users`)
- **resource_type**: (Optional) Specific resource being acted upon (e.g., `balances`, `payment`, `template`)
- **action_type**: The operation (e.g., `view`, `create`, `update`, `delete`, `approve`)

### Action Examples

```
# Reporting
reporting:bnt:balances:view
reporting:bnt:transactions:view
reporting:statements:view

# Security / User Management
security:users:create
security:users:view
security:users:approve

# Payments - Wildcard
payments:*

# Payments - Receivables
payments:receivables:payors:create
payments:receivables:payors:view
payments:receivables:invoices:create
payments:receivables:invoices:view

# Payments - Payables
payments:payables:invoices:view
payments:payables:invoices:approve
payments:payables:accounts:view
payments:payables:accounts:create

# Payments - ACH
payments:ach:payment:create
payments:ach:payment:update
payments:ach:payment:delete
payments:ach:payment:approve
payments:ach:payment:view
payments:ach:template:create
```

---

## Role Definitions

### System Roles

| Role | Action Pattern | Description |
|------|---------------|-------------|
| `SUPER_ADMIN` | `*` | Full access to all actions |
| `SECURITY_ADMIN` | `security:*` | Full access to security/user management |
| `VIEWER` | `*:view` | View-only access to all resources |
| `CREATOR` | `*:create`, `*:update`, `*:delete` | Create, update, and delete access |
| `APPROVER` | `*:approve` | Approval access for workflows |

### Role-Action Matrix

```
┌──────────────┬────────┬────────┬────────┬────────┬─────────┐
│ Action       │ SUPER  │ SEC    │ VIEWER │ CREATOR│ APPROVER│
│              │ ADMIN  │ ADMIN  │        │        │         │
├──────────────┼────────┼────────┼────────┼────────┼─────────┤
│ *:view       │   ✓    │   -    │   ✓    │   -    │    -    │
│ *:create     │   ✓    │   -    │   -    │   ✓    │    -    │
│ *:update     │   ✓    │   -    │   -    │   ✓    │    -    │
│ *:delete     │   ✓    │   -    │   -    │   ✓    │    -    │
│ *:approve    │   ✓    │   -    │   -    │   -    │    ✓    │
│ security:*   │   ✓    │   ✓    │   -    │   -    │    -    │
└──────────────┴────────┴────────┴────────┴────────┴─────────┘
```

---

## Data Model

### Action Value Object

```java
public record Action(
    ServiceType serviceType,
    String service,
    String resourceType,  // Optional
    ActionType actionType
) {
    public String toUrn() {
        if (resourceType == null || resourceType.isBlank()) {
            return "%s:%s:%s".formatted(serviceType, service, actionType);
        }
        return "%s:%s:%s:%s".formatted(serviceType, service, resourceType, actionType);
    }

    public static Action fromUrn(String urn) { ... }

    public boolean matches(Action other) {
        // Supports wildcard matching
    }
}

public enum ServiceType {
    REPORTING, PAYMENTS, SECURITY
}

public enum ActionType {
    VIEW, CREATE, UPDATE, DELETE, APPROVE
}
```

### Permission Entity

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `action_urn` | `VARCHAR` | The action URN |
| `scope_type` | `ENUM` | `ALL_ACCOUNTS` or `SPECIFIC_ACCOUNTS` |
| `account_ids` | `UUID[]` | List of account IDs (if scope is SPECIFIC) |
| `account_group_id` | `UUID` | Reference to account group (alternative to account_ids) |

### User Permission

| Field | Type | Description |
|-------|------|-------------|
| `user_id` | `UUID` | Reference to user |
| `permission_id` | `UUID` | Reference to permission |
| `granted_at` | `TIMESTAMP` | When permission was granted |
| `granted_by` | `VARCHAR` | Who granted the permission |

### Role Permission

| Field | Type | Description |
|-------|------|-------------|
| `role` | `VARCHAR` | Role name |
| `action_pattern` | `VARCHAR` | Action pattern (supports wildcards) |

---

## User Stories

### US-PA-001: Define Action URN Structure

**As a** developer
**I want** a consistent URN structure for actions
**So that** permissions can be defined and evaluated consistently

#### Acceptance Criteria

- [ ] `Action` value object created with URN parsing
- [ ] `ServiceType` enum defined
- [ ] `ActionType` enum defined
- [ ] Wildcard matching implemented (`*` matches any)
- [ ] URN validation implemented

---

### US-PA-002: Role-Based Permission Assignment

**As a** Client Administrator
**I want** to assign roles to users
**So that** they receive the associated permissions

#### Acceptance Criteria

- [ ] Roles can be assigned to users
- [ ] Multiple roles can be assigned to same user
- [ ] Role permissions evaluated during authorization
- [ ] UI shows role-based permissions

---

### US-PA-003: User-Specific Permission Override

**As a** Client Administrator
**I want** to grant or revoke specific permissions for a user
**So that** I can fine-tune access beyond roles

#### Acceptance Criteria

- [ ] Individual permissions can be granted to users
- [ ] User permissions override role permissions
- [ ] Grant and revoke actions are audited
- [ ] UI allows managing user-specific permissions

---

### US-PA-004: Account-Level Permission Scope

**As a** Client Administrator
**I want** to restrict permissions to specific accounts
**So that** users only access accounts they need

#### Acceptance Criteria

- [ ] Permission scope options: "All Accounts" or "Specific Accounts"
- [ ] When "Specific Accounts" selected, account picker shown
- [ ] Account Groups can be selected instead of individual accounts
- [ ] Permission evaluation respects account scope

#### UI Flow

```
┌─────────────────────────────────────────────┐
│ Permission: payments:ach:payment:view       │
├─────────────────────────────────────────────┤
│ Account Access:                             │
│ ○ All Accounts                              │
│ ● Specific Accounts                         │
│                                             │
│   □ Account Group: Treasury Accounts        │
│   ☑ Account: Operating Account (****1234)   │
│   ☑ Account: Payroll Account (****5678)     │
│   □ Account: Reserve Account (****9012)     │
└─────────────────────────────────────────────┘
```

---

### US-PA-005: Permission Check API - isAllowed

**As a** developer
**I want** an API to check if a user is allowed to perform an action
**So that** I can enforce permissions in the application

#### Acceptance Criteria

- [ ] `POST /api/permissions/check` endpoint
- [ ] Request includes action URN and optional account ID
- [ ] User determined from JWT
- [ ] Response: `{ "allowed": true/false, "reason": "..." }`
- [ ] Evaluates: user permissions → user group permissions → role permissions

#### API Specification

```
POST /api/permissions/check
Authorization: Bearer <jwt>

Request:
{
  "action": "payments:ach:payment:view",
  "accountId": "uuid-optional"
}

Response:
{
  "allowed": true,
  "evaluatedPermissions": [
    { "source": "role", "role": "VIEWER", "pattern": "*:view" }
  ]
}
```

---

### US-PA-006: Get Allowed Accounts API

**As a** developer
**I want** an API to get accounts a user can access for an action
**So that** I can filter UI and queries appropriately

#### Acceptance Criteria

- [ ] `GET /api/permissions/allowed-accounts?action={urn}` endpoint
- [ ] User determined from JWT
- [ ] Returns list of account IDs user can access
- [ ] If user has "All Accounts" scope, returns all profile accounts
- [ ] Combines scopes from all permission sources

#### API Specification

```
GET /api/permissions/allowed-accounts?action=payments:ach:payment:view
Authorization: Bearer <jwt>

Response:
{
  "scope": "SPECIFIC",  // or "ALL"
  "accounts": [
    { "id": "uuid", "name": "Operating Account", "number": "****1234" },
    { "id": "uuid", "name": "Payroll Account", "number": "****5678" }
  ]
}
```

---

### US-PA-007: Permission Evaluation Order

**As a** system
**I want** a defined permission evaluation order
**So that** permissions are consistently applied

#### Acceptance Criteria

- [ ] Evaluation order: User → User Group → Role
- [ ] First match wins (most specific first)
- [ ] Explicit deny overrides allow (future consideration)
- [ ] Evaluation logged for debugging

#### Evaluation Flow

```
1. Check User-specific permissions
   └── If match found → Return result
2. Check User Group permissions (for each group user belongs to)
   └── If match found → Return result
3. Check Role permissions (for each role user has)
   └── If match found → Return result
4. Default → Deny
```

---

### US-PA-008: Manage Permissions UI

**As a** Client Administrator
**I want** a UI to manage user permissions
**So that** I can control access without technical knowledge

#### Acceptance Criteria

- [ ] Permission management accessible from user detail
- [ ] Toggle permissions on/off per action
- [ ] Expand action to set account scope
- [ ] Show inherited permissions (from roles/groups)
- [ ] Visual distinction between direct and inherited

#### UI Mockup

```
┌─────────────────────────────────────────────────────────────┐
│ User Permissions: john.doe@acme.com                         │
├─────────────────────────────────────────────────────────────┤
│ Roles: [VIEWER] [CREATOR]                                   │
│ Groups: [Treasury Team]                                     │
├─────────────────────────────────────────────────────────────┤
│ Permission                    │ Status    │ Source │ Scope  │
├───────────────────────────────┼───────────┼────────┼────────┤
│ payments:ach:payment:view     │ ✓ Allowed │ Role   │ All    │
│ payments:ach:payment:create   │ ✓ Allowed │ Role   │ All    │
│ payments:ach:payment:approve  │ ✗ Denied  │ -      │ -      │
│   └─ [+ Grant]                │           │        │        │
│ reporting:bnt:balances:view   │ ✓ Allowed │ Group  │ 3 Accts│
└───────────────────────────────┴───────────┴────────┴────────┘
```

---

### US-PA-009: Wildcard Permission Matching

**As a** system
**I want** wildcard support in permission patterns
**So that** broad permissions can be efficiently assigned

#### Acceptance Criteria

- [ ] `*` matches any single segment
- [ ] `*:view` matches all view actions
- [ ] `payments:*` matches all payment actions
- [ ] `*` alone matches everything (SUPER_ADMIN)
- [ ] Matching is case-insensitive

#### Examples

```
Pattern: *:view
Matches: reporting:bnt:balances:view ✓
         payments:ach:payment:view ✓
         payments:ach:payment:create ✗

Pattern: payments:*
Matches: payments:ach:payment:view ✓
         payments:receivables:invoices:create ✓
         reporting:bnt:balances:view ✗

Pattern: payments:ach:*:view
Matches: payments:ach:payment:view ✓
         payments:ach:template:view ✓
         payments:ach:payment:create ✗
```

---

### US-PA-010: Audit Permission Changes

**As a** compliance officer
**I want** all permission changes audited
**So that** I can review who had access at any point in time

#### Acceptance Criteria

- [ ] Permission grant/revoke creates audit record
- [ ] Audit includes: user, permission, action, timestamp, actor
- [ ] Role assignment changes audited
- [ ] User group membership changes audited
- [ ] Audit log queryable by user and date range

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/permissions/check` | Check if action is allowed |
| `GET` | `/api/permissions/allowed-accounts` | Get accounts for action |
| `GET` | `/api/users/{id}/permissions` | Get user's effective permissions |
| `POST` | `/api/users/{id}/permissions` | Grant permission to user |
| `DELETE` | `/api/users/{id}/permissions/{permId}` | Revoke permission |
| `GET` | `/api/roles` | List available roles |
| `GET` | `/api/roles/{role}/permissions` | Get role's permissions |

---

## Security Considerations

1. **Principle of Least Privilege**: Default deny, explicit allow
2. **Separation of Duties**: Creators cannot approve their own work
3. **Audit Trail**: All permission changes logged
4. **Session Refresh**: Permission changes take effect on next request
5. **Cache Invalidation**: Permission cache cleared on changes
