# US-ICP-011: Manage User Groups

## Story

**As an** Indirect Client Administrator
**I want** to organize users into groups and assign permissions at the group level
**So that** I can efficiently manage permissions for teams without configuring each user individually

## Acceptance Criteria

- [ ] Groups page accessible from main navigation
- [ ] Grid displays all groups with name, description, member count, creation date
- [ ] "Create Group" button opens group creation form
- [ ] Group creation form includes: name, description, members selection
- [ ] Group name must be unique within the organization
- [ ] Users can be added to multiple groups
- [ ] Edit group allows changing name, description, members, and permissions
- [ ] Delete group requires confirmation and does not delete users
- [ ] Permissions can be assigned to groups
- [ ] Users inherit permissions from all their groups
- [ ] Group detail page shows members and permissions
- [ ] Add/remove members from group detail page
- [ ] Search and filter groups by name
- [ ] Audit log records all group changes

## Technical Notes

**API Endpoints:**
- `GET /api/indirect/groups` - List all groups
  - Query params: `page`, `size`, `search`
  - Response: `Page<GroupSummaryDTO>`
- `POST /api/indirect/groups` - Create new group
  - Request: `CreateGroupRequest { name, description, memberIds[], permissions[] }`
  - Response: `GroupDTO`
- `GET /api/indirect/groups/{groupId}` - Get group details
  - Response: `GroupDetailDTO`
- `PUT /api/indirect/groups/{groupId}` - Update group
  - Request: `UpdateGroupRequest { name, description }`
  - Response: `GroupDTO`
- `DELETE /api/indirect/groups/{groupId}` - Delete group
- `POST /api/indirect/groups/{groupId}/members` - Add members to group
  - Request: `{ userIds: UUID[] }`
- `DELETE /api/indirect/groups/{groupId}/members/{userId}` - Remove member
- `POST /api/indirect/groups/{groupId}/permissions` - Assign permission to group
  - Request: `{ permission: string }`
- `DELETE /api/indirect/groups/{groupId}/permissions/{permissionId}` - Remove permission

**Database Schema:**
- `user_groups` table:
  - `id`: UUID
  - `indirect_client_id`: UUID
  - `name`: VARCHAR(100) UNIQUE per indirect_client
  - `description`: TEXT
  - `created_by`: UUID
  - `created_at`: TIMESTAMP
  - `updated_at`: TIMESTAMP

- `user_group_members` table:
  - `group_id`: UUID
  - `user_id`: UUID
  - `added_by`: UUID
  - `added_at`: TIMESTAMP
  - Primary key: (group_id, user_id)

- `user_group_permissions` table:
  - `group_id`: UUID
  - `permission`: VARCHAR(100)
  - `granted_by`: UUID
  - `granted_at`: TIMESTAMP
  - Primary key: (group_id, permission)

**Permission Inheritance:**
- User's effective permissions = Individual permissions + All group permissions
- Group permissions are combined (union, not intersection)
- Individual permission revokes override group grants
- Permission calculation: `(RolePerms + GroupPerms + IndividualGrants) - IndividualRevokes`

**Business Rules:**
- Group names must be unique within organization
- Deleting group does not delete users or affect individual permissions
- Removing user from group immediately revokes group permissions
- Cannot delete group if it's the only source of admin permissions for any user
- Group member count is cached and updated on member add/remove

**Security:**
- Requires `INDIRECT_CLIENT_ADMIN` role or `user.manage_permissions` permission
- Can only manage groups within same indirect client organization
- Cannot assign permissions to group that current user doesn't have
- Audit log all group operations

## Dependencies

- US-ICP-001: View Users (for selecting members)
- US-ICP-004: Manage User Permissions (permission inheritance)
- Permission system

## Test Cases

1. **View Groups**: Navigate to groups page and verify all groups displayed
2. **Create Group**: Create new group with name and description, verify success
3. **Unique Group Name**: Attempt to create group with existing name and verify error
4. **Add Members**: Add users to group and verify member count increases
5. **Remove Member**: Remove user from group and verify member count decreases
6. **Edit Group**: Update group name and description, verify changes saved
7. **Delete Group**: Delete group and verify users remain but group is removed
8. **Assign Permissions**: Add permission to group and verify members inherit it
9. **Remove Permission**: Remove group permission and verify members lose it
10. **Multiple Groups**: Add user to multiple groups and verify permissions combined
11. **Permission Inheritance**: Verify user's effective permissions include group permissions
12. **Group Detail**: View group detail page and verify members and permissions shown
13. **Search Groups**: Search by group name and verify filtering works
14. **Cannot Delete Critical Group**: Attempt to delete group providing only admin access and verify error
15. **Audit Log**: Perform group operations and verify audit entries created
16. **Cross-Organization Security**: Verify cannot manage groups from other indirect clients

## UI/UX

**Groups Page:**
```
User Groups                                           [Create Group]
------------------------------------------------------------------

Search: [____________________] [Search]

Name                 Description              Members    Created
Administrators       Full system access       3          2025-10-15
Finance Team         Access to financial data 8          2025-10-20
Read Only Users      View-only access         15         2025-11-01
Customer Support     Customer service team    12         2025-11-10

Showing 1-4 of 4 groups
```

**Create Group Dialog:**
```
Create New Group
----------------

Group Name *
[____________________]

Description
[_____________________________________________]
[_____________________________________________]

Add Members (optional)
Search: [____________________]

Available Users                Selected Members
[ ] john.smith@example.com     [x] admin@example.com      [Remove]
[ ] jane.doe@example.com       [x] admin2@example.com     [Remove]
[ ] bob.jones@example.com
[ ] alice.wilson@example.com

0 selected                      2 selected

[Cancel]  [Create Group]
```

**Group Detail Page:**
```
Groups > Administrators                               [Back to Groups]
------------------------------------------------------------------

Administrators                                        [Edit]  [Delete]
Full system access

Created: 2025-10-15 by admin@example.com
Last Updated: 2025-12-20

------------------------------------------------------------------
[Members] [Permissions]

Members (3)
-----------
Name                 Email                      Role           Added
John Smith          john.smith@example.com     Admin          2025-10-15  [Remove]
Jane Doe            jane.doe@example.com       Admin          2025-11-01  [Remove]
Bob Jones           bob.jones@example.com      User Manager   2025-12-15  [Remove]

[Add Members]

Group Permissions (12)
----------------------
✓ user.read
✓ user.write
✓ user.delete
✓ user.manage_permissions
✓ profile.read
✓ profile.write
✓ profile.delete
✓ account.read
✓ account.write
✓ reports.read
✓ reports.export
✓ settings.manage

[Manage Permissions]
```

**Edit Group Dialog:**
```
Edit Group
----------

Group Name *
[Administrators              ]

Description
[Full system access                              ]
[                                                ]

[Cancel]  [Save Changes]
```

**Delete Group Confirmation:**
```
Delete Group?
-------------

Are you sure you want to delete this group?

Group: Administrators
Members: 3 users

When you delete this group:
- The group will be permanently removed
- Members will remain in the system
- Members will lose permissions granted by this group
- This action cannot be undone

[Cancel]  [Delete Group]
```

**Add Members Dialog:**
```
Add Members to "Administrators"
-------------------------------

Search: [____________________] [Search]

Available Users
[ ] john.smith@example.com     - Admin
[ ] jane.doe@example.com       - User Manager
[ ] bob.jones@example.com      - Viewer
[ ] alice.wilson@example.com   - Accountant
[ ] charlie.brown@example.com  - User

Selected: 0 users

[Cancel]  [Add Selected Members]
```

**Manage Group Permissions Dialog:**
```
Manage Permissions - Administrators
-----------------------------------

Current Permissions (12)                    Available Permissions
✓ user.read                [Remove]         [ ] audit.read         [Add]
✓ user.write               [Remove]         [ ] audit.export       [Add]
✓ user.delete              [Remove]         [ ] settings.advanced  [Add]
✓ user.manage_permissions  [Remove]
✓ profile.read             [Remove]
✓ profile.write            [Remove]
✓ profile.delete           [Remove]
✓ account.read             [Remove]
✓ account.write            [Remove]
✓ reports.read             [Remove]
✓ reports.export           [Remove]
✓ settings.manage          [Remove]

[Cancel]  [Save Changes]
```

**Permission Inheritance Display (User Detail Page):**
```
Effective Permissions
---------------------

Source                 Permissions
Individual             user.read, profile.read (2)
Group: Administrators  user.write, user.delete, reports.read (10)
Group: Finance Team    reports.export, account.read (5)
Role: User Manager     user.read, user.write (8)

Total: 15 unique permissions
[View Details]
```

**Error Messages:**
- Duplicate name: "A group with this name already exists."
- Empty name: "Group name is required."
- Cannot delete: "Cannot delete this group. It provides the only admin access for {X} users. Please assign admin permissions through another source first."
- Insufficient permissions: "You cannot assign permissions that you don't have."

**Success Messages:**
- Create: "Group '{name}' created successfully with {X} members."
- Update: "Group '{name}' updated successfully."
- Delete: "Group '{name}' deleted successfully. {X} members remain in the system."
- Add members: "{X} members added to '{group name}'."
- Remove member: "{user name} removed from '{group name}'."
- Permissions: "Permissions updated for group '{name}'. Changes will affect {X} members."
