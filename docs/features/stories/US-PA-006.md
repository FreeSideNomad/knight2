# US-PA-006: Get Allowed Accounts API

## Story

**As a** application developer
**I want** an API to retrieve all accounts a user can access for a given action
**So that** I can filter lists and dropdowns to show only accessible accounts

## Acceptance Criteria

- [ ] GET /api/permissions/allowed-accounts endpoint exists
- [ ] Endpoint accepts action URN as query parameter
- [ ] Returns list of account IDs the user can access
- [ ] If user has ALL_ACCOUNTS scope, returns appropriate indicator
- [ ] Response includes account metadata (ID, name, type)
- [ ] Combines permissions from both roles and user-specific permissions
- [ ] Supports pagination for large result sets
- [ ] Response time is optimized with caching
- [ ] Endpoint requires authentication

## Technical Notes

**API Endpoint:**
```
GET /api/permissions/allowed-accounts?action={actionUrn}&page={page}&size={size}
Authorization: Bearer {token}

Response 200 OK:
{
  "action": "direct:client-portal:profile:view",
  "scope": "SPECIFIC_ACCOUNTS",  // or "ALL_ACCOUNTS"
  "accounts": [
    {
      "accountId": "profile-001",
      "accountType": "PROFILE",
      "name": "Acme Corp Profile",
      "metadata": {
        "status": "ACTIVE",
        "createdAt": "2025-01-01T00:00:00Z"
      }
    },
    {
      "accountId": "profile-002",
      "accountType": "PROFILE",
      "name": "TechStart Profile",
      "metadata": { ... }
    }
  ],
  "pagination": {
    "page": 0,
    "size": 20,
    "totalElements": 45,
    "totalPages": 3
  }
}

Response 200 OK (All Accounts):
{
  "action": "direct:client-portal:profile:view",
  "scope": "ALL_ACCOUNTS",
  "accounts": null,  // null indicates all accounts accessible
  "message": "User has access to all accounts for this action"
}
```

**Implementation Algorithm:**
```java
public AllowedAccountsResponse getAllowedAccounts(
    UserId userId,
    Action action,
    Pageable pageable
) {
    // 1. Get all permissions matching the action (user + role)
    Set<Permission> matchingPermissions = getMatchingPermissions(userId, action);

    // 2. Check if any permission has ALL_ACCOUNTS scope
    boolean hasAllAccountsScope = matchingPermissions.stream()
        .anyMatch(p -> p.getScope() == PermissionScope.ALL_ACCOUNTS);

    if (hasAllAccountsScope) {
        return AllowedAccountsResponse.allAccounts(action);
    }

    // 3. Collect all specific account IDs
    Set<AccountId> accountIds = matchingPermissions.stream()
        .flatMap(p -> p.getAccountIds().stream())
        .collect(Collectors.toSet());

    // 4. Fetch account details with pagination
    Page<AccountInfo> accounts = accountService.getAccountInfo(
        accountIds,
        pageable
    );

    return AllowedAccountsResponse.specificAccounts(action, accounts);
}
```

**Account Info Resolution:**
```java
public interface AccountService {
    Page<AccountInfo> getAccountInfo(Set<AccountId> accountIds, Pageable pageable);
}

public record AccountInfo(
    AccountId accountId,
    AccountType accountType,
    String name,
    Map<String, Object> metadata
) {}

public enum AccountType {
    CLIENT,
    INDIRECT_CLIENT,
    PROFILE,
    INDIRECT_PROFILE,
    BANK
}
```

**Performance Optimizations:**
- Cache allowed accounts per user+action with 5-minute TTL
- Use database joins to fetch account details efficiently
- Support filtering by account type
- Return IDs only option for performance (skip metadata fetch)

**Enhanced Query Parameters:**
```
GET /api/permissions/allowed-accounts
  ?action={urn}
  &accountType={CLIENT|PROFILE|etc}  // optional filter
  &idsOnly={true|false}              // return IDs only
  &page={page}
  &size={size}
  &sort={field,direction}
```

**IDs Only Response:**
```json
{
  "action": "direct:client-portal:profile:view",
  "scope": "SPECIFIC_ACCOUNTS",
  "accountIds": ["profile-001", "profile-002", "profile-003"],
  "total": 45
}
```

## Dependencies

- US-PA-001: Define Action URN Structure
- US-PA-002: Role-Based Permission Assignment
- US-PA-003: User-Specific Permission Override
- US-PA-004: Account-Level Permission Scope
- US-PA-005: Permission Check API

## Test Cases

1. **Get Allowed Accounts - Specific Scope**
   - Given: User has permission for 3 specific profiles
   - When: GET /api/permissions/allowed-accounts?action=direct:client-portal:profile:view
   - Then: Returns 3 accounts with details

2. **Get Allowed Accounts - All Accounts Scope**
   - Given: User has ALL_ACCOUNTS permission
   - When: GET /api/permissions/allowed-accounts?action=direct:client-portal:profile:view
   - Then: Returns scope=ALL_ACCOUNTS, accounts=null

3. **Combine Multiple Permissions**
   - Given: User has role with 2 accounts, user permission with 3 accounts
   - When: GET /api/permissions/allowed-accounts
   - Then: Returns union of 5 unique accounts

4. **Filter by Account Type**
   - Given: User has access to clients and profiles
   - When: GET with accountType=PROFILE
   - Then: Returns only profile accounts

5. **Pagination**
   - Given: User has access to 100 accounts
   - When: GET with page=0&size=20
   - Then: Returns first 20 accounts with pagination metadata

6. **IDs Only Mode**
   - Given: User has access to 50 accounts
   - When: GET with idsOnly=true
   - Then: Returns array of IDs without metadata (faster)

7. **No Matching Permission**
   - Given: User has no permission for action
   - When: GET /api/permissions/allowed-accounts
   - Then: Returns empty accounts array

8. **Wildcard Action Matching**
   - Given: User has permission "direct:client-portal:*:view"
   - When: GET with action=direct:client-portal:profile:view
   - Then: Returns allowed accounts

9. **Cache Performance**
   - Given: Same query repeated
   - When: Second request made within 5 minutes
   - Then: Response from cache, < 10ms response time

10. **Account Details Include Metadata**
    - Given: User has access to accounts
    - When: GET /api/permissions/allowed-accounts
    - Then: Each account includes name, type, and metadata

## UI/UX (if applicable)

**Usage in UI Components:**

1. **Filtered Dropdown:**
```javascript
async function loadAccessibleProfiles() {
  const response = await fetch(
    '/api/permissions/allowed-accounts?action=direct:client-portal:profile:view&accountType=PROFILE'
  );
  const data = await response.json();

  if (data.scope === 'ALL_ACCOUNTS') {
    // Load all profiles from regular API
    return await loadAllProfiles();
  } else {
    // Use filtered list
    return data.accounts;
  }
}
```

2. **Autocomplete Search:**
```javascript
async function searchAccessibleAccounts(searchTerm) {
  const response = await fetch(
    `/api/permissions/allowed-accounts?action=direct:client-portal:profile:view&search=${searchTerm}`
  );
  const data = await response.json();
  return data.accounts;
}
```

3. **List View Filter:**
```javascript
// Apply server-side filtering
async function loadProfileList(page) {
  const allowedAccountsResponse = await fetch(
    '/api/permissions/allowed-accounts?action=direct:client-portal:profile:view&idsOnly=true'
  );
  const { scope, accountIds } = await allowedAccountsResponse.json();

  if (scope === 'ALL_ACCOUNTS') {
    return loadAllProfiles(page);
  } else {
    return loadProfilesByIds(accountIds, page);
  }
}
```

**Admin UI - Account Access Viewer:**
- Shows user's accessible accounts per action
- Tree view grouped by account type
- Search/filter capability
- Export to CSV option
- Visual indicator for ALL_ACCOUNTS scope
