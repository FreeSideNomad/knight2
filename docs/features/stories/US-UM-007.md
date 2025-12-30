# US-UM-007: Soft Delete User

## Story

**As a** bank administrator
**I want** to soft delete users instead of permanently removing them
**So that** I can maintain data integrity, audit trails, and comply with retention policies

## Acceptance Criteria

- [ ] Users are marked as deleted instead of being removed from database
- [ ] Deleted users are hidden from user list by default
- [ ] Deleted users cannot log in
- [ ] User data (clients, profiles, audit logs) is retained
- [ ] Deletion timestamp and deleting user are recorded
- [ ] Deleted users can be viewed with "Show deleted" filter
- [ ] Email and login_id remain unique even for deleted users
- [ ] Optional reason for deletion can be provided
- [ ] Deletion action is logged in audit trail
- [ ] Related permissions and sessions are revoked

## Technical Notes

**Database Changes:**
```sql
-- Add soft delete columns
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN deleted_by VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN deletion_reason TEXT NULL;

-- Create index for filtering
CREATE INDEX idx_users_deleted_at ON users(deleted_at);

-- Update unique constraints to handle deleted users
-- Option 1: Partial unique index (excludes deleted users)
CREATE UNIQUE INDEX uk_users_email_active
ON users(email) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX uk_users_login_id_active
ON users(login_id) WHERE deleted_at IS NULL;
```

**Implementation:**
- Add `deletedAt`, `deletedBy`, `deletionReason` to User entity
- Update UserRepository queries to exclude deleted users by default
- Add `findIncludingDeleted()` method for admin views
- Update authentication to block deleted users
- Revoke all active sessions on deletion
- Update Auth0 user status to blocked
- Add deletion event to audit log

**API Endpoints:**
```
DELETE /api/users/{id}
Request:
{
  "reason": "Employee left company"
}

Response: 200 OK
{
  "id": "usr_123456",
  "deletedAt": "2025-12-30T15:30:00Z",
  "deletedBy": "admin@example.com",
  "deletionReason": "Employee left company"
}

GET /api/users?includeDeleted=true
```

**Business Rules:**
- Only users with BANK_ADMIN or SUPER_ADMIN roles can delete users
- Cannot delete own account
- Cannot delete SUPER_ADMIN users (unless performed by another SUPER_ADMIN)
- Deletion reason is optional but recommended
- Deleted users retain all historical data associations

## Dependencies

- None

## Test Cases

1. **Soft Delete Success**: Verify user is marked as deleted with timestamp
2. **Delete Metadata**: Verify deletedBy and deletionReason are recorded
3. **Hidden from List**: Verify deleted users don't appear in default user list
4. **Login Blocked**: Verify deleted user cannot log in
5. **Auth0 Update**: Verify user is blocked in Auth0
6. **Session Revocation**: Verify active sessions are terminated
7. **Data Retention**: Verify user's clients and profiles are retained
8. **Audit Trail**: Verify deletion is logged
9. **Show Deleted Filter**: Verify deleted users appear with includeDeleted=true
10. **Uniqueness**: Verify new user can be created with same email/login_id after deletion
11. **Cannot Delete Self**: Verify error when user tries to delete own account
12. **Permission Check**: Verify only authorized roles can delete users
13. **Cannot Delete Super Admin**: Verify regular admin cannot delete super admin

## UI/UX (if applicable)

**User List View:**
- Default view excludes deleted users
- Add toggle: "Show deleted users"
- Deleted users displayed with strikethrough and gray background
- Deleted badge/icon next to user name
- "Deleted on [date] by [user]" in row details

**User Detail View - Active User:**
```
┌─────────────────────────────────────────┐
│ John Doe                    [⋮ Actions] │
│ jdoe                                    │
├─────────────────────────────────────────┤
│ Status: Active                          │
│ ...                                     │
│                                         │
│ [Actions Dropdown]                      │
│   Edit User                             │
│   Reset Password                        │
│   Lock Account                          │
│   Delete User                           │
└─────────────────────────────────────────┘
```

**Delete Confirmation Dialog:**
```
┌─────────────────────────────────────────┐
│ ⚠ Delete User                      [X]  │
├─────────────────────────────────────────┤
│                                         │
│ Are you sure you want to delete this   │
│ user?                                   │
│                                         │
│ User: John Doe (jdoe)                   │
│ Email: john.doe@example.com             │
│                                         │
│ This action will:                       │
│ • Prevent the user from logging in      │
│ • Revoke all active sessions            │
│ • Hide the user from the user list      │
│ • Retain all user data and history      │
│                                         │
│ Reason for deletion (optional):         │
│ [_________________________________]     │
│ [_________________________________]     │
│                                         │
│         [Cancel]  [Delete User]         │
└─────────────────────────────────────────┘
```

**User Detail View - Deleted User:**
```
┌─────────────────────────────────────────┐
│ John Doe                   🗑 DELETED   │
│ jdoe                                    │
├─────────────────────────────────────────┤
│ Status: Deleted                         │
│ Deleted: Dec 30, 2025 by admin@ex.com  │
│ Reason: Employee left company           │
│                                         │
│ User information is retained for audit  │
│ purposes but login is disabled.         │
│                                         │
│ [View Audit Log]                        │
└─────────────────────────────────────────┘
```

**User List with Deleted Users:**
```
Filter: [Show deleted users ✓]

Name          Login ID  Status    Last Login
────────────────────────────────────────────
John Doe      jdoe      Active    2 hours ago
Jane Smith    jsmith    🗑 Deleted  Dec 25, 2025
Bob Johnson   bjohnson  Locked    Yesterday
```
