# US-ICP-001: View Users

## Story

**As an** Indirect Client Administrator
**I want** to view a comprehensive list of all users in my organization
**So that** I can monitor user accounts, their status, and activity

## Acceptance Criteria

- [ ] Grid displays all users with columns: email, name, login ID, status, lock type, last login, roles
- [ ] Users can filter by status (Active, Inactive, Locked)
- [ ] Users can filter by role (Admin, User, Viewer, etc.)
- [ ] Users can search by email, name, or login ID
- [ ] Grid is sortable by all columns
- [ ] Pagination is available for large user lists (20 users per page)
- [ ] Last login shows timestamp in user's timezone
- [ ] Lock type displays: None, CLIENT, BANK, or SECURITY
- [ ] Status indicators use color coding (green for active, red for locked, gray for inactive)
- [ ] Grid refreshes automatically when user data changes

## Technical Notes

**API Endpoints:**
- `GET /api/indirect/users` - Retrieve paginated user list with filters
  - Query params: `page`, `size`, `status`, `role`, `search`
  - Response: `Page<UserSummaryDTO>`

**Database:**
- Query `indirect_client_users` table
- Join with `user_roles` for role information
- Include lock status from `user_locks` table
- Track last login from `user_activity_log`

**Security:**
- Requires `INDIRECT_CLIENT_ADMIN` role
- Users can only view users within their indirect client organization
- Filter by `indirect_client_id` in all queries

## Dependencies

- User authentication and authorization system
- Indirect client context resolution

## Test Cases

1. **View All Users**: Admin logs in and sees grid with all users in their organization
2. **Filter by Status**: Select "Locked" filter and verify only locked users are displayed
3. **Filter by Role**: Select "Admin" role filter and verify only admins are shown
4. **Search Users**: Enter email in search and verify matching users appear
5. **Sort by Column**: Click "Last Login" header and verify users sort by last login date
6. **Pagination**: Navigate through pages and verify 20 users per page
7. **Empty State**: New organization with no users shows empty state message
8. **Cross-Organization Security**: Verify users cannot see users from other indirect clients
9. **Lock Type Display**: Verify different lock types display correctly (CLIENT, BANK, SECURITY)
10. **Real-time Updates**: Add new user and verify grid refreshes automatically

## UI/UX

**Grid Layout:**
- Header with "Users" title and "Add User" button
- Filter bar with dropdowns for status and role, search input
- Data grid with columns: Email, Name, Login ID, Status, Lock Type, Last Login, Roles, Actions
- Row actions: View Details, Edit, Lock/Unlock, Delete
- Pagination controls at bottom
- Empty state: "No users found. Click 'Add User' to get started."

**Status Indicators:**
- Active: Green dot + "Active"
- Locked: Red dot + "Locked (CLIENT/BANK/SECURITY)"
- Inactive: Gray dot + "Inactive"

**Roles Display:**
- Show primary role as badge
- "+X more" indicator if user has multiple roles
- Hover to see all roles
