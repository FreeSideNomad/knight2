# Feature Documentation

This directory contains detailed feature specifications broken down into user stories for the Knight Platform.

## Documents

| # | Document | Description |
|---|----------|-------------|
| 01 | [User Management](./01-user-management.md) | User lifecycle, login tracking, lock types, soft delete |
| 02 | [Permissions & Authorization](./02-permissions-authorization.md) | URN-based actions, roles, permission evaluation |
| 03 | [Account Groups](./03-account-groups.md) | Client-managed account groupings for permissions |
| 04 | [Authentication & MFA](./04-authentication-mfa.md) | Auth0 integration, registration, MFA options (legacy) |
| 05 | [Services Architecture](./05-services-architecture.md) | Abstract service base class, account eligibility |
| 06 | [User Groups](./06-user-groups.md) | User groupings for permission management |
| 07 | [Indirect Client Portal](./07-indirect-client-portal.md) | Portal-specific user management features |
| 08 | [Sample Data](./08-sample-data.md) | Test data generation improvements |
| 10 | [Auth0 Authentication](./10-auth0-authentication.md) | **Current**: FTR, MFA, Password Reset, Step-Up Auth |

## Key Concepts

### Action URN Scheme

```
[service_type]:[service]:[resource_type]:[action_type]

Examples:
- reporting:bnt:balances:view
- payments:ach:payment:create
- security:users:approve
```

### Lock Type Hierarchy

```
NONE < CLIENT < BANK < SECURITY
```

- **CLIENT**: Set by Client Administrator
- **BANK**: Set by Bank Administrator (Employee Portal)
- **SECURITY**: Set by Security Administrator

### Permission Evaluation Order

```
1. User-specific permissions (highest priority)
2. User Group permissions
3. Role permissions (lowest priority)
```

### System Roles

| Role | Pattern | Description |
|------|---------|-------------|
| SUPER_ADMIN | `*` | Full access |
| SECURITY_ADMIN | `security:*` | User management |
| VIEWER | `*:view` | Read-only |
| CREATOR | `*:create`, `*:update`, `*:delete` | Create/modify |
| APPROVER | `*:approve` | Approval workflows |

## User Story Naming Convention

User stories follow this naming pattern:

```
US-[AREA]-[NUMBER]: [Title]
```

Areas:
- **UM**: User Management
- **PA**: Permissions & Authorization
- **AG**: Account Groups
- **AU**: Authentication
- **SA**: Services Architecture
- **UG**: User Groups
- **ICP**: Indirect Client Portal
- **SD**: Sample Data

## Implementation Priority

### Phase 1: Core User Management
- US-UM-001 to US-UM-010
- US-AU-001 to US-AU-012

### Phase 2: Permissions System
- US-PA-001 to US-PA-010
- US-AG-001 to US-AG-009

### Phase 3: Groups & Services
- US-UG-001 to US-UG-010
- US-SA-001 to US-SA-005

### Phase 4: Portal Features
- US-ICP-001 to US-ICP-011
- US-SD-001 to US-SD-006

## Dependencies

```
User Management ─────────────────────────────────────┐
     │                                               │
     ▼                                               ▼
Authentication ────► Permissions ────► Services Architecture
                          │
                          ▼
                    Account Groups
                          │
                          ▼
                    User Groups
                          │
                          ▼
              Indirect Client Portal
```
