# US-UM-010: User List with Filters

## Story

**As a** bank administrator
**I want** to view and filter the user list by status, role, and search criteria
**So that** I can quickly find and manage users in the system

## Acceptance Criteria

- [ ] User list displays key information: name, login ID, email, roles, status, last login
- [ ] List supports pagination with configurable page size
- [ ] Filter by user status: Active, Locked, Pending Registration, Deleted
- [ ] Filter by roles: multiple selection supported
- [ ] Search by name, login ID, or email with partial matching
- [ ] Column sorting (name, login ID, email, last login)
- [ ] Default sort: last login descending
- [ ] Bulk actions: lock, unlock, export
- [ ] Visual indicators for user status (badges, icons, colors)
- [ ] Quick actions menu per user row
- [ ] Responsive design for various screen sizes
- [ ] Export filtered results to CSV

## Technical Notes

**API Endpoint:**
```
GET /api/users
Query Parameters:
  - status: string[] (ACTIVE, LOCKED, PENDING_REGISTRATION, DELETED)
  - roles: string[] (SUPER_ADMIN, BANK_ADMIN, etc.)
  - search: string (searches name, login_id, email)
  - sortBy: string (name, loginId, email, lastLoggedInAt)
  - sortDirection: string (ASC, DESC)
  - page: number (default: 0)
  - size: number (default: 20)
  - includeDeleted: boolean (default: false)

Response: 200 OK
{
  "users": [
    {
      "id": "usr_123456",
      "loginId": "jdoe",
      "email": "john.doe@example.com",
      "firstName": "John",
      "lastName": "Doe",
      "roles": ["EMPLOYEE", "CLIENT_MANAGER"],
      "status": "ACTIVE",
      "lockType": "NONE",
      "lastLoggedInAt": "2025-12-30T15:30:00Z",
      "mfaEnabled": true,
      "createdAt": "2025-12-01T10:00:00Z"
    }
  ],
  "totalElements": 150,
  "totalPages": 8,
  "currentPage": 0,
  "pageSize": 20
}

GET /api/users/export
Response: CSV file download
```

**User Status Calculation:**
- ACTIVE: Not locked, not deleted, registration complete
- LOCKED: lock_type != NONE
- PENDING_REGISTRATION: registration_token not null and not expired
- DELETED: deleted_at not null

**Implementation:**
- Use specification pattern for dynamic filtering
- Implement efficient database queries with proper indexing
- Cache role list for filter dropdown
- Implement debounced search (300ms delay)
- Add row-level security checks for bulk actions

**Performance Considerations:**
- Index on status, roles, last_logged_in_at
- Limit search results to prevent performance issues
- Use cursor-based pagination for large datasets
- Consider read replica for list queries

## Dependencies

- US-UM-001: Track User Login Time
- US-UM-003: Lock Type Implementation
- US-UM-004: User Login ID Separate from Email
- US-UM-007: Soft Delete User

## Test Cases

1. **Default List Load**: Verify user list loads with default filters (active users only)
2. **Pagination**: Verify pagination controls work correctly
3. **Page Size Change**: Verify changing page size updates results
4. **Status Filter - Active**: Verify filtering by ACTIVE status
5. **Status Filter - Locked**: Verify filtering by LOCKED status
6. **Status Filter - Pending**: Verify filtering by PENDING_REGISTRATION status
7. **Status Filter - Deleted**: Verify deleted users appear when filter applied
8. **Role Filter - Single**: Verify filtering by single role
9. **Role Filter - Multiple**: Verify filtering by multiple roles (OR logic)
10. **Search - Name**: Verify partial name matching
11. **Search - Login ID**: Verify partial login ID matching
12. **Search - Email**: Verify partial email matching
13. **Search - No Results**: Verify empty state when no matches found
14. **Sort - Name**: Verify sorting by name (ASC/DESC)
15. **Sort - Last Login**: Verify sorting by last login timestamp
16. **Combined Filters**: Verify combining status, role, and search filters
17. **Bulk Lock**: Verify bulk lock action on selected users
18. **Bulk Unlock**: Verify bulk unlock action on selected users
19. **Export CSV**: Verify CSV export includes filtered results
20. **Quick Actions**: Verify per-row action menu works
21. **Responsive Design**: Verify layout adapts to mobile screens
22. **Performance**: Verify list loads within 2 seconds for 10,000 users

## UI/UX (if applicable)

**User List View:**
```
┌───────────────────────────────────────────────────────────────────┐
│ Users                                            [+ Create User]  │
├───────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────────┐  │
│ │ Search users...                                         🔍  │  │
│ └─────────────────────────────────────────────────────────────┘  │
│                                                                   │
│ Status: [All ▾]  Roles: [All ▾]  [Clear Filters]  [Export CSV]  │
│                                                                   │
│ ┌─┬──────────┬──────────┬─────────────────────┬───────┬─────┐   │
│ │☐│Name      │Login ID  │Email                │Roles  │Last│   │
│ ├─┼──────────┼──────────┼─────────────────────┼───────┼─────┤   │
│ │☐│John Doe  │jdoe      │john.doe@example.com │🏢💼  │2h  │⋮ │
│ │ │          │          │✓ Active • 🔐 MFA    │       │ago │   │
│ │ │          │          │                     │       │    │   │
│ │☐│Jane Smith│jsmith    │jane.smith@ex.com    │👑     │1d  │⋮ │
│ │ │          │          │✓ Active • 🔐 MFA    │       │ago │   │
│ │ │          │          │                     │       │    │   │
│ │☐│Bob Jones │bjones    │bob.jones@ex.com     │🏢     │3d  │⋮ │
│ │ │          │          │🔒 Locked (BANK)     │       │ago │   │
│ │ │          │          │                     │       │    │   │
│ │☐│Alice Wong│awong     │alice.wong@ex.com    │🏢💼  │Never│⋮│
│ │ │          │          │⏳ Pending Registration      │    │   │
│ │ │          │          │                     │       │    │   │
│ └─┴──────────┴──────────┴─────────────────────┴───────┴─────┘   │
│                                                                   │
│ 4 users selected  [🔒 Lock] [🔓 Unlock]                          │
│                                                                   │
│                     ← 1 2 3 4 5 6 7 8 →                          │
│               Showing 1-20 of 150 • [20 per page ▾]              │
└───────────────────────────────────────────────────────────────────┘
```

**Status Filter Dropdown:**
```
[All                              ▾]
  All Statuses
  ─────────────
  ✓ Active (142)
  🔒 Locked (5)
  ⏳ Pending Registration (3)
  🗑 Deleted (12)
```

**Role Filter Dropdown:**
```
[All                              ▾]
  All Roles
  ─────────────
  ☐ Super Admin (2)
  ☐ Bank Admin (8)
  ☑ Employee (95)
  ☑ Client Manager (45)
  ☐ Indirect Client Manager (12)
  ☐ Support (18)
```

**Quick Actions Menu (⋮):**
```
  View Details
  Edit User
  ─────────────
  Reset Password
  Reset MFA
  ─────────────
  Lock Account
  Delete User
  ─────────────
  View Activity Log
```

**Role Icons:**
- 👑 Super Admin
- 🏢 Bank Admin, Employee
- 💼 Client Manager, Indirect Client Manager
- 🎧 Support

**Status Badges:**
```
✓ Active         (green badge)
🔒 Locked        (orange badge with lock type: CLIENT/BANK/SECURITY)
⏳ Pending       (yellow badge)
🗑 Deleted       (gray badge with strikethrough)
```

**Empty State:**
```
┌─────────────────────────────────────────┐
│                                         │
│            👥                           │
│                                         │
│        No users found                   │
│                                         │
│   Try adjusting your filters or         │
│   search criteria                       │
│                                         │
│       [Clear All Filters]               │
│                                         │
└─────────────────────────────────────────┘
```

**Mobile/Responsive Design:**
```
┌─────────────────────────────┐
│ Users          [+ Create]   │
├─────────────────────────────┤
│ 🔍 Search users...          │
│ Filters: Active, Employee ⚙│
├─────────────────────────────┤
│ John Doe                 ⋮  │
│ jdoe • ✓ Active • 🔐       │
│ 2 hours ago                 │
├─────────────────────────────┤
│ Jane Smith               ⋮  │
│ jsmith • ✓ Active • 🔐     │
│ 1 day ago                   │
├─────────────────────────────┤
│ Bob Jones                ⋮  │
│ bjones • 🔒 Locked (BANK)  │
│ 3 days ago                  │
├─────────────────────────────┤
│        ← 1 2 3 4 5 →        │
│      Showing 1-20 of 150    │
└─────────────────────────────┘
```

**Bulk Action Confirmation:**
```
┌─────────────────────────────────────────┐
│ Lock Users                         [X]  │
├─────────────────────────────────────────┤
│                                         │
│ Lock 4 selected users?                  │
│                                         │
│ • John Doe (jdoe)                       │
│ • Jane Smith (jsmith)                   │
│ • Bob Johnson (bjohnson)                │
│ • Alice Wong (awong)                    │
│                                         │
│ Lock Type: [BANK ▾]                     │
│                                         │
│ Reason (optional):                      │
│ [_________________________________]     │
│                                         │
│         [Cancel]  [Lock Users]          │
└─────────────────────────────────────────┘
```
