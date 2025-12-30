# US-UG-004: Assign Permissions to Group

## Story

**As a** Profile Administrator
**I want** to assign permissions to a user group using the same UI as user permissions
**So that** all group members automatically inherit those permissions

## Acceptance Criteria

- [ ] Permission assignment UI matches individual user permission assignment
- [ ] All available permission types are selectable (client, account, role-based)
- [ ] Multiple permissions can be assigned at once
- [ ] Assigned permissions display in group detail view
- [ ] Permission count shown in group summary
- [ ] All group members immediately inherit new permissions
- [ ] Duplicate permission assignments are prevented
- [ ] Success message shows count of permissions added
- [ ] Audit log records permission assignments with timestamp and actor

## Technical Notes

**Database Changes:**
- Create `user_group_permissions` table:
  - `id` (UUID, primary key)
  - `user_group_id` (UUID, foreign key to user_groups)
  - `permission_type` (VARCHAR(50): CLIENT_ACCESS, ACCOUNT_ACCESS, ROLE_BASED)
  - `client_id` (UUID, nullable, foreign key to clients)
  - `account_id` (UUID, nullable, foreign key to accounts)
  - `role_name` (VARCHAR(100), nullable)
  - `granted_at` (TIMESTAMP)
  - `granted_by` (VARCHAR(255))
- Add unique constraint on (user_group_id, permission_type, client_id, account_id, role_name)
- Add indexes for query performance

**API Changes:**
- POST `/api/profiles/{profileId}/user-groups/{groupId}/permissions`
  - Request body: `{ "permissions": [{ "type": "CLIENT_ACCESS", "clientId": "uuid" }, ...] }`
  - Response: `{ "added": 3, "skipped": 1, "permissions": [PermissionDto...] }`
  - Returns 200 OK with summary
  - Returns 403 Forbidden if user lacks MANAGE_PERMISSIONS permission
  - Returns 404 Not Found if group doesn't exist

- GET `/api/profiles/{profileId}/user-groups/{groupId}/permissions`
  - Returns list of all permissions assigned to group
  - Includes permission details (client name, account name, etc.)

**Domain Model:**
- Add `assignPermission(Permission)` method to `UserGroup` aggregate
- Add `assignPermissions(List<Permission>)` for bulk assignment
- Emit `PermissionGrantedToGroup` domain event
- Domain event triggers permission cache refresh for all group members

**Permission Inheritance:**
- Update permission evaluation service to check user groups
- Cache effective permissions per user
- Invalidate cache when group permissions change

## Dependencies

- US-UG-001: Create User Group must be completed
- US-UG-002: Add Users to Group must be completed
- Existing user permission system must be in place

## Test Cases

1. **Assign Client Access Permission**
   - Given a group "Sales Team" with 5 members
   - When admin assigns CLIENT_ACCESS permission for "Acme Corp"
   - Then permission is added to group
   - And all 5 members can access Acme Corp
   - And success message displays

2. **Assign Multiple Permissions at Once**
   - Given a group "Accounting"
   - When admin assigns 3 different client access permissions
   - Then all 3 permissions are added
   - And success message shows "3 permissions added"

3. **Assign Role-Based Permission**
   - Given a group "Managers"
   - When admin assigns ROLE_BASED permission with role "MANAGER"
   - Then all group members inherit MANAGER role permissions
   - And can perform manager-level operations

4. **Prevent Duplicate Permission**
   - Given group "Engineering" already has CLIENT_ACCESS for "TechCo"
   - When admin tries to assign same permission again
   - Then operation succeeds with warning
   - And message shows "1 permission already assigned"

5. **New Member Inherits Existing Permissions**
   - Given group "Sales" has 3 permissions assigned
   - When new user is added to group
   - Then new user immediately has all 3 permissions
   - And can access resources according to those permissions

6. **Permission UI Consistency**
   - Given admin is on group permission assignment page
   - Then UI elements match user permission assignment UI
   - And same permission types are available
   - And same selection controls are used

7. **Unauthorized Access**
   - Given user without MANAGE_PERMISSIONS permission
   - When they attempt to assign permissions to group
   - Then they receive 403 Forbidden error

## UI/UX (if applicable)

**Permission Assignment Interface:**
- "Assign Permissions" button on group detail page
- Opens modal or dedicated section
- Permission type selector (Client Access, Account Access, Role-Based)
- Conditional inputs based on permission type:
  - Client Access: Client picker dropdown
  - Account Access: Account picker dropdown
  - Role-Based: Role name dropdown
- "Add Permission" button to add to list
- List of permissions to be assigned with remove option
- "Save Permissions" primary button
- "Cancel" secondary button

**Group Permissions Display:**
- Table showing assigned permissions
- Columns: Type, Resource, Granted By, Granted At
- Remove button for each permission
- Permission count badge in group header
