# US-PA-007: Permission Evaluation Order

## Story

**As a** system architect
**I want** a clear evaluation order for permissions
**So that** permission conflicts are resolved consistently and predictably

## Acceptance Criteria

- [ ] Permission evaluation follows: User-specific → Role-based → Deny
- [ ] First matching permission is used (first match wins)
- [ ] User-specific permissions take precedence over role permissions
- [ ] Among multiple roles, all are evaluated equally (no role hierarchy)
- [ ] Evaluation order is documented and consistent
- [ ] No explicit deny permissions (absence of permission = deny)
- [ ] System logs show which permission source was used
- [ ] Performance is optimized by checking user permissions first

## Technical Notes

**Evaluation Algorithm:**
```java
public class PermissionEvaluator {

    public PermissionCheckResult evaluate(
        UserId userId,
        Action action,
        Optional<AccountId> accountId
    ) {
        // Step 1: Check user-specific permissions (highest priority)
        Optional<Permission> userPermission =
            checkUserPermissions(userId, action, accountId);

        if (userPermission.isPresent()) {
            return PermissionCheckResult.allowed(
                userPermission.get(),
                PermissionSource.USER,
                "User-specific permission"
            );
        }

        // Step 2: Check role-based permissions
        Optional<RolePermissionMatch> rolePermission =
            checkRolePermissions(userId, action, accountId);

        if (rolePermission.isPresent()) {
            return PermissionCheckResult.allowed(
                rolePermission.get().permission(),
                PermissionSource.ROLE,
                "Role: " + rolePermission.get().role().getName()
            );
        }

        // Step 3: No matching permission found - deny by default
        return PermissionCheckResult.denied(
            "No matching permission found",
            DenialReason.NO_PERMISSION
        );
    }

    private Optional<Permission> checkUserPermissions(
        UserId userId,
        Action action,
        Optional<AccountId> accountId
    ) {
        List<UserPermission> userPermissions =
            userPermissionRepository.findActiveByUserId(userId);

        return userPermissions.stream()
            .map(UserPermission::getPermission)
            .filter(p -> p.matches(action))
            .filter(p -> accountId.isEmpty() || p.allowsAccount(accountId.get()))
            .findFirst(); // First match wins
    }

    private Optional<RolePermissionMatch> checkRolePermissions(
        UserId userId,
        Action action,
        Optional<AccountId> accountId
    ) {
        List<Role> userRoles = roleRepository.findByUserId(userId);

        // Check all roles in order assigned (no hierarchy)
        return userRoles.stream()
            .flatMap(role -> role.getPermissions().stream()
                .filter(p -> p.matches(action))
                .filter(p -> accountId.isEmpty() || p.allowsAccount(accountId.get()))
                .map(p -> new RolePermissionMatch(role, p)))
            .findFirst(); // First match across all roles wins
    }
}

record RolePermissionMatch(Role role, Permission permission) {}
```

**Permission Source Tracking:**
```java
public enum PermissionSource {
    USER,           // User-specific permission
    ROLE,           // Role-based permission
    NONE            // No permission (denied)
}

public record PermissionCheckResult(
    boolean allowed,
    PermissionSource source,
    String sourceDetails,  // e.g., "Role: VIEWER" or "User-specific permission"
    Optional<Permission> matchedPermission,
    Optional<DenialReason> denialReason
) {
    public static PermissionCheckResult allowed(
        Permission permission,
        PermissionSource source,
        String sourceDetails
    ) {
        return new PermissionCheckResult(
            true,
            source,
            sourceDetails,
            Optional.of(permission),
            Optional.empty()
        );
    }

    public static PermissionCheckResult denied(
        String sourceDetails,
        DenialReason reason
    ) {
        return new PermissionCheckResult(
            false,
            PermissionSource.NONE,
            sourceDetails,
            Optional.empty(),
            Optional.of(reason)
        );
    }
}

public enum DenialReason {
    NO_PERMISSION,           // No matching permission found
    INSUFFICIENT_SCOPE,      // Permission exists but wrong account scope
    REVOKED_PERMISSION       // Permission was revoked
}
```

**Evaluation Priority Examples:**

1. **User Permission Overrides Role:**
   - User has: "direct:client-portal:profile:view" for account-001 (USER)
   - Role has: "direct:client-portal:profile:view" for ALL_ACCOUNTS (ROLE)
   - Check: view profile-002
   - Result: DENIED (user permission checked first, doesn't match account)

2. **First Role Match Wins:**
   - Role1 (VIEWER): "direct:client-portal:*:view" for ALL_ACCOUNTS
   - Role2 (CREATOR): "direct:client-portal:*:create" for ALL_ACCOUNTS
   - Check: view profile
   - Result: ALLOWED via Role1 (whichever is evaluated first)

3. **No Explicit Deny:**
   - User has: "direct:client-portal:profile:view"
   - Check: "direct:client-portal:profile:delete"
   - Result: DENIED (no matching permission, not explicit deny)

**Logging and Auditing:**
```java
@Slf4j
public class PermissionEvaluator {

    public PermissionCheckResult evaluate(...) {
        PermissionCheckResult result = performEvaluation(...);

        log.info(
            "Permission check: userId={}, action={}, accountId={}, " +
            "allowed={}, source={}, details={}",
            userId, action, accountId,
            result.allowed(), result.source(), result.sourceDetails()
        );

        return result;
    }
}
```

**Performance Optimization:**
- User permissions checked first (smaller dataset)
- Early return on first match
- Lazy evaluation of role permissions
- Cache effective permissions per user

## Dependencies

- US-PA-001: Define Action URN Structure
- US-PA-002: Role-Based Permission Assignment
- US-PA-003: User-Specific Permission Override
- US-PA-005: Permission Check API

## Test Cases

1. **User Permission Takes Precedence**
   - Given: User has user-specific permission AND role permission
   - When: Permission check performed
   - Then: User permission is used, source=USER

2. **Role Permission Used When No User Permission**
   - Given: User has only role permission
   - When: Permission check performed
   - Then: Role permission is used, source=ROLE

3. **First Role Match Wins**
   - Given: User has VIEWER and CREATOR roles (both match)
   - When: Permission check performed
   - Then: First matching role permission used

4. **Deny When No Match**
   - Given: User has no matching permissions
   - When: Permission check performed
   - Then: Denied, reason=NO_PERMISSION

5. **User Permission Narrows Role Permission**
   - Given: User has role with ALL_ACCOUNTS, user permission with SPECIFIC_ACCOUNTS
   - When: Check account not in user permission
   - Then: Denied (user permission checked first)

6. **Multiple Roles Evaluated**
   - Given: User has 3 roles, 3rd role has matching permission
   - When: Permission check performed
   - Then: Allowed via 3rd role

7. **Revoked Permission Skipped**
   - Given: User has revoked permission
   - When: Permission check performed
   - Then: Revoked permission not considered

8. **Evaluation Order Logged**
   - Given: Permission check performed
   - When: Logs examined
   - Then: Log shows source and evaluation path

9. **Performance - User Permission First**
   - Given: User has user-specific permission
   - When: Permission check performed
   - Then: Role permissions not evaluated (short circuit)

## UI/UX (if applicable)

**Permission Debugger (Admin Tool):**

Shows evaluation flow for debugging:

```
Permission Check: direct:client-portal:profile:view (account: profile-001)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Step 1: Check User Permissions
  ✓ Found: direct:client-portal:profile:view [SPECIFIC_ACCOUNTS: profile-001, profile-002]
  ✓ Action Match: Yes
  ✓ Account Match: Yes (profile-001 in allowed list)

  → ALLOWED (source: USER)

Step 2: Check Role Permissions [SKIPPED - Already matched]
  - Role: VIEWER
  - Role: CREATOR

Result: ALLOWED
Source: User-specific permission
Granted By: admin@example.com
Granted At: 2025-12-15 10:30:00
```

**Example Denial:**
```
Permission Check: direct:client-portal:profile:delete (account: profile-001)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Step 1: Check User Permissions
  ✗ No matching user permissions found

Step 2: Check Role Permissions
  - Role: VIEWER
    ✗ No matching permission for 'delete' action
  - Role: CREATOR
    ✗ No matching permission for 'delete' action

Result: DENIED
Reason: NO_PERMISSION
Message: User does not have permission to delete profiles
```
