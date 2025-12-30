# US-PA-004: Account-Level Permission Scope

## Story

**As a** security administrator
**I want** to control whether permissions apply to all accounts or specific accounts
**So that** I can implement fine-grained access control based on business relationships

## Acceptance Criteria

- [ ] Permissions support two scope types: ALL_ACCOUNTS and SPECIFIC_ACCOUNTS
- [ ] ALL_ACCOUNTS scope grants permission for any account in the system
- [ ] SPECIFIC_ACCOUNTS scope requires explicit list of account IDs
- [ ] Scope validation ensures SPECIFIC_ACCOUNTS has at least one account ID
- [ ] Scope validation ensures ALL_ACCOUNTS has no account IDs
- [ ] Account IDs are validated against existing accounts
- [ ] Scope is enforced during permission checks
- [ ] Account scope applies to both role and user permissions

## Technical Notes

**Domain Model:**
```java
public enum PermissionScope {
    ALL_ACCOUNTS,
    SPECIFIC_ACCOUNTS
}

public record Permission(
    Action action,
    PermissionScope scope,
    Set<AccountId> accountIds
) {
    public Permission {
        validateScope(scope, accountIds);
        accountIds = Set.copyOf(accountIds); // immutable
    }

    private void validateScope(PermissionScope scope, Set<AccountId> accountIds) {
        if (scope == PermissionScope.SPECIFIC_ACCOUNTS) {
            if (accountIds == null || accountIds.isEmpty()) {
                throw new IllegalArgumentException(
                    "SPECIFIC_ACCOUNTS scope requires at least one account ID"
                );
            }
        } else if (scope == PermissionScope.ALL_ACCOUNTS) {
            if (accountIds != null && !accountIds.isEmpty()) {
                throw new IllegalArgumentException(
                    "ALL_ACCOUNTS scope cannot have specific account IDs"
                );
            }
        }
    }

    public boolean allowsAccount(AccountId accountId) {
        return scope == PermissionScope.ALL_ACCOUNTS
            || accountIds.contains(accountId);
    }
}
```

**Account ID Types:**
- ClientId (for direct client accounts)
- IndirectClientId (for indirect client accounts)
- ProfileId (for service profiles)
- IndirectProfileId (for indirect service profiles)

All implement AccountId marker interface for polymorphic handling.

**Database Representation:**
- Scope stored as VARCHAR(50) enum value
- Account IDs stored in separate join tables
- Support for multiple account ID types

**Permission Check Logic:**
```java
public boolean isAllowed(UserId userId, Action action, AccountId accountId) {
    // Get all permissions for user (roles + user-specific)
    Set<Permission> permissions = getEffectivePermissions(userId, action);

    // Check if any permission allows the account
    return permissions.stream()
        .anyMatch(p -> p.allowsAccount(accountId));
}
```

**Examples:**

1. View all client profiles:
```json
{
  "action": "direct:client-portal:profile:view",
  "scope": "ALL_ACCOUNTS",
  "accountIds": []
}
```

2. View specific client profiles:
```json
{
  "action": "direct:client-portal:profile:view",
  "scope": "SPECIFIC_ACCOUNTS",
  "accountIds": ["profile-001", "profile-002", "profile-003"]
}
```

3. Approve enrolments for specific banks:
```json
{
  "action": "bank:payor-enrolment:enrolment:approve",
  "scope": "SPECIFIC_ACCOUNTS",
  "accountIds": ["bank-001"]
}
```

## Dependencies

- US-PA-001: Define Action URN Structure

## Test Cases

1. **ALL_ACCOUNTS Allows Any Account**
   - Given: Permission with scope ALL_ACCOUNTS
   - When: allowsAccount() called with any account ID
   - Then: Returns true

2. **SPECIFIC_ACCOUNTS Allows Listed Account**
   - Given: Permission with accountIds ["acc-001", "acc-002"]
   - When: allowsAccount("acc-001") called
   - Then: Returns true

3. **SPECIFIC_ACCOUNTS Denies Unlisted Account**
   - Given: Permission with accountIds ["acc-001", "acc-002"]
   - When: allowsAccount("acc-999") called
   - Then: Returns false

4. **Validate SPECIFIC_ACCOUNTS Has Account IDs**
   - Given: Creating permission with SPECIFIC_ACCOUNTS scope
   - When: accountIds is empty
   - Then: IllegalArgumentException thrown

5. **Validate ALL_ACCOUNTS Has No Account IDs**
   - Given: Creating permission with ALL_ACCOUNTS scope
   - When: accountIds contains values
   - Then: IllegalArgumentException thrown

6. **Support Multiple Account ID Types**
   - Given: Permission with ClientId and ProfileId in accountIds
   - When: allowsAccount() called with ProfileId
   - Then: Returns true

7. **Account ID Validation**
   - Given: Creating permission with non-existent account ID
   - When: Permission is saved
   - Then: Validation error returned

## UI/UX (if applicable)

**Scope Selection Component:**

1. **Radio Button Group:**
   - ( ) All Accounts
   - ( ) Specific Accounts

2. **When "Specific Accounts" Selected:**
   - Show account multi-select dropdown
   - Group accounts by type (Clients, Indirect Clients, Profiles)
   - Search/filter capability
   - Show account name and ID
   - Selected accounts displayed as chips/tags
   - Remove capability for each selected account

3. **Visual Indicators:**
   - Badge showing "All" or "N accounts" next to permission
   - Tooltip on hover showing account list
   - Color coding: All Accounts (blue), Specific Accounts (orange)

4. **Validation Feedback:**
   - Error message if "Specific Accounts" selected with no accounts
   - Warning if "All Accounts" selected for sensitive actions

**Example UI:**
```
Permission Scope
━━━━━━━━━━━━━━━
⚬ All Accounts
⚪ Specific Accounts

[When Specific Accounts selected:]

Select Accounts
━━━━━━━━━━━━━━━
┌─────────────────────────────────┐
│ Search accounts...              │
├─────────────────────────────────┤
│ Clients                         │
│   □ Acme Corp (client-001)      │
│   □ TechStart (client-002)      │
│ Indirect Clients                │
│   □ Global Inc (indirect-001)   │
│ Profiles                        │
│   □ Profile A (profile-001)     │
└─────────────────────────────────┘

Selected: [Acme Corp ×] [Profile A ×]
```
