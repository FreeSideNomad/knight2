# US-AG-009: Dynamic Group Membership

## Story

**As a** service profile administrator
**I want** user permissions to automatically reflect current account group membership
**So that** I don't need to manually update permissions when accounts are added or removed from groups

## Acceptance Criteria

- [ ] When account is added to group, users with permissions to that group immediately gain access
- [ ] When account is removed from group, users with permissions to that group immediately lose access
- [ ] No manual permission updates required when group membership changes
- [ ] Authorization checks always use current group membership, not cached values
- [ ] Audit log records show when access was granted/revoked via group membership changes
- [ ] Permission history shows timeline of effective account access based on group changes
- [ ] Performance remains acceptable even with frequent group membership changes

## Technical Notes

### Authorization Strategy
- **Resolution at Authorization Time**: Resolve group membership during each authorization check
- **Cache with Invalidation**: Cache group membership but invalidate on changes
- **Event-Driven Updates**: Publish events when group membership changes

**Recommended**: Combination of caching with event-driven invalidation

### Domain Events
```java
// Published when accounts are added to group
AccountsAddedToGroupEvent {
  AccountGroupId groupId;
  List<ClientId> accountIds;
  Instant occurredAt;
}

// Published when accounts are removed from group
AccountsRemovedFromGroupEvent {
  AccountGroupId groupId;
  List<ClientId> accountIds;
  Instant occurredAt;
}
```

### Event Handlers
- `PermissionCacheInvalidationHandler`: Invalidates permission cache for affected users
- `AuditLogHandler`: Records access changes in audit log
- `NotificationHandler`: (Optional) Notify affected users of access changes

### Caching Strategy
```java
// Cache key structure
cache.put("user:{userId}:accounts", Set<ClientId>)
cache.put("group:{groupId}:accounts", Set<ClientId>)

// Invalidation on group membership change
onAccountsAddedToGroup(event) {
  cache.invalidate("group:{groupId}:accounts")
  // Find all users with permissions to this group
  affectedUsers = findUsersWithGroupPermission(groupId)
  affectedUsers.forEach(user ->
    cache.invalidate("user:{userId}:accounts")
  )
}
```

### Authorization Service
```java
boolean hasAccessToAccount(UserId userId, ClientId accountId) {
  // Get user's account access (cached)
  Set<ClientId> accessibleAccounts =
    cache.get("user:{userId}:accounts", () -> {
      return resolveUserAccountAccess(userId);
    });

  return accessibleAccounts.contains(accountId);
}

Set<ClientId> resolveUserAccountAccess(UserId userId) {
  Set<ClientId> accounts = new HashSet<>();

  // Get individual account permissions
  accounts.addAll(getIndividualAccountPermissions(userId));

  // Get account group permissions and resolve members
  List<AccountGroupId> userGroups = getUserAccountGroups(userId);
  userGroups.forEach(groupId -> {
    accounts.addAll(getGroupAccounts(groupId));
  });

  return accounts;
}
```

### Audit Trail
- Record effective access changes:
  - "User [X] granted access to Account [Y] via group [Z] membership"
  - "User [X] revoked access to Account [Y] via removal from group [Z]"
- Link audit entries to source event (group membership change)

## Dependencies

- US-AG-002: Add Accounts to Group (triggers access grants)
- US-AG-003: Remove Accounts from Group (triggers access revocations)
- US-AG-007: Use Account Group in Permissions (provides group-based permissions to resolve)

## Test Cases

1. **Access granted when account added to group**
   - User A has permission to Group 1 (containing Accounts X, Y)
   - User A cannot access Account Z
   - Admin adds Account Z to Group 1
   - User A authenticates
   - Verify User A can now access Account Z without permission changes

2. **Access revoked when account removed from group**
   - User A has permission to Group 1 (containing Accounts X, Y, Z)
   - User A can access Account Z
   - Admin removes Account Z from Group 1
   - User A tries to access Account Z
   - Verify User A can no longer access Account Z

3. **Multiple groups with overlapping membership**
   - Account A is in both Group 1 and Group 2
   - User X has permission to Group 1
   - User Y has permission to Group 2
   - Account A is removed from Group 1
   - Verify User X loses access to Account A
   - Verify User Y still has access to Account A

4. **Cache invalidation on group change**
   - User A's permissions are cached
   - Admin adds Account B to User A's group
   - Verify cache is invalidated
   - Verify User A's next authorization check includes Account B

5. **Audit log records dynamic access changes**
   - Add Account X to Group 1 (used by User A)
   - Query audit log
   - Verify entry shows: "User A granted access to Account X via Group 1 membership change"

6. **Performance with frequent changes**
   - Create group with 100 accounts
   - Assign to 50 users
   - Add 10 accounts to group
   - Measure authorization check response time
   - Verify response time < 100ms

7. **Concurrent access and group changes**
   - User A is accessing Account X via Group 1
   - While access is active, Account X is removed from Group 1
   - User A's next request to Account X
   - Verify access is denied immediately

8. **Permission history timeline**
   - View permission history for User A
   - Verify timeline shows:
     - Date 1: Permission to Group 1 granted (Accounts: X, Y)
     - Date 2: Account Z added to Group 1 (effective accounts: X, Y, Z)
     - Date 3: Account X removed from Group 1 (effective accounts: Y, Z)

## UI/UX

### Real-time Feedback
- Toast notifications for users affected by group changes (optional):
  - "You now have access to X new accounts via [Group Name]"
  - "Your access to X accounts has been removed via [Group Name]"

### Permission History View
- Timeline visualization showing:
  - Permission grants/revocations
  - Group membership changes affecting user
  - Effective account access at each point in time
- Filter: "Show only changes affecting me"

### Admin Dashboard
- Real-time impact preview before confirming group changes:
  - "Adding Account X to Group Y will grant access to 15 users"
  - "Removing Account Z from Group Y will revoke access for 8 users"
  - List of affected users

### Audit Log Enhancement
- Filter audit log by:
  - "Access changes via group membership"
  - Specific account group
  - Specific user
- Export audit report for compliance

### Performance Monitoring
- Admin dashboard showing:
  - Cache hit rate for authorization checks
  - Average authorization check response time
  - Number of cache invalidations per hour
  - Alert if performance degrades

## Performance Considerations

### Optimization Strategies
1. **Batch Cache Invalidation**: Invalidate multiple user caches in single operation
2. **Lazy Loading**: Only resolve group membership when needed
3. **Background Refresh**: Pre-warm cache after group changes
4. **Read Replicas**: Use read replicas for authorization queries
5. **Index Optimization**: Ensure indexes on group membership joins

### Monitoring
- Track cache hit/miss rates
- Monitor query performance for group resolution
- Alert on cache invalidation storms
- Dashboard for authorization check latency
