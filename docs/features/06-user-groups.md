# User Groups

## Overview

User Groups provide a way to organize users into logical groupings and assign permissions at the group level. This simplifies permission management for organizations with many users who share common access needs.

## Data Model

### User Group Entity

| Field | Type | Description |
|-------|------|-------------|
| `id` | `UUID` | Primary key |
| `profile_id` | `UUID` | Parent profile |
| `name` | `VARCHAR(100)` | Display name (e.g., "Treasury Team") |
| `description` | `VARCHAR(500)` | Optional description |
| `created_at` | `TIMESTAMP` | Creation timestamp |
| `created_by` | `VARCHAR` | Creator user ID |
| `updated_at` | `TIMESTAMP` | Last update timestamp |

### User Group Membership

| Field | Type | Description |
|-------|------|-------------|
| `user_group_id` | `UUID` | Reference to group |
| `user_id` | `UUID` | Reference to user |
| `added_at` | `TIMESTAMP` | When user was added |
| `added_by` | `VARCHAR` | Who added the user |

### User Group Permission

| Field | Type | Description |
|-------|------|-------------|
| `user_group_id` | `UUID` | Reference to group |
| `permission_id` | `UUID` | Reference to permission |
| `granted_at` | `TIMESTAMP` | When permission was granted |
| `granted_by` | `VARCHAR` | Who granted the permission |

### Permission Evaluation Order

```
1. User-specific permissions (highest priority)
2. User Group permissions
3. Role permissions (lowest priority)
```

---

## User Stories

### US-UG-001: Create User Group

**As a** Client Administrator
**I want** to create user groups
**So that** I can organize users by function or department

#### Acceptance Criteria

- [ ] Form to create new user group with name and description
- [ ] Name must be unique within profile
- [ ] Group created with no members initially
- [ ] Success notification shown
- [ ] Group appears in user groups list

#### UI Flow

```
┌─────────────────────────────────────────────┐
│ Create User Group                           │
├─────────────────────────────────────────────┤
│ Name*:        [Treasury Team          ]     │
│                                             │
│ Description:  [Users who manage treasury   ]│
│               [operations and payments     ]│
│                                             │
│              [Cancel]  [Create Group]       │
└─────────────────────────────────────────────┘
```

---

### US-UG-002: Add Users to Group

**As a** Client Administrator
**I want** to add users to a group
**So that** they receive group permissions

#### Acceptance Criteria

- [ ] User picker shows all profile users
- [ ] Multiple users can be selected
- [ ] Already-added users are indicated
- [ ] Users can be added one by one or in bulk
- [ ] A user can belong to multiple groups

#### UI Flow

```
┌─────────────────────────────────────────────────────────┐
│ Manage Members: Treasury Team                           │
├─────────────────────────────────────────────────────────┤
│ Current Members (3):                                    │
│ ┌───────────────────────────────────────────────────┐   │
│ │ ☑ john.doe@acme.com (John Doe)            [Remove]│   │
│ │ ☑ jane.smith@acme.com (Jane Smith)        [Remove]│   │
│ │ ☑ bob.wilson@acme.com (Bob Wilson)        [Remove]│   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│ Add Users:                                              │
│ ┌───────────────────────────────────────────────────┐   │
│ │ □ alice.jones@acme.com (Alice Jones)              │   │
│ │ □ charlie.brown@acme.com (Charlie Brown)          │   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│                          [Cancel]  [Save Changes]       │
└─────────────────────────────────────────────────────────┘
```

---

### US-UG-003: Remove Users from Group

**As a** Client Administrator
**I want** to remove users from a group
**So that** they no longer receive group permissions

#### Acceptance Criteria

- [ ] Remove button available for each user in group
- [ ] Confirmation dialog before removal
- [ ] Removal logged with timestamp and user
- [ ] User's effective permissions updated immediately

---

### US-UG-004: Assign Permissions to Group

**As a** Client Administrator
**I want** to assign permissions to a user group
**So that** all group members receive those permissions

#### Acceptance Criteria

- [ ] Permission assignment UI on group detail page
- [ ] Same options as user permission assignment
- [ ] Can set action and account scope
- [ ] All group members inherit permission
- [ ] Permission changes apply immediately

#### UI Flow

```
┌─────────────────────────────────────────────────────────┐
│ Group Permissions: Treasury Team                        │
├─────────────────────────────────────────────────────────┤
│ Members: 3                                              │
├─────────────────────────────────────────────────────────┤
│ Permission                    │ Scope          │ Actions│
├───────────────────────────────┼────────────────┼────────┤
│ payments:ach:payment:view     │ All Accounts   │ [Edit] │
│ payments:ach:payment:create   │ Treasury Accts │ [Edit] │
│ reporting:bnt:balances:view   │ All Accounts   │ [Edit] │
│                               │                │        │
│                               [+ Add Permission]        │
└─────────────────────────────────────────────────────────┘
```

---

### US-UG-005: View User Groups

**As a** Client Administrator
**I want** to view all user groups
**So that** I can manage them effectively

#### Acceptance Criteria

- [ ] List view showing all groups
- [ ] Each group shows: name, description, member count, permission count
- [ ] Click to view/edit group details
- [ ] Sort by name or member count
- [ ] Search by group name

#### UI Mockup

```
┌─────────────────────────────────────────────────────────────┐
│ User Groups                                       [+ New]   │
├─────────────────────────────────────────────────────────────┤
│ Name                    │ Members │ Perms │ Description     │
├─────────────────────────┼─────────┼───────┼─────────────────┤
│ Treasury Team           │    5    │   8   │ Treasury ops    │
│ Accounts Payable        │    3    │   6   │ AP processing   │
│ Accounts Receivable     │    4    │   5   │ AR management   │
│ Approvers               │    2    │   3   │ Payment approval│
└─────────────────────────┴─────────┴───────┴─────────────────┘
```

---

### US-UG-006: View User's Groups

**As a** Client Administrator
**I want** to see which groups a user belongs to
**So that** I understand their permission sources

#### Acceptance Criteria

- [ ] User detail page shows group memberships
- [ ] Shows which permissions come from each group
- [ ] Can navigate to group from user page
- [ ] Can add/remove group membership from user page

---

### US-UG-007: Edit User Group

**As a** Client Administrator
**I want** to edit user group details
**So that** I can keep information current

#### Acceptance Criteria

- [ ] Edit name and description
- [ ] Name uniqueness validated
- [ ] Changes saved with audit trail
- [ ] Success notification shown

---

### US-UG-008: Delete User Group

**As a** Client Administrator
**I want** to delete user groups that are no longer needed
**So that** the group list stays clean

#### Acceptance Criteria

- [ ] Delete action available for each group
- [ ] Warning shows members will lose permissions
- [ ] Confirmation dialog required
- [ ] Group deleted (or soft deleted)
- [ ] Members' effective permissions updated

#### Confirmation Dialog

```
┌─────────────────────────────────────────────────────────┐
│ Delete User Group                                       │
├─────────────────────────────────────────────────────────┤
│ Are you sure you want to delete "Treasury Team"?       │
│                                                         │
│ ⚠️ Warning: This group has 5 members who will lose     │
│ the 8 permissions assigned to this group.              │
│                                                         │
│ Affected users:                                         │
│ - john.doe@acme.com                                     │
│ - jane.smith@acme.com                                   │
│ - bob.wilson@acme.com                                   │
│ - ...and 2 more                                         │
│                                                         │
│                    [Cancel]  [Delete Anyway]            │
└─────────────────────────────────────────────────────────┘
```

---

### US-UG-009: Permission Evaluation with Groups

**As a** system
**I want** to evaluate group permissions in authorization
**So that** users receive their group-level access

#### Acceptance Criteria

- [ ] Permission check queries user's group memberships
- [ ] Group permissions considered after user permissions
- [ ] Group permissions considered before role permissions
- [ ] Multiple group permissions are combined (union)
- [ ] Account scopes from groups are combined

#### Evaluation Flow

```
User requests action X on account A

1. Check user-specific permissions for action X
   └── If found: return result

2. Get all groups user belongs to
   For each group:
     Check group permissions for action X
     └── If found: check account scope includes A
         └── If yes: return allowed

3. Check role permissions for action X
   └── If found: return result

4. Default: denied
```

---

### US-UG-010: Effective Permissions View

**As a** Client Administrator
**I want** to see a user's effective permissions
**So that** I understand their total access

#### Acceptance Criteria

- [ ] User detail shows effective permissions table
- [ ] Each permission shows source (User, Group name, Role)
- [ ] Combined account scope shown
- [ ] Conflicts/overlaps clearly indicated

#### UI Mockup

```
┌─────────────────────────────────────────────────────────────┐
│ Effective Permissions: john.doe@acme.com                    │
├─────────────────────────────────────────────────────────────┤
│ Roles: [VIEWER]                                             │
│ Groups: [Treasury Team] [Approvers]                         │
├─────────────────────────────────────────────────────────────┤
│ Permission                    │ Source        │ Scope       │
├───────────────────────────────┼───────────────┼─────────────┤
│ payments:ach:payment:view     │ Treasury Team │ All         │
│ payments:ach:payment:create   │ Treasury Team │ 3 Accounts  │
│ payments:ach:payment:approve  │ Approvers     │ All         │
│ reporting:*:view              │ Role: VIEWER  │ All         │
│ security:users:view           │ User          │ N/A         │
└───────────────────────────────┴───────────────┴─────────────┘
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/user-groups` | List user groups |
| `POST` | `/api/user-groups` | Create user group |
| `GET` | `/api/user-groups/{id}` | Get group details |
| `PUT` | `/api/user-groups/{id}` | Update group |
| `DELETE` | `/api/user-groups/{id}` | Delete group |
| `GET` | `/api/user-groups/{id}/members` | List group members |
| `POST` | `/api/user-groups/{id}/members` | Add members |
| `DELETE` | `/api/user-groups/{id}/members/{userId}` | Remove member |
| `GET` | `/api/user-groups/{id}/permissions` | List group permissions |
| `POST` | `/api/user-groups/{id}/permissions` | Add permission |
| `DELETE` | `/api/user-groups/{id}/permissions/{permId}` | Remove permission |
| `GET` | `/api/users/{id}/groups` | Get user's groups |
| `GET` | `/api/users/{id}/effective-permissions` | Get effective permissions |

---

## Domain Events

| Event | Trigger | Payload |
|-------|---------|---------|
| `USER_GROUP_CREATED` | New group created | group details |
| `USER_GROUP_UPDATED` | Group name/desc changed | group details |
| `USER_GROUP_DELETED` | Group deleted | group id |
| `USER_ADDED_TO_GROUP` | User added | group id, user id |
| `USER_REMOVED_FROM_GROUP` | User removed | group id, user id |
| `GROUP_PERMISSION_GRANTED` | Permission added | group id, permission |
| `GROUP_PERMISSION_REVOKED` | Permission removed | group id, permission id |

---

## Implementation Notes

1. **Permission Caching**: Consider caching effective permissions per user
2. **Cache Invalidation**: Clear cache when group membership or permissions change
3. **Audit Trail**: All group and permission changes are audited
4. **Profile Scoping**: Groups are scoped to a profile
5. **Multi-Group Membership**: Users can belong to multiple groups
6. **Permission Union**: When user belongs to multiple groups, permissions are combined
