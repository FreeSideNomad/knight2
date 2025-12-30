# US-PA-002: Role-Based Permission Assignment

## Story

**As a** security administrator
**I want** to assign predefined roles to users
**So that** users automatically receive appropriate permissions based on their role

## Acceptance Criteria

- [ ] System supports five predefined roles: SUPER_ADMIN, SECURITY_ADMIN, VIEWER, CREATOR, APPROVER
- [ ] Each role has a predefined set of permissions
- [ ] Users can be assigned one or more roles
- [ ] Role assignments can be added and removed
- [ ] Role permissions are automatically applied to users
- [ ] Role definitions are stored in database
- [ ] API endpoint exists to assign/remove roles from users
- [ ] Role assignments are validated (role exists, user exists)

## Technical Notes

**Domain Model:**
- Create `Role` aggregate with:
  - RoleId (value object)
  - name (SUPER_ADMIN, SECURITY_ADMIN, VIEWER, CREATOR, APPROVER)
  - description
  - permissions (Set<Permission>)
  - createdAt, updatedAt

- Create `Permission` value object with:
  - action (Action URN)
  - scope (ALL_ACCOUNTS or SPECIFIC_ACCOUNTS)
  - accountIds (Set<AccountId>) - only for SPECIFIC_ACCOUNTS scope

**Predefined Role Permissions:**

SUPER_ADMIN:
- `*:*:*:*` with scope ALL_ACCOUNTS (full system access)

SECURITY_ADMIN:
- `admin:user-management:user:*` with scope ALL_ACCOUNTS
- `admin:user-management:role:*` with scope ALL_ACCOUNTS
- `admin:user-management:permission:*` with scope ALL_ACCOUNTS

VIEWER:
- `direct:client-portal:*:view` with scope ALL_ACCOUNTS
- `indirect:indirect-portal:*:view` with scope ALL_ACCOUNTS
- `bank:payor-enrolment:*:view` with scope ALL_ACCOUNTS

CREATOR:
- All VIEWER permissions
- `direct:client-portal:*:create` with scope ALL_ACCOUNTS
- `indirect:indirect-portal:*:create` with scope ALL_ACCOUNTS

APPROVER:
- All VIEWER permissions
- `direct:client-portal:*:approve` with scope ALL_ACCOUNTS
- `indirect:indirect-portal:*:approve` with scope ALL_ACCOUNTS
- `bank:payor-enrolment:*:approve` with scope ALL_ACCOUNTS

**Database Schema:**
```sql
CREATE TABLE roles (
    role_id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL,
    action VARCHAR(255) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (role_id, action),
    FOREIGN KEY (role_id) REFERENCES roles(role_id)
);

CREATE TABLE role_permission_accounts (
    role_id UUID NOT NULL,
    action VARCHAR(255) NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, action, account_id),
    FOREIGN KEY (role_id, action) REFERENCES role_permissions(role_id, action)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL,
    assigned_by UUID NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (role_id) REFERENCES roles(role_id)
);
```

**API Endpoints:**
```
POST /api/users/{userId}/roles
  Body: { "roleId": "uuid" }

DELETE /api/users/{userId}/roles/{roleId}

GET /api/users/{userId}/roles
  Response: [{ "roleId": "uuid", "name": "VIEWER", "assignedAt": "..." }]

GET /api/roles
  Response: [{ "roleId": "uuid", "name": "VIEWER", "description": "..." }]
```

## Dependencies

- US-PA-001: Define Action URN Structure

## Test Cases

1. **Assign Role to User**
   - Given: User exists, Role VIEWER exists
   - When: POST /api/users/{userId}/roles with roleId
   - Then: Role is assigned, user gets VIEWER permissions

2. **Remove Role from User**
   - Given: User has VIEWER role assigned
   - When: DELETE /api/users/{userId}/roles/{roleId}
   - Then: Role is removed, permissions are revoked

3. **Assign Multiple Roles**
   - Given: User exists
   - When: VIEWER and CREATOR roles are assigned
   - Then: User has combined permissions from both roles

4. **Assign Invalid Role**
   - Given: Role does not exist
   - When: POST /api/users/{userId}/roles with invalid roleId
   - Then: 404 Not Found error returned

5. **List User Roles**
   - Given: User has VIEWER and CREATOR roles
   - When: GET /api/users/{userId}/roles
   - Then: Both roles are returned with assignment details

6. **SUPER_ADMIN Has All Permissions**
   - Given: User has SUPER_ADMIN role
   - When: Permission check for any action
   - Then: Always returns allowed

## UI/UX (if applicable)

**User Role Management Screen:**
- Display list of assigned roles with badges
- "Add Role" button opens role selection dropdown
- Each role shows: name, description, assigned date, assigned by
- "Remove" icon next to each role
- Show inherited permissions from each role (expandable)
- Color coding: SUPER_ADMIN (red), SECURITY_ADMIN (orange), others (blue)
