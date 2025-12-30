# US-PA-003: User-Specific Permission Override

## Story

**As a** security administrator
**I want** to grant or revoke individual permissions to specific users
**So that** I can fine-tune access control beyond role-based permissions

## Acceptance Criteria

- [ ] Individual permissions can be granted directly to users
- [ ] User-specific permissions override role-based permissions
- [ ] Permissions can be granted with ALL_ACCOUNTS or SPECIFIC_ACCOUNTS scope
- [ ] For SPECIFIC_ACCOUNTS scope, specific account IDs must be provided
- [ ] Individual permissions can be revoked from users
- [ ] API endpoints exist to grant/revoke user permissions
- [ ] User permissions are persisted in database
- [ ] Granting a permission records who granted it and when

## Technical Notes

**Domain Model:**
- Create `UserPermission` aggregate with:
  - UserPermissionId (value object)
  - userId (UserId)
  - permission (Permission value object)
  - grantedAt (timestamp)
  - grantedBy (UserId)
  - revoked (boolean)
  - revokedAt (timestamp, nullable)
  - revokedBy (UserId, nullable)

**Permission Value Object:**
```java
public record Permission(
    Action action,
    PermissionScope scope,
    Set<AccountId> accountIds
) {
    // Validation in constructor
    public Permission {
        if (scope == PermissionScope.SPECIFIC_ACCOUNTS && accountIds.isEmpty()) {
            throw new IllegalArgumentException("SPECIFIC_ACCOUNTS requires at least one accountId");
        }
        if (scope == PermissionScope.ALL_ACCOUNTS && !accountIds.isEmpty()) {
            throw new IllegalArgumentException("ALL_ACCOUNTS cannot have specific accountIds");
        }
    }
}
```

**Database Schema:**
```sql
CREATE TABLE user_permissions (
    user_permission_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    action VARCHAR(255) NOT NULL,
    scope VARCHAR(50) NOT NULL,
    granted_at TIMESTAMP NOT NULL,
    granted_by UUID NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP,
    revoked_by UUID,
    UNIQUE (user_id, action, revoked)
);

CREATE TABLE user_permission_accounts (
    user_permission_id UUID NOT NULL,
    account_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_permission_id, account_id),
    FOREIGN KEY (user_permission_id) REFERENCES user_permissions(user_permission_id)
);

CREATE INDEX idx_user_permissions_user ON user_permissions(user_id, revoked);
```

**API Endpoints:**
```
POST /api/users/{userId}/permissions
  Body: {
    "action": "direct:client-portal:profile:view",
    "scope": "SPECIFIC_ACCOUNTS",
    "accountIds": ["acc-123", "acc-456"]
  }
  Response: {
    "userPermissionId": "uuid",
    "userId": "uuid",
    "permission": { ... },
    "grantedAt": "2025-12-30T10:00:00Z",
    "grantedBy": "uuid"
  }

DELETE /api/users/{userId}/permissions/{userPermissionId}
  Response: 204 No Content

GET /api/users/{userId}/permissions
  Response: [{
    "userPermissionId": "uuid",
    "permission": { ... },
    "grantedAt": "...",
    "grantedBy": "...",
    "revoked": false
  }]

PUT /api/users/{userId}/permissions/{userPermissionId}
  Body: {
    "scope": "ALL_ACCOUNTS",
    "accountIds": []
  }
  Response: Updated permission
```

**Business Rules:**
- Only SECURITY_ADMIN or SUPER_ADMIN can grant/revoke permissions
- Cannot grant permission that grantor doesn't have
- Revoking sets revoked=true, preserves history
- Duplicate active permissions are prevented by unique constraint
- Account IDs are validated against existing accounts

## Dependencies

- US-PA-001: Define Action URN Structure
- US-PA-002: Role-Based Permission Assignment

## Test Cases

1. **Grant Permission with All Accounts Scope**
   - Given: Security admin wants to grant view permission
   - When: POST /api/users/{userId}/permissions with scope ALL_ACCOUNTS
   - Then: Permission is granted, user can view all accounts

2. **Grant Permission with Specific Accounts**
   - Given: Security admin wants to grant view for specific accounts
   - When: POST /api/users/{userId}/permissions with accountIds
   - Then: Permission is granted only for specified accounts

3. **Revoke User Permission**
   - Given: User has granted permission
   - When: DELETE /api/users/{userId}/permissions/{permissionId}
   - Then: Permission is marked as revoked, user loses access

4. **Prevent Duplicate Permissions**
   - Given: User already has active permission for action
   - When: POST same permission again
   - Then: 409 Conflict error returned

5. **Validate Account IDs**
   - Given: Granting SPECIFIC_ACCOUNTS permission
   - When: accountIds contains non-existent account
   - Then: 400 Bad Request with validation error

6. **Update Permission Scope**
   - Given: User has SPECIFIC_ACCOUNTS permission
   - When: PUT to change scope to ALL_ACCOUNTS
   - Then: Permission scope is updated, all accounts accessible

7. **Non-Admin Cannot Grant Permissions**
   - Given: User without SECURITY_ADMIN role
   - When: POST /api/users/{userId}/permissions
   - Then: 403 Forbidden error returned

8. **List User Permissions**
   - Given: User has multiple granted permissions
   - When: GET /api/users/{userId}/permissions
   - Then: All active permissions returned, revoked excluded by default

## UI/UX (if applicable)

**User Permission Management Panel:**
- Table showing all user-specific permissions
- Columns: Action, Scope, Accounts, Granted At, Granted By, Actions
- "Add Permission" button opens permission dialog
- Permission dialog:
  - Action URN input with autocomplete
  - Scope radio buttons: All Accounts / Specific Accounts
  - Account multi-select (shown only for Specific Accounts)
  - Save and Cancel buttons
- Each permission row has:
  - Edit icon (to change scope/accounts)
  - Revoke icon (with confirmation)
- Visual indicator for user-specific vs role-inherited permissions
- Show effective permissions after combining role + user permissions
