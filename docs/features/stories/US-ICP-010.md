# US-ICP-010: User Detail Page

## Story

**As an** Indirect Client Administrator
**I want** to view comprehensive details about a user
**So that** I can manage their account, permissions, and troubleshoot issues

## Acceptance Criteria

- [ ] Detail page accessible from user grid (click on user row)
- [ ] Page displays user profile: name, email, login ID, status, creation date
- [ ] Page shows current roles and effective permissions
- [ ] Page displays lock status and lock history
- [ ] Page shows MFA enrollment status and enrolled methods
- [ ] Page displays last login timestamp and IP address
- [ ] Page shows recent activity log (last 20 actions)
- [ ] Action buttons available: Edit, Lock/Unlock, Delete, Reset MFA, Manage Permissions
- [ ] Action buttons are context-aware (e.g., "Unlock" only shown for locked users)
- [ ] Tabs organize information: Overview, Permissions, Activity, Lock History
- [ ] Page refreshes in real-time if user data changes
- [ ] Breadcrumb navigation shows: Users > User Name
- [ ] Back button returns to user list

## Technical Notes

**API Endpoints:**
- `GET /api/indirect/users/{userId}` - Get user details
  - Response: `UserDetailDTO`
- `GET /api/indirect/users/{userId}/activity` - Get user activity log
  - Query params: `page`, `size`, `startDate`, `endDate`
  - Response: `Page<ActivityLogDTO>`
- `GET /api/indirect/users/{userId}/lock-history` - Get lock history
  - Response: `LockHistoryDTO[]`
- `GET /api/indirect/users/{userId}/permissions` - Get roles and permissions
  - Response: `{ roles: RoleDTO[], effectivePermissions: string[] }`
- `GET /api/indirect/users/{userId}/mfa/status` - Get MFA status
  - Response: `{ enrolled: boolean, methods: MfaMethodDTO[] }`

**Database Queries:**
- Join `indirect_client_users` with `user_roles`, `user_permissions`
- Query `user_locks` for lock history
- Query `user_activity_log` for recent activity
- Query `user_mfa_enrollments` or Auth0 for MFA status

**Real-time Updates:**
- Use WebSocket or Server-Sent Events for live updates
- Subscribe to user changes when page loads
- Update UI when user status, permissions, or locks change
- Show notification when data is updated

**Activity Log Types:**
- LOGIN: User logged in
- LOGOUT: User logged out
- PASSWORD_CHANGED: Password was changed
- MFA_ENROLLED: MFA was enrolled
- MFA_RESET: MFA was reset
- LOCKED: Account was locked
- UNLOCKED: Account was unlocked
- ROLE_ASSIGNED: Role was assigned
- ROLE_REVOKED: Role was revoked
- PERMISSION_GRANTED: Permission was granted
- PERMISSION_REVOKED: Permission was revoked

**Security:**
- Requires `INDIRECT_CLIENT_ADMIN` role or `user.read` permission
- Can only view users within same indirect client organization
- Sensitive information (passwords, MFA secrets) never displayed
- Audit log access for viewing user details

## Dependencies

- US-ICP-001: View Users (navigation from grid)
- US-ICP-003: Delete User (delete action)
- US-ICP-004: Manage User Permissions (permissions tab)
- US-ICP-005: Lock User (lock action)
- US-ICP-006: Unlock User (unlock action)
- US-ICP-007: Re-enroll MFA (MFA reset action)

## Test Cases

1. **View User Details**: Click user in grid and verify detail page loads with all information
2. **Profile Information**: Verify name, email, login ID, status display correctly
3. **Roles and Permissions**: Verify user's roles and effective permissions shown
4. **Lock Status**: View locked user and verify lock details displayed
5. **MFA Status**: Verify MFA enrollment status and methods shown
6. **Last Login**: Verify last login timestamp and IP address displayed
7. **Activity Log**: Verify recent activities shown in chronological order
8. **Lock History**: Navigate to Lock History tab and verify all locks/unlocks shown
9. **Context-Aware Actions**: Verify action buttons change based on user state
10. **Edit Action**: Click Edit and verify edit form opens
11. **Lock Action**: Click Lock and verify lock confirmation dialog appears
12. **Delete Action**: Click Delete and verify delete confirmation dialog appears
13. **Real-time Update**: Change user in another session and verify detail page updates
14. **Breadcrumb Navigation**: Click breadcrumb and verify navigation to user list
15. **Cross-Organization Security**: Verify cannot view users from other indirect clients
16. **Tab Navigation**: Switch between tabs and verify data loads correctly
17. **Pagination in Activity**: Scroll activity log and verify pagination works

## UI/UX

**User Detail Page Layout:**
```
Users > John Smith                                    [Back to Users]
------------------------------------------------------------------

[Photo]  John Smith                                    ● Active
         john.smith@example.com
         Login ID: jsmith

         [Edit]  [Lock Account]  [Reset MFA]  [Delete]

------------------------------------------------------------------
[Overview] [Permissions] [Activity] [Lock History]

Profile Information
-------------------
Created: 2025-10-15 9:30 AM
Last Updated: 2025-12-28 2:15 PM
Last Login: 2025-12-30 10:45 AM from 192.168.1.100
Status: Active

Multi-Factor Authentication
----------------------------
Status: Enrolled
Methods:
  - Authenticator App (enrolled 2025-10-15)
  - Passkey - MacBook Pro (enrolled 2025-11-20)
[Reset MFA]

Roles
-----
● Admin
● User Manager
[Manage Permissions]

Quick Stats
-----------
Total Logins: 247
Failed Login Attempts: 2
Last Password Change: 2025-11-01

Recent Activity
---------------
Date/Time              Action              Details                IP Address
2025-12-30 10:45 AM   LOGIN               Successful             192.168.1.100
2025-12-30 9:30 AM    LOGOUT              -                      192.168.1.100
2025-12-29 4:20 PM    LOGIN               Successful             192.168.1.100
2025-12-29 11:15 AM   PERMISSION_GRANTED  user.delete granted    192.168.1.50
2025-12-28 2:15 PM    ROLE_ASSIGNED       User Manager           192.168.1.50

[Load More Activity]
```

**Permissions Tab:**
```
Roles and Permissions
---------------------

Current Roles
-------------
● Admin
  Assigned: 2025-10-15
  Assigned By: admin@example.com

● User Manager
  Assigned: 2025-12-28
  Assigned By: admin2@example.com

[Manage Roles]

Effective Permissions (15 total)
---------------------------------
✓ user.read
✓ user.write
✓ user.delete
✓ user.manage_permissions
✓ profile.read
✓ profile.write
✓ account.read
✓ reports.read
✓ reports.export

[View All Permissions]  [Manage Permissions]
```

**Activity Tab:**
```
Activity Log
------------

Filter: [All Actions ▼]  Date Range: [Last 30 Days ▼]  [Apply]

Date/Time              Action              Details                      Performed By
2025-12-30 10:45 AM   LOGIN               Successful                   john.smith@example.com
2025-12-30 9:30 AM    LOGOUT              -                            john.smith@example.com
2025-12-29 4:20 PM    LOGIN               Successful                   john.smith@example.com
2025-12-29 11:15 AM   PERMISSION_GRANTED  user.delete granted          admin2@example.com
2025-12-28 2:15 PM    ROLE_ASSIGNED       User Manager                 admin2@example.com
2025-12-27 3:10 PM    MFA_ENROLLED        Passkey - MacBook Pro        john.smith@example.com
2025-12-26 10:00 AM   PASSWORD_CHANGED    -                            john.smith@example.com
2025-12-25 9:15 AM    LOGIN               Failed - Invalid password    john.smith@example.com

[Load More]  [Export to CSV]
```

**Lock History Tab:**
```
Lock History
------------

Current Lock Status: Not Locked

Lock #1
-------
Type: CLIENT
Status: Resolved
Locked By: admin@example.com
Locked At: 2025-12-20 2:00 PM
Reason: Suspicious login attempts
Unlocked By: admin@example.com
Unlocked At: 2025-12-21 9:00 AM
Unlock Notes: User verified, issue resolved

Lock #2
-------
Type: CLIENT
Status: Resolved
Locked By: admin2@example.com
Locked At: 2025-11-15 4:30 PM
Reason: Password reset required
Unlocked By: admin2@example.com
Unlocked At: 2025-11-15 5:00 PM
Unlock Notes: Password reset completed

[No more lock history]
```

**Action Buttons (Context-Aware):**
- Active user: [Edit] [Lock Account] [Reset MFA] [Delete]
- Locked user (CLIENT): [Edit] [Unlock Account] [Reset MFA] [Delete]
- Locked user (BANK/SECURITY): [Edit] [View Lock Details] [Reset MFA] [Delete]
- Last admin: [Edit] [Reset MFA] (Lock and Delete disabled with tooltip)

**Empty States:**
- No activity: "No activity recorded for this user yet."
- No lock history: "This user has never been locked."
- No MFA: "MFA not enrolled. User will be prompted to enroll on next login."
