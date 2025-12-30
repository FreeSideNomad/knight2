# US-ICP-004: Manage User Permissions

## Story

**As an** Indirect Client Administrator
**I want** to assign and manage user roles and permissions
**So that** I can control what each user can access and do in the portal

## Acceptance Criteria

- [ ] Permissions page shows current roles and permissions for selected user
- [ ] Available roles are displayed with descriptions
- [ ] Admin can assign multiple roles to a user
- [ ] Admin can revoke roles from a user
- [ ] Individual permissions can be granted beyond role defaults
- [ ] Individual permissions can be revoked even if granted by role
- [ ] Permissions are scoped to the indirect client organization
- [ ] Changes take effect immediately
- [ ] User sees permission changes reflected in their session (within 5 minutes or on next login)
- [ ] Audit log records all permission changes
- [ ] Cannot remove all admin roles if user is the last admin

## Technical Notes

**API Endpoints:**
- `GET /api/indirect/users/{userId}/permissions` - Get user's roles and permissions
  - Response: `{ roles: RoleDTO[], permissions: PermissionDTO[], effectivePermissions: string[] }`
- `POST /api/indirect/users/{userId}/roles` - Assign role to user
  - Request: `{ roleId: string }`
- `DELETE /api/indirect/users/{userId}/roles/{roleId}` - Remove role from user
- `POST /api/indirect/users/{userId}/permissions` - Grant individual permission
  - Request: `{ permission: string, scope: string }`
- `DELETE /api/indirect/users/{userId}/permissions/{permissionId}` - Revoke permission

**Database:**
- `user_roles` table: Maps users to roles
- `role_permissions` table: Maps roles to default permissions
- `user_permissions` table: Individual permission grants/revokes
- `permissions` table: Available permissions catalog

**Permission Model:**
- Roles provide base permissions
- Individual grants add permissions
- Individual revokes override role permissions
- Effective permissions = (Role permissions + Grants) - Revokes
- All permissions scoped to `indirect_client_id`

**Permissions Catalog:**
- `user.read` - View users
- `user.write` - Create/edit users
- `user.delete` - Delete users
- `user.manage_permissions` - Manage user permissions
- `profile.read` - View indirect profiles
- `profile.write` - Create/edit indirect profiles
- `account.read` - View account information
- `account.write` - Modify account settings
- `reports.read` - View reports
- `reports.export` - Export reports

**Security:**
- Requires `INDIRECT_CLIENT_ADMIN` role or `user.manage_permissions` permission
- Cannot grant permissions higher than current user has
- Cannot modify permissions for users in other organizations
- Session cache invalidation on permission change

## Dependencies

- US-ICP-001: View Users
- US-ICP-010: User Detail Page
- Role and permission system
- Session management for cache invalidation

## Test Cases

1. **View Current Permissions**: Open permissions page and see user's current roles and permissions
2. **Assign Role**: Add "User Manager" role and verify permissions granted
3. **Remove Role**: Remove role and verify associated permissions revoked
4. **Grant Individual Permission**: Grant specific permission not in user's roles
5. **Revoke Individual Permission**: Revoke permission granted by role
6. **Multiple Roles**: Assign multiple roles and verify combined permissions
7. **Effective Permissions**: Verify effective permissions correctly combine roles, grants, and revokes
8. **Last Admin Protection**: Attempt to remove admin role from last admin and verify error
9. **Immediate Effect**: Change permissions and verify user's access changes within 5 minutes
10. **Audit Log**: Make permission change and verify audit log entry
11. **Cross-Organization Security**: Verify cannot modify permissions for users in other organizations
12. **Insufficient Permissions**: Non-admin attempts to change permissions and verify access denied

## UI/UX

**Permissions Management Page:**
```
Manage Permissions - John Smith (jsmith@example.com)
----------------------------------------------------

Current Roles
-------------
[x] Admin                    [Remove]
    Full access to all features

[x] User Manager             [Remove]
    Can create and manage users

[ ] Viewer
    Read-only access

[ ] Accountant
    Access to financial reports

[+ Add Role]

Individual Permissions
---------------------
Permission              Source          Action
user.read              Role: Admin      -
user.write             Role: Admin      -
user.delete            Granted          [Revoke]
profile.read           Role: Admin      -
profile.write          Revoked          [Grant]
reports.export         Role: Accountant -

[+ Grant Permission]

Effective Permissions: 15 permissions
[View All Permissions]

[Cancel]  [Save Changes]
```

**Permission Change Confirmation:**
- Show diff of permission changes before saving
- Warn if removing critical permissions

**Success Message:**
"Permissions updated successfully for {user name}"

**Audit Log Entry Format:**
```
Action: Permission Changed
User: jsmith
Changed By: admin@example.com
Changes:
  + Added role: User Manager
  - Removed role: Viewer
  + Granted permission: user.delete
  - Revoked permission: profile.write
Timestamp: 2025-12-30 10:30:00 UTC
```
