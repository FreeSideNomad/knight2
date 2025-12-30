# US-ICP-005: Lock User

## Story

**As an** Indirect Client Administrator
**I want** to lock user accounts
**So that** I can prevent suspicious or unauthorized access while investigating issues

## Acceptance Criteria

- [ ] Lock action is available from user grid and user detail page
- [ ] Lock type is set to "CLIENT" (organization-level lock)
- [ ] Confirmation dialog appears before locking
- [ ] Locked user cannot sign in to the portal
- [ ] Active sessions are immediately invalidated
- [ ] User status changes to "Locked" in user grid
- [ ] Lock type displays as "CLIENT" in user grid
- [ ] Admin can provide optional reason for lock
- [ ] Locked user receives email notification (optional, configurable)
- [ ] Audit log records lock action with reason
- [ ] Cannot lock yourself
- [ ] Cannot lock the last admin user

## Technical Notes

**API Endpoints:**
- `POST /api/indirect/users/{userId}/lock` - Lock user account
  - Request: `{ reason: string (optional), notifyUser: boolean }`
  - Response: `UserDTO`
- `GET /api/indirect/users/{userId}/lock-validation` - Check if user can be locked
  - Response: `{ canLock: boolean, reason: string }`

**Database:**
- Insert into `user_locks` table:
  - `user_id`: UUID
  - `lock_type`: 'CLIENT'
  - `locked_by`: UUID (admin user ID)
  - `locked_at`: TIMESTAMP
  - `reason`: TEXT (optional)
  - `status`: 'ACTIVE'
- Update `indirect_client_users.status` to 'LOCKED'

**Lock Types Hierarchy:**
- CLIENT: Set by indirect client admin (can be unlocked by admin)
- BANK: Set by bank administrator (cannot be unlocked by client admin)
- SECURITY: Set by security team for fraud/security issues (cannot be unlocked by client admin)

**Authentication Impact:**
- Check lock status during authentication
- If any active lock exists, deny login
- Return error: "Account is locked. Please contact your administrator."
- Invalidate all active sessions and refresh tokens

**Security:**
- Requires `INDIRECT_CLIENT_ADMIN` role
- Can only lock users within same indirect client organization
- Cannot lock users with BANK or SECURITY level locks already in place
- Audit log entry includes: user ID, lock type, reason, admin ID, timestamp

**Email Notification (if enabled):**
- Subject: "Your account has been locked"
- Body includes: lock reason (if provided), contact information for administrator
- Template configurable per organization

## Dependencies

- US-ICP-001: View Users (display lock status)
- US-ICP-010: User Detail Page (lock action)
- Authentication service (session invalidation)
- Email service (optional notification)

## Test Cases

1. **Lock User Successfully**: Select lock, confirm, and verify user status changes to locked
2. **Prevent Login**: Lock user and verify they cannot log in
3. **Session Invalidation**: Lock logged-in user and verify their session terminates immediately
4. **Lock Type**: Verify lock type is set to "CLIENT" in database and displayed in grid
5. **Cannot Lock Self**: Admin tries to lock own account and verify error
6. **Cannot Lock Last Admin**: Attempt to lock last admin and verify error
7. **Lock with Reason**: Provide reason and verify it's stored in database
8. **Email Notification**: Lock user with notification enabled and verify email sent
9. **Skip Notification**: Lock user with notification disabled and verify no email sent
10. **Audit Log**: Lock user and verify audit log entry created with reason
11. **Cross-Organization Security**: Verify cannot lock users from other indirect clients
12. **Already Locked**: Attempt to lock already locked user and verify appropriate message
13. **Higher-Level Lock**: Verify cannot lock user with BANK or SECURITY lock

## UI/UX

**Lock Confirmation Dialog:**
```
Lock User Account?
------------------

Are you sure you want to lock this user?

Name: John Smith
Email: john.smith@example.com
Login ID: jsmith

When locked, this user will:
- Be unable to sign in
- Have all active sessions terminated
- See "Account locked" message when attempting to login

Reason (optional):
[_____________________________________________]
[_____________________________________________]

[ ] Send email notification to user

[Cancel]  [Lock Account]
```

**Error Messages:**
- Self-lock: "You cannot lock your own account."
- Last admin: "Cannot lock the only administrator. Please assign another user as admin first."
- Already locked: "This user is already locked at the {BANK/SECURITY} level. Contact {appropriate authority} to unlock."

**Success Message:**
"User {name} has been locked successfully"

**Grid Display:**
- Status column shows red dot + "Locked"
- Lock Type column shows "CLIENT" badge
- Lock icon appears in actions column (changes to unlock)

**User Detail Page:**
```
Lock Status
-----------
Status: Locked
Lock Type: CLIENT
Locked By: admin@example.com
Locked At: 2025-12-30 10:30:00
Reason: Suspicious activity detected

[Unlock Account]
```
