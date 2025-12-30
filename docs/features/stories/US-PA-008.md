# US-PA-008: Manage Permissions UI

## Story

**As a** security administrator
**I want** a user-friendly interface to manage user permissions
**So that** I can efficiently grant, revoke, and review access rights

## Acceptance Criteria

- [ ] UI to view all user permissions (role-based and user-specific)
- [ ] UI to grant individual permissions to users
- [ ] UI to revoke user-specific permissions
- [ ] UI to set account scope (All Accounts or Specific Accounts)
- [ ] Visual distinction between role-inherited and user-specific permissions
- [ ] Search and filter permissions by action, source, scope
- [ ] Show permission effective date and who granted it
- [ ] Prevent editing role-inherited permissions (must edit role)
- [ ] Confirm before revoking permissions
- [ ] Real-time validation of action URNs and account IDs

## Acceptance Criteria (continued)

- [ ] Responsive design for desktop and tablet
- [ ] Accessible (WCAG 2.1 AA compliance)
- [ ] Loading states and error handling
- [ ] Audit log viewer for permission changes

## Technical Notes

**Page Structure:**
```
User Permission Management
├── User Info Header
│   ├── User name, email, ID
│   └── Active/Inactive status
├── Role Assignments Section
│   ├── Assigned roles (badges)
│   └── Add/Remove role buttons
├── Permissions Table
│   ├── Filter/Search bar
│   ├── Group by source toggle
│   └── Permission rows
└── Add Permission Dialog
    ├── Action URN input
    ├── Scope selector
    ├── Account multi-select
    └── Save/Cancel buttons
```

**Permission Table Columns:**
- Source (Role badge or "User" badge)
- Action URN
- Scope (All Accounts or N accounts)
- Granted By
- Granted At
- Actions (Edit, Revoke)

**UI Components:**

1. **Permission Row:**
```tsx
interface PermissionRowProps {
  permission: UserPermission;
  source: 'ROLE' | 'USER';
  roleName?: string;
  onEdit?: () => void;
  onRevoke?: () => void;
}

// Example:
┌─────────────────────────────────────────────────────────────────────┐
│ [ROLE: VIEWER] direct:client-portal:profile:view                    │
│ Scope: All Accounts                                                 │
│ Granted: 2025-12-01 by system                                      │
│ [Inherited - Edit Role]                                            │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ [USER] direct:client-portal:account:update                          │
│ Scope: 3 accounts ▼                                                │
│   - Acme Corp (client-001)                                         │
│   - TechStart (client-002)                                         │
│   - Global Inc (client-003)                                        │
│ Granted: 2025-12-15 10:30 by admin@example.com                    │
│ [Edit] [Revoke]                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

2. **Add Permission Dialog:**
```
┌─────────────────────────────────────────────────────────────────┐
│ Add Permission                                           [×]    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ Action URN *                                                    │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ direct:client-portal:profile:view                       │   │
│ └─────────────────────────────────────────────────────────┘   │
│ Common actions: [View Profiles] [Create Accounts] [Approve]   │
│                                                                 │
│ Scope *                                                         │
│ ⚬ All Accounts                                                 │
│ ⚪ Specific Accounts                                            │
│                                                                 │
│ [When Specific Accounts selected:]                             │
│                                                                 │
│ Select Accounts                                                 │
│ ┌─────────────────────────────────────────────────────────┐   │
│ │ Search accounts...                              [×]     │   │
│ ├─────────────────────────────────────────────────────────┤   │
│ │ ☐ Clients (15)                                          │   │
│ │   ☐ Acme Corp (client-001)                             │   │
│ │   ☐ TechStart (client-002)                             │   │
│ │ ☐ Profiles (23)                                         │   │
│ │   ☐ Profile A (profile-001)                            │   │
│ │   ☐ Profile B (profile-002)                            │   │
│ └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│ Selected: [Acme Corp ×] [Profile A ×]                          │
│                                                                 │
│                                      [Cancel] [Add Permission] │
└─────────────────────────────────────────────────────────────────┘
```

3. **Filters and Search:**
```
┌─────────────────────────────────────────────────────────────────┐
│ [🔍 Search permissions...]                                      │
│                                                                 │
│ Source: [All ▼] [USER] [ROLE]                                  │
│ Scope: [All ▼] [All Accounts] [Specific Accounts]              │
│ Action Type: [All ▼] [VIEW] [CREATE] [UPDATE] [DELETE]         │
│                                                                 │
│ [Group by Source] ☑                                            │
└─────────────────────────────────────────────────────────────────┘
```

**Color Coding:**
- Role permissions: Blue background
- User permissions: Orange background
- All Accounts scope: Green badge
- Specific Accounts: Yellow badge
- Revoked: Gray with strikethrough

**Frontend API Integration:**
```typescript
// API Client
class PermissionService {
  async getUserPermissions(userId: string): Promise<UserPermission[]> {
    const response = await fetch(`/api/users/${userId}/permissions`);
    return response.json();
  }

  async grantPermission(
    userId: string,
    permission: PermissionRequest
  ): Promise<UserPermission> {
    const response = await fetch(`/api/users/${userId}/permissions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(permission)
    });
    return response.json();
  }

  async revokePermission(
    userId: string,
    permissionId: string
  ): Promise<void> {
    await fetch(`/api/users/${userId}/permissions/${permissionId}`, {
      method: 'DELETE'
    });
  }

  async updatePermission(
    userId: string,
    permissionId: string,
    update: PermissionUpdate
  ): Promise<UserPermission> {
    const response = await fetch(
      `/api/users/${userId}/permissions/${permissionId}`,
      {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(update)
      }
    );
    return response.json();
  }

  async getAllowedAccounts(
    userId: string,
    action: string
  ): Promise<AllowedAccountsResponse> {
    const response = await fetch(
      `/api/permissions/allowed-accounts?action=${action}`
    );
    return response.json();
  }
}
```

**Validation:**
- Action URN format validation with immediate feedback
- Account ID existence validation
- Duplicate permission check
- Required field validation
- Permission grant authorization check

**Error Handling:**
```typescript
try {
  await permissionService.grantPermission(userId, permission);
  showSuccess('Permission granted successfully');
  refreshPermissions();
} catch (error) {
  if (error.status === 409) {
    showError('This permission is already granted to the user');
  } else if (error.status === 403) {
    showError('You do not have permission to grant this access');
  } else if (error.status === 400) {
    showError(`Validation error: ${error.message}`);
  } else {
    showError('Failed to grant permission. Please try again.');
  }
}
```

## Dependencies

- US-PA-001: Define Action URN Structure
- US-PA-002: Role-Based Permission Assignment
- US-PA-003: User-Specific Permission Override
- US-PA-004: Account-Level Permission Scope
- US-PA-006: Get Allowed Accounts API

## Test Cases

**Functional Tests:**

1. **View User Permissions**
   - Given: User with role and user-specific permissions
   - When: Page loads
   - Then: All permissions displayed, grouped by source

2. **Grant Permission with All Accounts**
   - Given: Admin on permission management page
   - When: Add permission with ALL_ACCOUNTS scope
   - Then: Permission saved, appears in list

3. **Grant Permission with Specific Accounts**
   - Given: Admin selects specific accounts
   - When: Save permission
   - Then: Permission saved with account IDs

4. **Revoke User Permission**
   - Given: User has user-specific permission
   - When: Click revoke, confirm dialog
   - Then: Permission revoked, removed from list

5. **Cannot Edit Role Permission**
   - Given: Permission from role
   - When: Try to edit
   - Then: Edit disabled, link to edit role shown

6. **Validate Action URN**
   - Given: Invalid URN format entered
   - When: Try to save
   - Then: Validation error shown

7. **Search Permissions**
   - Given: User has multiple permissions
   - When: Search for "profile"
   - Then: Only permissions with "profile" shown

8. **Filter by Source**
   - Given: Mixed role and user permissions
   - When: Filter by USER
   - Then: Only user-specific permissions shown

9. **Expand Account List**
   - Given: Permission with specific accounts
   - When: Click expand icon
   - Then: Full account list shown

10. **Prevent Duplicate Permission**
    - Given: User already has permission
    - When: Try to grant same permission
    - Then: Error message shown

**Accessibility Tests:**

11. **Keyboard Navigation**
    - Given: User using keyboard only
    - When: Tab through interface
    - Then: All interactive elements accessible

12. **Screen Reader Support**
    - Given: Screen reader active
    - When: Navigate permissions
    - Then: Proper labels and descriptions read

**Performance Tests:**

13. **Large Permission List**
    - Given: User with 100+ permissions
    - When: Page loads
    - Then: Loads in < 2 seconds, pagination works

14. **Account Selection Performance**
    - Given: 1000+ accounts available
    - When: Open account selector
    - Then: Search and filter responsive

## UI/UX (if applicable)

**Mockup: Permission Management Page**

```
┌────────────────────────────────────────────────────────────────────┐
│ ← Back to Users                                     [Settings ⚙]   │
├────────────────────────────────────────────────────────────────────┤
│                                                                    │
│  👤 John Doe                                                       │
│     john.doe@example.com · ID: user-123                           │
│     Status: 🟢 Active                                             │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ Roles                                                        │ │
│  │ [VIEWER] [CREATOR] [+ Add Role]                             │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │ Permissions                                [+ Add Permission]│ │
│  ├──────────────────────────────────────────────────────────────┤ │
│  │ [🔍 Search] [Source ▼] [Scope ▼] [Action Type ▼]           │ │
│  │ ☑ Group by source                                           │ │
│  ├──────────────────────────────────────────────────────────────┤ │
│  │                                                              │ │
│  │ Role Permissions (5)                                        │ │
│  │ ┌────────────────────────────────────────────────────────┐ │ │
│  │ │ [VIEWER] direct:client-portal:profile:view             │ │ │
│  │ │ 🟢 All Accounts                                        │ │ │
│  │ │ Granted: 2025-12-01 by system                         │ │ │
│  │ │ [View Role →]                                          │ │ │
│  │ └────────────────────────────────────────────────────────┘ │ │
│  │ ┌────────────────────────────────────────────────────────┐ │ │
│  │ │ [VIEWER] indirect:indirect-portal:client:view          │ │ │
│  │ │ 🟢 All Accounts                                        │ │ │
│  │ │ Granted: 2025-12-01 by system                         │ │ │
│  │ │ [View Role →]                                          │ │ │
│  │ └────────────────────────────────────────────────────────┘ │ │
│  │                                                              │ │
│  │ User Permissions (2)                                        │ │
│  │ ┌────────────────────────────────────────────────────────┐ │ │
│  │ │ [USER] direct:client-portal:account:update             │ │ │
│  │ │ 🟡 3 accounts ▼                                        │ │ │
│  │ │ Granted: 2025-12-15 10:30 by admin@example.com       │ │ │
│  │ │ [Edit ✏] [Revoke 🗑]                                  │ │ │
│  │ └────────────────────────────────────────────────────────┘ │ │
│  │                                                              │ │
│  │ Showing 7 of 7 permissions                                  │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
└────────────────────────────────────────────────────────────────────┘
```

**Interaction States:**
- Hover: Highlight row with light gray background
- Selected: Blue border around row
- Loading: Skeleton loaders for permissions
- Empty state: "No permissions found" with illustration
- Error state: Error banner with retry button
