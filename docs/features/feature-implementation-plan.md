# Feature Implementation Plan

This document outlines the recommended sequence for implementing user stories across all feature areas.

## Implementation Phases

### Phase 1: Foundation (Data Model & Core Services)

These stories establish the foundational data model and core services that other features depend on.

#### Sprint 1.1: User Entity Updates
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 1 | US-UM-001 | Track User Login Time | None |
| 2 | US-UM-002 | Separate Sync Events from Login | US-UM-001 |
| 3 | US-UM-003 | Lock Type Implementation | None |
| 4 | US-UM-004 | User Login ID Separate from Email | None |

#### Sprint 1.2: Action & Service Framework
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 5 | US-PA-001 | Define Action URN Structure | None |
| 6 | US-SA-001 | Service Registration | US-PA-001 |
| 7 | US-SA-002 | Account Eligibility Check | US-SA-001 |
| 8 | US-SA-003 | Get Service Actions | US-SA-001 |

```
┌─────────────┐     ┌─────────────┐
│  US-UM-001  │     │  US-UM-003  │
│  Login Time │     │  Lock Types │
└──────┬──────┘     └─────────────┘
       │
       ▼
┌─────────────┐     ┌─────────────┐
│  US-UM-002  │     │  US-UM-004  │
│  Sync Events│     │  Login ID   │
└─────────────┘     └─────────────┘

┌─────────────┐
│  US-PA-001  │
│  Action URN │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  US-SA-001  │
│  Service Reg│
└──────┬──────┘
       │
       ├──────────────┐
       ▼              ▼
┌─────────────┐ ┌─────────────┐
│  US-SA-002  │ │  US-SA-003  │
│  Eligibility│ │  Actions    │
└─────────────┘ └─────────────┘
```

---

### Phase 2: Authentication & User Lifecycle

Complete authentication flows and user management capabilities.

#### Sprint 2.1: Auth0 Integration
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 9 | US-AU-001 | Login ID Separate from Email | US-UM-004 |
| 10 | US-AU-002 | New User Registration Email | US-AU-001 |
| 11 | US-AU-003 | Email Verification via OTP | US-AU-002 |
| 12 | US-AU-004 | Password Setup | US-AU-003 |

#### Sprint 2.2: MFA & Session
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 13 | US-AU-005 | MFA Enrollment - Guardian Push | US-AU-004 |
| 14 | US-AU-006 | MFA Enrollment - Passkey | US-AU-004 |
| 15 | US-AU-007 | Login Flow | US-AU-005, US-AU-006 |
| 16 | US-AU-010 | Track Email Verification | US-AU-003 |
| 17 | US-AU-011 | Session Management | US-AU-007 |

#### Sprint 2.3: User Management
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 18 | US-UM-005 | Create New User | US-AU-002 |
| 19 | US-UM-006 | User Registration Flow | US-AU-007 |
| 20 | US-UM-007 | Soft Delete User | US-UM-005 |
| 21 | US-UM-008 | Re-enroll MFA | US-AU-005 |
| 22 | US-AU-008 | Forgot Password Flow | US-AU-003 |
| 23 | US-AU-009 | Re-enroll MFA (Admin) | US-UM-008 |
| 24 | US-AU-012 | Step-Up Authentication | US-AU-007 |

```
US-UM-004 ──► US-AU-001 ──► US-AU-002 ──► US-AU-003 ──► US-AU-004
                                              │             │
                                              ▼             ├───────┐
                                         US-AU-010          ▼       ▼
                                                      US-AU-005  US-AU-006
                                                            │       │
                                                            └───┬───┘
                                                                ▼
                                                          US-AU-007
                                                                │
                              ┌──────────────────┬──────────────┤
                              ▼                  ▼              ▼
                         US-AU-011          US-AU-012     US-AU-008
```

---

### Phase 3: Permissions System

Implement the complete permissions framework.

#### Sprint 3.1: Role & Permission Management
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 25 | US-PA-002 | Role-Based Permission Assignment | US-PA-001 |
| 26 | US-PA-003 | User-Specific Permission Override | US-PA-002 |
| 27 | US-PA-009 | Wildcard Permission Matching | US-PA-001 |
| 28 | US-PA-007 | Permission Evaluation Order | US-PA-002, US-PA-003 |

#### Sprint 3.2: Account Scoping
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 29 | US-AG-001 | Create Account Group | None |
| 30 | US-AG-002 | Add Accounts to Group | US-AG-001 |
| 31 | US-AG-003 | Remove Accounts from Group | US-AG-002 |
| 32 | US-AG-004 | View Account Groups | US-AG-001 |
| 33 | US-AG-005 | Edit Account Group | US-AG-001 |
| 34 | US-PA-004 | Account-Level Permission Scope | US-AG-001 |

#### Sprint 3.3: Permission APIs
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 35 | US-PA-005 | Permission Check API - isAllowed | US-PA-007, US-PA-004 |
| 36 | US-PA-006 | Get Allowed Accounts API | US-PA-005 |
| 37 | US-SA-004 | Dynamic Permission UI | US-SA-003, US-PA-005 |
| 38 | US-SA-005 | Account Filtering in Permission Check | US-SA-002, US-PA-005 |
| 39 | US-PA-008 | Manage Permissions UI | US-PA-005, US-PA-006 |
| 40 | US-PA-010 | Audit Permission Changes | US-PA-003 |

```
US-PA-001 ──► US-PA-002 ──► US-PA-003
    │             │             │
    │             └──────┬──────┘
    │                    ▼
    │              US-PA-007 ──────────────┐
    │                                      │
    ▼                                      ▼
US-PA-009     US-AG-001 ──► US-PA-004 ──► US-PA-005
                  │                           │
                  ├──► US-AG-002              │
                  │        │                  ▼
                  │        ▼             US-PA-006
                  │    US-AG-003              │
                  │                           ▼
                  ├──► US-AG-004         US-PA-008
                  │
                  └──► US-AG-005
```

---

### Phase 4: User Groups

Add user grouping capabilities for permission management.

#### Sprint 4.1: User Group Management
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 41 | US-UG-001 | Create User Group | None |
| 42 | US-UG-002 | Add Users to Group | US-UG-001 |
| 43 | US-UG-003 | Remove Users from Group | US-UG-002 |
| 44 | US-UG-004 | Assign Permissions to Group | US-UG-001, US-PA-005 |
| 45 | US-UG-005 | View User Groups | US-UG-001 |

#### Sprint 4.2: User Group Integration
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 46 | US-UG-006 | View User's Groups | US-UG-002 |
| 47 | US-UG-007 | Edit User Group | US-UG-001 |
| 48 | US-UG-008 | Delete User Group | US-UG-001 |
| 49 | US-UG-009 | Permission Evaluation with Groups | US-UG-004, US-PA-007 |
| 50 | US-UG-010 | Effective Permissions View | US-UG-009 |

#### Sprint 4.3: Account Group Completion
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 51 | US-AG-006 | Delete Account Group | US-AG-001 |
| 52 | US-AG-007 | Use Account Group in Permissions | US-AG-001, US-PA-004 |
| 53 | US-AG-008 | View Group Usage | US-AG-007 |
| 54 | US-AG-009 | Dynamic Group Membership | US-AG-007 |

---

### Phase 5: User Management Views

Complete user management UI and remaining stories.

#### Sprint 5.1: User Management UI
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 55 | US-UM-009 | View User Activity | US-UM-001, US-UM-002 |
| 56 | US-UM-010 | User List with Filters | US-UM-003, US-UM-007 |

---

### Phase 6: Indirect Client Portal

Implement portal-specific features.

#### Sprint 6.1: Sample Data
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 57 | US-SD-001 | Generate 10+ Indirect Client Companies | None |
| 58 | US-SD-002 | Unique Individuals Per Company | US-SD-001 |
| 59 | US-SD-003 | External References for Companies | US-SD-001 |
| 60 | US-SD-004 | Diverse Company Attributes | US-SD-001 |
| 61 | US-SD-005 | Generate Addresses | US-SD-001 |
| 62 | US-SD-006 | Generate Contact Information | US-SD-002 |

#### Sprint 6.2: Indirect Portal User Management
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 63 | US-ICP-001 | View Users | US-UM-010 |
| 64 | US-ICP-002 | Add User | US-UM-005 |
| 65 | US-ICP-003 | Delete User | US-UM-007 |
| 66 | US-ICP-004 | Manage User Permissions | US-PA-008 |
| 67 | US-ICP-005 | Lock User | US-UM-003 |
| 68 | US-ICP-006 | Unlock User | US-UM-003 |
| 69 | US-ICP-007 | Re-enroll MFA | US-AU-009 |

#### Sprint 6.3: Indirect Portal Authentication
| Order | Story | Description | Dependencies |
|-------|-------|-------------|--------------|
| 70 | US-ICP-008 | Forgot Password | US-AU-008 |
| 71 | US-ICP-009 | Passkey MFA Option | US-AU-006 |
| 72 | US-ICP-010 | User Detail Page | US-ICP-001 |
| 73 | US-ICP-011 | Manage User Groups | US-UG-005 |

---

## Dependency Graph (Simplified)

```
                    ┌─────────────────────────────────────────┐
                    │           PHASE 1: FOUNDATION           │
                    │  US-UM-001,002,003,004                  │
                    │  US-PA-001, US-SA-001,002,003           │
                    └─────────────────┬───────────────────────┘
                                      │
                    ┌─────────────────┴───────────────────────┐
                    │         PHASE 2: AUTHENTICATION         │
                    │  US-AU-001-012                          │
                    │  US-UM-005,006,007,008                  │
                    └─────────────────┬───────────────────────┘
                                      │
                    ┌─────────────────┴───────────────────────┐
                    │          PHASE 3: PERMISSIONS           │
                    │  US-PA-002-010                          │
                    │  US-AG-001-005                          │
                    │  US-SA-004,005                          │
                    └─────────────────┬───────────────────────┘
                                      │
                    ┌─────────────────┴───────────────────────┐
                    │          PHASE 4: USER GROUPS           │
                    │  US-UG-001-010                          │
                    │  US-AG-006-009                          │
                    └─────────────────┬───────────────────────┘
                                      │
          ┌───────────────────────────┴───────────────────────┐
          │                                                   │
┌─────────┴─────────┐                           ┌─────────────┴─────────┐
│  PHASE 5: UI      │                           │  PHASE 6: ICP         │
│  US-UM-009,010    │                           │  US-SD-001-006        │
└───────────────────┘                           │  US-ICP-001-011       │
                                                └───────────────────────┘
```

---

## Story Count Summary

| Area | Count | Stories |
|------|-------|---------|
| User Management (UM) | 10 | US-UM-001 to US-UM-010 |
| Permissions (PA) | 10 | US-PA-001 to US-PA-010 |
| Account Groups (AG) | 9 | US-AG-001 to US-AG-009 |
| Authentication (AU) | 12 | US-AU-001 to US-AU-012 |
| Services (SA) | 5 | US-SA-001 to US-SA-005 |
| User Groups (UG) | 10 | US-UG-001 to US-UG-010 |
| Indirect Client Portal (ICP) | 11 | US-ICP-001 to US-ICP-011 |
| Sample Data (SD) | 6 | US-SD-001 to US-SD-006 |
| **Total** | **73** | |

---

## Parallel Execution Opportunities

Stories that can be implemented in parallel (no dependencies between them):

### Foundation Phase
- US-UM-001, US-UM-003, US-UM-004, US-PA-001 (all independent)

### After US-PA-001
- US-SA-002, US-SA-003 (both depend only on US-SA-001)

### Authentication Phase
- US-AU-005, US-AU-006 (both depend on US-AU-004)

### Account Groups
- US-AG-002, US-AG-004, US-AG-005 (all depend only on US-AG-001)

### User Groups
- US-UG-002, US-UG-004, US-UG-005, US-UG-007, US-UG-008 (all depend only on US-UG-001)

### Sample Data
- US-SD-002, US-SD-003, US-SD-004, US-SD-005 (all depend only on US-SD-001)

---

## Risk Areas

1. **Auth0 Configuration**: US-AU-001 (Login ID) requires careful Auth0 setup
2. **Permission Evaluation**: US-PA-007, US-UG-009 are complex and critical
3. **Data Migration**: US-UM-003 (Lock Types) needs migration from existing lock_reason
4. **Performance**: US-PA-005, US-PA-006 need efficient queries for permission checks

---

## User Story Files

Individual user story files are located in the `stories/` subdirectory:

```
docs/features/stories/
├── US-UM-001.md through US-UM-010.md
├── US-PA-001.md through US-PA-010.md
├── US-AG-001.md through US-AG-009.md
├── US-AU-001.md through US-AU-012.md
├── US-SA-001.md through US-SA-005.md
├── US-UG-001.md through US-UG-010.md
├── US-ICP-001.md through US-ICP-011.md
└── US-SD-001.md through US-SD-006.md
```
