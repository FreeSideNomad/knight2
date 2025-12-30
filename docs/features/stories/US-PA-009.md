# US-PA-009: Wildcard Permission Matching

## Story

**As a** security administrator
**I want** to use wildcards in action URNs
**So that** I can grant broad permissions without creating many individual entries

## Acceptance Criteria

- [ ] Asterisk (*) matches any single segment in action URN
- [ ] Wildcards supported in any segment position
- [ ] Multiple wildcards in single URN supported
- [ ] Wildcard permissions evaluated correctly during permission checks
- [ ] Wildcard matching is case-sensitive
- [ ] Partial wildcards not supported (e.g., "prof*" - only full segment "*")
- [ ] Wildcard permissions documented with examples
- [ ] Performance impact of wildcards is acceptable

## Technical Notes

**Wildcard Rules:**
- `*` matches exactly one segment
- Only full-segment wildcards (not partial like `prof*`)
- Case-sensitive matching
- Left-to-right evaluation

**Valid Wildcard Patterns:**
```
*:*:*:*                           # Matches everything (superuser)
direct:*:*:view                   # All view actions in direct service
*:client-portal:profile:view      # View profiles across all service types
direct:client-portal:*:*          # All actions on all resources in client-portal
*:*:profile:*                     # All actions on profiles across all services
direct:client-portal:*:view       # View all resources in client-portal
```

**Invalid Patterns:**
```
prof*:view                        # Partial segment wildcard not allowed
direct:**:view                    # Double wildcard not allowed
direct:client-portal:profile      # Too few segments
```

**Matching Algorithm:**
```java
public class WildcardPermissionMatcher {

    public boolean matches(Action permissionAction, Action requestedAction) {
        String[] permissionSegments = permissionAction.toUrn().split(":");
        String[] requestedSegments = requestedAction.toUrn().split(":");

        // Must have same number of segments
        if (permissionSegments.length != requestedSegments.length) {
            return false;
        }

        // Check each segment
        for (int i = 0; i < permissionSegments.length; i++) {
            if (!segmentMatches(permissionSegments[i], requestedSegments[i])) {
                return false;
            }
        }

        return true;
    }

    private boolean segmentMatches(String permissionSegment, String requestedSegment) {
        // Wildcard matches anything
        if ("*".equals(permissionSegment)) {
            return true;
        }

        // Exact match (case-sensitive)
        return permissionSegment.equals(requestedSegment);
    }
}
```

**Specificity and Precedence:**
When multiple permissions match, more specific permissions take precedence:

1. Exact match (no wildcards): Highest priority
2. Fewer wildcards: Higher priority
3. Wildcards in later segments: Higher priority

```java
public class PermissionSpecificityComparator implements Comparator<Permission> {

    @Override
    public int compare(Permission p1, Permission p2) {
        String[] segments1 = p1.getAction().toUrn().split(":");
        String[] segments2 = p2.getAction().toUrn().split(":");

        // Count wildcards (fewer is more specific)
        int wildcards1 = countWildcards(segments1);
        int wildcards2 = countWildcards(segments2);

        if (wildcards1 != wildcards2) {
            return Integer.compare(wildcards1, wildcards2);
        }

        // Same number of wildcards, compare left-to-right
        for (int i = 0; i < segments1.length; i++) {
            boolean isWild1 = "*".equals(segments1[i]);
            boolean isWild2 = "*".equals(segments2[i]);

            if (isWild1 != isWild2) {
                return isWild1 ? 1 : -1; // non-wildcard is more specific
            }
        }

        return 0; // Equal specificity
    }

    private int countWildcards(String[] segments) {
        return (int) Arrays.stream(segments)
            .filter("*"::equals)
            .count();
    }
}
```

**Enhanced Permission Check:**
```java
public Optional<Permission> findMatchingPermission(
    Collection<Permission> permissions,
    Action action,
    Optional<AccountId> accountId
) {
    WildcardPermissionMatcher matcher = new WildcardPermissionMatcher();

    return permissions.stream()
        // Filter to matching permissions
        .filter(p -> matcher.matches(p.getAction(), action))
        // Filter by account scope
        .filter(p -> accountId.isEmpty() || p.allowsAccount(accountId.get()))
        // Sort by specificity (most specific first)
        .sorted(new PermissionSpecificityComparator())
        // Return most specific match
        .findFirst();
}
```

**Performance Considerations:**
- Wildcards require full iteration of permissions (can't use hash-based lookup)
- Optimize by checking exact matches first
- Cache compiled wildcard patterns
- Index permissions by first non-wildcard segment

**Optimized Lookup:**
```java
public class PermissionIndex {
    // Group by first segment for faster lookup
    private Map<String, List<Permission>> indexByServiceType = new HashMap<>();
    private List<Permission> fullyWildcard = new ArrayList<>();

    public void add(Permission permission) {
        String[] segments = permission.getAction().toUrn().split(":");
        if ("*".equals(segments[0])) {
            fullyWildcard.add(permission);
        } else {
            indexByServiceType
                .computeIfAbsent(segments[0], k -> new ArrayList<>())
                .add(permission);
        }
    }

    public List<Permission> findCandidates(Action action) {
        String[] segments = action.toUrn().split(":");
        List<Permission> candidates = new ArrayList<>();

        // Add exact service type matches
        candidates.addAll(
            indexByServiceType.getOrDefault(segments[0], Collections.emptyList())
        );

        // Add wildcard matches
        candidates.addAll(fullyWildcard);

        return candidates;
    }
}
```

## Dependencies

- US-PA-001: Define Action URN Structure
- US-PA-005: Permission Check API

## Test Cases

1. **Match All Wildcard**
   - Given: Permission "*:*:*:*"
   - When: Check "direct:client-portal:profile:view"
   - Then: Matches

2. **Match Service Type Wildcard**
   - Given: Permission "*:client-portal:profile:view"
   - When: Check "direct:client-portal:profile:view"
   - Then: Matches

3. **Match Resource Wildcard**
   - Given: Permission "direct:client-portal:*:view"
   - When: Check "direct:client-portal:profile:view"
   - Then: Matches

4. **Match Action Type Wildcard**
   - Given: Permission "direct:client-portal:profile:*"
   - When: Check "direct:client-portal:profile:delete"
   - Then: Matches

5. **No Match - Wrong Service**
   - Given: Permission "direct:*:*:view"
   - When: Check "indirect:client-portal:profile:view"
   - Then: Does not match

6. **Multiple Wildcards**
   - Given: Permission "*:*:profile:view"
   - When: Check "direct:client-portal:profile:view"
   - Then: Matches

7. **Specificity - Exact Over Wildcard**
   - Given: Permissions ["direct:client-portal:profile:view", "*:*:*:view"]
   - When: Check "direct:client-portal:profile:view"
   - Then: Exact match selected

8. **Specificity - Fewer Wildcards**
   - Given: Permissions ["direct:*:profile:view", "*:*:*:view"]
   - When: Check "direct:client-portal:profile:view"
   - Then: "direct:*:profile:view" selected (fewer wildcards)

9. **Reject Partial Wildcard**
   - Given: Creating permission "prof*:view:*:*"
   - When: Validate
   - Then: Validation error (partial wildcards not allowed)

10. **Case Sensitive Matching**
    - Given: Permission "Direct:*:*:view" (capital D)
    - When: Check "direct:client-portal:profile:view"
    - Then: Does not match

11. **Performance - Large Permission Set**
    - Given: 1000 permissions with various wildcards
    - When: Perform 100 permission checks
    - Then: Average check time < 50ms

12. **Wildcard with Account Scope**
    - Given: Permission "*:*:*:view" with SPECIFIC_ACCOUNTS
    - When: Check matching action with allowed account
    - Then: Matches

## UI/UX (if applicable)

**Wildcard Helper in Permission Dialog:**

```
┌─────────────────────────────────────────────────────────────────┐
│ Add Permission                                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Action URN *                                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ direct:*:*:view                                         │   │
│ └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│ 💡 Wildcard Tips                                               │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ Use * to match any segment:                             │   │
│ │ • *:*:*:*          = All permissions                    │   │
│ │ • direct:*:*:view  = View all in direct services        │   │
│ │ • *:*:profile:*    = All actions on profiles            │   │
│ │                                                           │   │
│ │ Current pattern matches:                                 │   │
│ │ ✓ direct:client-portal:profile:view                     │   │
│ │ ✓ direct:client-portal:account:view                     │   │
│ │ ✓ direct:indirect-portal:client:view                    │   │
│ │ ... and 47 more actions                                 │   │
│ └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│ ⚠ Warning: This wildcard grants broad permissions              │
│                                                                 │
│                                      [Cancel] [Add Permission] │
└─────────────────────────────────────────────────────────────────┘
```

**Wildcard Permission Display:**

In permission list, show expandable details:

```
┌────────────────────────────────────────────────────────────┐
│ [USER] direct:*:*:view                                     │
│ 🟢 All Accounts                                            │
│ 🔶 Wildcard (matches 23 actions) [Show ▼]                 │
│ Granted: 2025-12-15 10:30 by admin@example.com           │
│ [Edit] [Revoke]                                           │
└────────────────────────────────────────────────────────────┘

[When expanded:]
┌────────────────────────────────────────────────────────────┐
│ [USER] direct:*:*:view                                     │
│ 🟢 All Accounts                                            │
│ 🔶 Wildcard - Matches these actions:                      │
│   ✓ direct:client-portal:profile:view                    │
│   ✓ direct:client-portal:account:view                    │
│   ✓ direct:client-portal:user:view                       │
│   ... and 20 more [View All]                             │
│ Granted: 2025-12-15 10:30 by admin@example.com           │
│ [Edit] [Revoke]                                           │
└────────────────────────────────────────────────────────────┘
```

**Wildcard Validation Feedback:**

```
Action URN: prof*:view:*:*
            ^^^^
❌ Error: Partial wildcards not supported. Use * to match entire segment.
Did you mean: *:*:profile:view?
```
