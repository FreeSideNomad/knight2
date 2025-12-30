# Account Groups

## Overview

Account Groups allow clients to organize their accounts into logical groupings for easier permission management. Groups are configured at the profile level by Client Administrators (not bank staff) and can be referenced in permission assignments.

## Data Model

### Account Group Entity

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `profile_id` | `UUID` | Parent profile |
| `name` | `VARCHAR(100)` | Display name (e.g., "Treasury Accounts") |
| `description` | `VARCHAR(500)` | Optional description |
| `created_at` | `TIMESTAMP` | Creation timestamp |
| `created_by` | `VARCHAR` | Creator user ID |
| `updated_at` | `TIMESTAMP` | Last update timestamp |

### Account Group Membership

| Field | Type | Description |
|-------|------|-------------|
| `account_group_id` | `UUID` | Reference to group |
| `account_id` | `UUID` | Reference to client account |
| `added_at` | `TIMESTAMP` | When account was added |
| `added_by` | `VARCHAR` | Who added the account |

### Relationships

```
Profile
  └── AccountGroup (1:N)
        └── AccountGroupMembership (1:N)
              └── ClientAccount (N:1)
```

---

## User Stories

### US-AG-001: Create Account Group

**As a** Client Administrator
**I want** to create account groups
**So that** I can organize accounts for permission management

#### Acceptance Criteria

- [ ] Form to create new account group with name and description
- [ ] Name must be unique within profile
- [ ] Group created with no accounts initially
- [ ] Success notification shown
- [ ] Group appears in account groups list

#### UI Flow

```
┌─────────────────────────────────────────────┐
│ Create Account Group                        │
├─────────────────────────────────────────────┤
│ Name*:        [Treasury Accounts      ]     │
│                                             │
│ Description:  [Accounts used for           ]│
│               [treasury operations         ]│
│                                             │
│              [Cancel]  [Create Group]       │
└─────────────────────────────────────────────┘
```

---

### US-AG-002: Add Accounts to Group

**As a** Client Administrator
**I want** to add accounts to a group
**So that** the group can be used for permissions

#### Acceptance Criteria

- [ ] Account picker shows all profile accounts
- [ ] Multiple accounts can be selected
- [ ] Already-added accounts are indicated
- [ ] Accounts can be added one by one or in bulk
- [ ] An account can belong to multiple groups

#### UI Flow

```
┌─────────────────────────────────────────────────────────┐
│ Manage Accounts: Treasury Accounts                      │
├─────────────────────────────────────────────────────────┤
│ Current Members (2):                                    │
│ ┌───────────────────────────────────────────────────┐   │
│ │ ☑ Operating Account (****1234)            [Remove]│   │
│ │ ☑ Reserve Account (****9012)              [Remove]│   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│ Add Accounts:                                           │
│ ┌───────────────────────────────────────────────────┐   │
│ │ □ Payroll Account (****5678)                      │   │
│ │ □ Escrow Account (****3456)                       │   │
│ │ □ Collections Account (****7890)                  │   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│                          [Cancel]  [Save Changes]       │
└─────────────────────────────────────────────────────────┘
```

---

### US-AG-003: Remove Accounts from Group

**As a** Client Administrator
**I want** to remove accounts from a group
**So that** I can maintain accurate groupings

#### Acceptance Criteria

- [ ] Remove button available for each account in group
- [ ] Confirmation dialog before removal
- [ ] Removal logged with timestamp and user
- [ ] Permissions using this group updated accordingly

---

### US-AG-004: View Account Groups

**As a** Client Administrator
**I want** to view all account groups
**So that** I can manage them effectively

#### Acceptance Criteria

- [ ] List view showing all groups
- [ ] Each group shows: name, description, account count
- [ ] Click to view/edit group details
- [ ] Sort by name or account count
- [ ] Search by group name

#### UI Mockup

```
┌─────────────────────────────────────────────────────────────┐
│ Account Groups                                    [+ New]   │
├─────────────────────────────────────────────────────────────┤
│ Name                    │ Accounts │ Description            │
├─────────────────────────┼──────────┼────────────────────────┤
│ Treasury Accounts       │    3     │ Treasury operations    │
│ Payroll Accounts        │    2     │ Payroll processing     │
│ Vendor Payments         │    5     │ AP disbursements       │
│ Collections             │    4     │ AR collection accounts │
└─────────────────────────┴──────────┴────────────────────────┘
```

---

### US-AG-005: Edit Account Group

**As a** Client Administrator
**I want** to edit account group details
**So that** I can keep information current

#### Acceptance Criteria

- [ ] Edit name and description
- [ ] Name uniqueness validated
- [ ] Changes saved with audit trail
- [ ] Success notification shown

---

### US-AG-006: Delete Account Group

**As a** Client Administrator
**I want** to delete account groups that are no longer needed
**So that** the group list stays clean

#### Acceptance Criteria

- [ ] Delete action available for each group
- [ ] Warning if group is used in permissions
- [ ] Confirmation dialog required
- [ ] Group deleted (or soft deleted)
- [ ] Permissions referencing group updated (scope becomes empty)

#### Confirmation Dialog

```
┌─────────────────────────────────────────────────────────┐
│ Delete Account Group                                    │
├─────────────────────────────────────────────────────────┤
│ Are you sure you want to delete "Treasury Accounts"?   │
│                                                         │
│ ⚠️ Warning: This group is referenced by 3 permissions.  │
│ Users with these permissions will lose access to the   │
│ accounts in this group.                                │
│                                                         │
│ Affected users: john.doe, jane.smith, bob.wilson       │
│                                                         │
│                    [Cancel]  [Delete Anyway]            │
└─────────────────────────────────────────────────────────┘
```

---

### US-AG-007: Use Account Group in Permissions

**As a** Client Administrator
**I want** to select an account group when assigning permissions
**So that** I can efficiently manage access to multiple accounts

#### Acceptance Criteria

- [ ] Account Group option in permission scope selector
- [ ] Groups shown alongside individual accounts
- [ ] Permission applies to all accounts in group
- [ ] Permission updates automatically as group membership changes

#### UI Integration

```
┌─────────────────────────────────────────────────┐
│ Grant Permission                                │
├─────────────────────────────────────────────────┤
│ Action: payments:ach:payment:view               │
│                                                 │
│ Account Scope:                                  │
│ ○ All Accounts                                  │
│ ● Specific Accounts                             │
│                                                 │
│   Account Groups:                               │
│   ☑ Treasury Accounts (3 accounts)             │
│   □ Payroll Accounts (2 accounts)              │
│                                                 │
│   Individual Accounts:                          │
│   □ Escrow Account (****3456)                  │
│   □ Collections Account (****7890)             │
│                                                 │
│              [Cancel]  [Grant Permission]       │
└─────────────────────────────────────────────────┘
```

---

### US-AG-008: View Group Usage

**As a** Client Administrator
**I want** to see where an account group is used
**So that** I understand the impact of changes

#### Acceptance Criteria

- [ ] Group detail page shows usage section
- [ ] Lists permissions using this group
- [ ] Lists users with permissions via this group
- [ ] Link to relevant permission/user management

---

### US-AG-009: Dynamic Group Membership

**As a** system
**I want** permissions to reflect current group membership
**So that** access is always accurate

#### Acceptance Criteria

- [ ] When account added to group, users with group permission gain access
- [ ] When account removed from group, access is revoked
- [ ] No manual permission updates needed
- [ ] Permission check queries group membership in real-time

#### Technical Notes

```sql
-- Permission check with group support
SELECT DISTINCT ca.id
FROM client_accounts ca
LEFT JOIN user_permissions up ON up.user_id = :userId
LEFT JOIN permission_account_groups pag ON pag.permission_id = up.permission_id
LEFT JOIN account_group_memberships agm ON agm.account_group_id = pag.account_group_id
LEFT JOIN permission_accounts pa ON pa.permission_id = up.permission_id
WHERE up.action_urn = :actionUrn
  AND (
    up.scope_type = 'ALL_ACCOUNTS'
    OR pa.account_id = ca.id
    OR agm.account_id = ca.id
  )
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/account-groups` | List account groups |
| `POST` | `/api/account-groups` | Create account group |
| `GET` | `/api/account-groups/{id}` | Get group details |
| `PUT` | `/api/account-groups/{id}` | Update group |
| `DELETE` | `/api/account-groups/{id}` | Delete group |
| `GET` | `/api/account-groups/{id}/accounts` | List accounts in group |
| `POST` | `/api/account-groups/{id}/accounts` | Add accounts to group |
| `DELETE` | `/api/account-groups/{id}/accounts/{accountId}` | Remove account |
| `GET` | `/api/account-groups/{id}/usage` | Get permissions using group |

---

## Domain Events

| Event | Trigger | Payload |
|-------|---------|---------|
| `ACCOUNT_GROUP_CREATED` | New group created | group details |
| `ACCOUNT_GROUP_UPDATED` | Group name/desc changed | group details |
| `ACCOUNT_GROUP_DELETED` | Group deleted | group id |
| `ACCOUNT_ADDED_TO_GROUP` | Account added | group id, account id |
| `ACCOUNT_REMOVED_FROM_GROUP` | Account removed | group id, account id |

---

## Implementation Notes

1. **Client-Managed**: Account groups are created and managed by Client Administrators, not bank staff
2. **Profile-Scoped**: Groups are scoped to a profile and cannot span profiles
3. **Multi-Group Membership**: An account can belong to multiple groups
4. **Real-Time Resolution**: Group membership resolved at permission check time
5. **Audit Trail**: All group and membership changes are audited
