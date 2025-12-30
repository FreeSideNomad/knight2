# US-UG-009: Permission Evaluation with Groups

## Story

**As a** System
**I want** to evaluate user permissions from User, Group, and Role sources hierarchically
**So that** access control decisions consider all permission sources correctly

## Acceptance Criteria

- [ ] Permission evaluation checks User → Group → Role in order
- [ ] User direct permissions have highest priority
- [ ] Group permissions applied for all user's group memberships
- [ ] Role permissions provide baseline access
- [ ] Permissions are additive (union of all sources)
- [ ] Permission cache invalidates when group membership changes
- [ ] Permission cache invalidates when group permissions change
- [ ] Authorization checks use effective permissions
- [ ] Performance is optimized with appropriate caching
- [ ] Evaluation handles users with no groups gracefully
- [ ] Evaluation handles groups with no permissions gracefully

## Technical Notes

**Permission Evaluation Algorithm:**
```java
EffectivePermissions calculateEffectivePermissions(UserId userId) {
    Set<Permission> effectivePermissions = new HashSet<>();

    // 1. Direct user permissions (highest priority)
    effectivePermissions.addAll(userPermissionRepository.findByUserId(userId));

    // 2. Group permissions (for all groups user belongs to)
    List<UserGroup> userGroups = userGroupRepository.findByUserId(userId);
    for (UserGroup group : userGroups) {
        effectivePermissions.addAll(groupPermissionRepository.findByGroupId(group.getId()));
    }

    // 3. Role-based permissions (baseline)
    User user = userRepository.findById(userId);
    if (user.hasRole()) {
        effectivePermissions.addAll(rolePermissionRepository.findByRole(user.getRole()));
    }

    return new EffectivePermissions(effectivePermissions);
}
```

**Caching Strategy:**
- Cache key: `user:{userId}:effective-permissions`
- Cache TTL: 5 minutes (configurable)
- Invalidate cache on:
  - User added to or removed from group
  - Group permissions added or removed
  - User direct permissions changed
  - User role changed
- Use Redis or in-memory cache
- Implement cache-aside pattern

**API Changes:**
- Internal service: `PermissionEvaluationService`
  - Method: `hasPermission(UserId, Permission): boolean`
  - Method: `getEffectivePermissions(UserId): Set<Permission>`
  - Method: `canAccessClient(UserId, ClientId): boolean`
  - Method: `canAccessAccount(UserId, AccountId): boolean`
  - Method: `hasRole(UserId, RoleName): boolean`

**Database Queries:**
- Optimize with appropriate indexes
- Use JOIN queries to reduce round trips
- Example query for effective permissions:
  ```sql
  SELECT DISTINCT permission_type, client_id, account_id, role_name
  FROM (
    -- Direct permissions
    SELECT permission_type, client_id, account_id, role_name, 'USER' as source
    FROM user_permissions
    WHERE user_id = ?

    UNION ALL

    -- Group permissions
    SELECT gp.permission_type, gp.client_id, gp.account_id, gp.role_name, 'GROUP' as source
    FROM user_group_permissions gp
    JOIN user_group_members ugm ON gp.user_group_id = ugm.user_group_id
    WHERE ugm.user_id = ?

    UNION ALL

    -- Role permissions (future)
    SELECT permission_type, client_id, account_id, role_name, 'ROLE' as source
    FROM role_permissions
    WHERE role_name = (SELECT role FROM users WHERE id = ?)
  ) all_permissions
  ```

**Spring Security Integration:**
- Implement custom `PermissionEvaluator`
- Use in `@PreAuthorize` annotations:
  - `@PreAuthorize("@permissionEvaluator.hasClientAccess(#clientId)")`
- Inject into security filter chain

## Dependencies

- US-UG-002: Add Users to Group must be completed
- US-UG-004: Assign Permissions to Group must be completed
- Existing user permission system must be in place

## Test Cases

1. **User with Only Direct Permissions**
   - Given user "john@example.com" has direct CLIENT_ACCESS to "TechCo"
   - And john belongs to no groups
   - When evaluating john's effective permissions
   - Then result includes only "TechCo" access
   - And source is "USER"

2. **User with Only Group Permissions**
   - Given user "jane@example.com" has no direct permissions
   - And jane is member of "Sales" group with CLIENT_ACCESS to "Acme Corp"
   - When evaluating jane's effective permissions
   - Then result includes "Acme Corp" access
   - And source is "GROUP: Sales"

3. **User with Both Direct and Group Permissions**
   - Given user "bob@example.com" has direct CLIENT_ACCESS to "TechCo"
   - And bob is member of "Engineering" with CLIENT_ACCESS to "StartupXYZ"
   - When evaluating bob's effective permissions
   - Then result includes both "TechCo" and "StartupXYZ" access
   - And sources are correctly identified

4. **User in Multiple Groups**
   - Given user "alice@example.com" is member of "Engineering" and "Leadership"
   - And "Engineering" has CLIENT_ACCESS to "TechCo"
   - And "Leadership" has CLIENT_ACCESS to "Acme Corp"
   - When evaluating alice's effective permissions
   - Then result includes both "TechCo" and "Acme Corp" access
   - And both have source "GROUP" with respective group names

5. **Permissions Are Additive**
   - Given user has 2 direct permissions, 3 from "Group A", 2 from "Group B"
   - When evaluating effective permissions
   - Then total is 7 permissions (union of all)
   - And no permissions are excluded

6. **Duplicate Permission from Multiple Sources**
   - Given user has direct CLIENT_ACCESS to "TechCo"
   - And user's group also grants CLIENT_ACCESS to "TechCo"
   - When evaluating effective permissions
   - Then "TechCo" appears once in set
   - But metadata tracks both sources

7. **Authorization Check Uses Effective Permissions**
   - Given user has group permission to access "Acme Corp"
   - When API request to `/api/clients/acme-corp-id` is made
   - Then authorization succeeds
   - And request is processed

8. **Cache Invalidation on Group Membership Change**
   - Given user's effective permissions are cached
   - When user is added to new group
   - Then cache is invalidated
   - And next permission check recalculates
   - And includes new group's permissions

9. **Cache Invalidation on Group Permission Change**
   - Given user is member of "Sales" group
   - And user's effective permissions are cached
   - When new permission is added to "Sales" group
   - Then cache is invalidated for all group members
   - And they receive new permission immediately

10. **Performance with Large Groups**
    - Given group "All Employees" has 1000 members
    - When permission is added to the group
    - Then all 1000 member caches are invalidated
    - And new permission checks complete within SLA (< 100ms)

11. **User with No Permissions**
    - Given user "charlie@example.com" has no direct permissions
    - And charlie belongs to no groups
    - And charlie has no role
    - When evaluating effective permissions
    - Then result is empty set
    - And authorization checks fail appropriately

12. **Group with No Permissions**
    - Given user is member of "Empty Group" with no permissions
    - When evaluating effective permissions
    - Then group contributes nothing
    - And only other sources are included

## UI/UX (if applicable)

N/A - This is a backend service story with no direct UI component.

However, the results of permission evaluation are displayed in:
- US-UG-006: View User's Groups (shows effective permissions)
- US-UG-010: Effective Permissions View (displays evaluation results)

## Performance Requirements

- Permission evaluation: < 100ms (95th percentile)
- Cache hit ratio: > 90%
- Cache invalidation: < 1 second propagation
- Support up to 10,000 users per profile
- Support up to 1,000 groups per profile
- Support users in up to 50 groups each

## Security Considerations

- Never expose permission evaluation logic to client
- Log all authorization failures for audit
- Prevent permission escalation through group manipulation
- Validate all permission checks on server side
- Use parameterized queries to prevent SQL injection
- Implement rate limiting on permission checks
