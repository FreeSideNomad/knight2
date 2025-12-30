# US-PA-005: Permission Check API

## Story

**As a** application developer
**I want** a simple API to check if a user is allowed to perform an action
**So that** I can enforce authorization consistently across all application features

## Acceptance Criteria

- [ ] POST /api/permissions/check endpoint exists
- [ ] Endpoint accepts userId, action URN, and optional accountId
- [ ] Returns boolean allowed/denied response
- [ ] Response includes reason for denial (no permission, insufficient scope, etc.)
- [ ] Evaluates both role-based and user-specific permissions
- [ ] Supports wildcard matching in action URNs
- [ ] Response time is under 100ms for typical queries
- [ ] Endpoint requires authentication
- [ ] Results can be cached for performance

## Technical Notes

**API Endpoint:**
```
POST /api/permissions/check
Content-Type: application/json

Request Body:
{
  "userId": "user-uuid",
  "action": "direct:client-portal:profile:view",
  "accountId": "profile-001"  // optional
}

Response 200 OK:
{
  "allowed": true,
  "matchedPermission": {
    "action": "direct:client-portal:profile:view",
    "source": "ROLE",  // or "USER"
    "sourceId": "role-uuid",
    "sourceName": "VIEWER"
  }
}

Response 200 OK (Denied):
{
  "allowed": false,
  "reason": "NO_MATCHING_PERMISSION",
  "message": "User does not have permission for action: direct:client-portal:profile:view"
}

Response 200 OK (Account Denied):
{
  "allowed": false,
  "reason": "INSUFFICIENT_SCOPE",
  "message": "User has permission but not for account: profile-001",
  "availableAccounts": ["profile-002", "profile-003"]
}
```

**Permission Evaluation Algorithm:**
```java
public PermissionCheckResult isAllowed(
    UserId userId,
    Action action,
    Optional<AccountId> accountId
) {
    // 1. Get user-specific permissions (highest priority)
    List<UserPermission> userPerms = userPermissionRepo
        .findByUserIdAndNotRevoked(userId);

    Optional<Permission> userMatch = findMatchingPermission(
        userPerms, action, accountId
    );
    if (userMatch.isPresent()) {
        return allowed(userMatch.get(), "USER");
    }

    // 2. Get role-based permissions
    List<Role> roles = userRoleRepo.findByUserId(userId);
    for (Role role : roles) {
        Optional<Permission> roleMatch = findMatchingPermission(
            role.getPermissions(), action, accountId
        );
        if (roleMatch.isPresent()) {
            return allowed(roleMatch.get(), "ROLE", role);
        }
    }

    // 3. No match found
    return denied("NO_MATCHING_PERMISSION");
}

private Optional<Permission> findMatchingPermission(
    Collection<Permission> permissions,
    Action action,
    Optional<AccountId> accountId
) {
    return permissions.stream()
        .filter(p -> p.matches(action))
        .filter(p -> accountId.isEmpty() || p.allowsAccount(accountId.get()))
        .findFirst();
}
```

**Performance Optimizations:**
- Cache user permissions in Redis with 5-minute TTL
- Cache key: `permissions:{userId}`
- Invalidate cache on permission grant/revoke
- Database indexes on user_id and action columns
- Eager load role permissions to avoid N+1 queries

**Caching Strategy:**
```java
@Cacheable(value = "userPermissions", key = "#userId")
public Set<Permission> getEffectivePermissions(UserId userId) {
    // Combines role + user permissions
}

@CacheEvict(value = "userPermissions", key = "#userId")
public void grantPermission(UserId userId, Permission permission) {
    // Grant and invalidate cache
}
```

**Error Responses:**
```
400 Bad Request - Invalid action URN format
401 Unauthorized - Not authenticated
404 Not Found - User does not exist
500 Internal Server Error - System error
```

## Dependencies

- US-PA-001: Define Action URN Structure
- US-PA-002: Role-Based Permission Assignment
- US-PA-003: User-Specific Permission Override
- US-PA-004: Account-Level Permission Scope

## Test Cases

1. **Allow Based on Role Permission**
   - Given: User has VIEWER role
   - When: Check permission for "direct:client-portal:profile:view"
   - Then: Returns allowed=true, source=ROLE

2. **Allow Based on User Permission**
   - Given: User has specific permission (not from role)
   - When: Check permission for that action
   - Then: Returns allowed=true, source=USER

3. **Deny - No Permission**
   - Given: User has no relevant permissions
   - When: Check permission for "direct:client-portal:profile:delete"
   - Then: Returns allowed=false, reason=NO_MATCHING_PERMISSION

4. **Deny - Insufficient Scope**
   - Given: User has SPECIFIC_ACCOUNTS permission for acc-001
   - When: Check permission for acc-002
   - Then: Returns allowed=false, reason=INSUFFICIENT_SCOPE

5. **Allow - All Accounts Scope**
   - Given: User has ALL_ACCOUNTS permission
   - When: Check permission for any account
   - Then: Returns allowed=true

6. **User Permission Overrides Role**
   - Given: User has both role and user-specific permission
   - When: Check permission
   - Then: Returns allowed=true, source=USER (user takes priority)

7. **Wildcard Matching**
   - Given: User has permission "direct:client-portal:*:view"
   - When: Check "direct:client-portal:profile:view"
   - Then: Returns allowed=true

8. **Check Without Account ID**
   - Given: User has permission
   - When: Check without accountId parameter
   - Then: Returns allowed=true if permission exists (ignores scope)

9. **Performance Test**
   - Given: User with 10 roles and 20 permissions
   - When: Perform 100 permission checks
   - Then: Average response time < 100ms

10. **Cache Invalidation**
    - Given: User permissions cached
    - When: New permission granted
    - Then: Next check reflects new permission

## UI/UX (if applicable)

**Not a UI feature, but used by UI components:**

Example usage in frontend:
```javascript
async function canUserViewProfile(userId, profileId) {
  const response = await fetch('/api/permissions/check', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      userId: userId,
      action: 'direct:client-portal:profile:view',
      accountId: profileId
    })
  });

  const result = await response.json();
  return result.allowed;
}

// Usage: Hide/show UI elements
if (await canUserViewProfile(currentUser.id, profileId)) {
  showViewButton();
} else {
  hideViewButton();
}
```

**Developer Testing UI (Admin Tool):**
- Permission checker panel in admin console
- Input fields: User ID, Action URN, Account ID (optional)
- "Check Permission" button
- Display result with color coding (green=allowed, red=denied)
- Show matched permission details
- Show evaluation path (which role/permission matched)
