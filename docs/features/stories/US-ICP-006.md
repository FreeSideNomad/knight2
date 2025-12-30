# US-ICP-006: Unlock User

## Story

**As an** Indirect Client Administrator
**I want** to unlock user accounts that I have locked
**So that** I can restore access after resolving issues

## Acceptance Criteria

- [ ] Unlock action is available from user grid and user detail page
- [ ] Can only unlock users with "CLIENT" level locks
- [ ] Cannot unlock users with "BANK" or "SECURITY" level locks
- [ ] Confirmation dialog appears before unlocking
- [ ] Unlocked user can sign in immediately
- [ ] User status changes to "Active" in user grid
- [ ] Lock record is marked as resolved (not deleted)
- [ ] Admin can provide optional notes when unlocking
- [ ] Unlocked user receives email notification (optional, configurable)
- [ ] Audit log records unlock action with notes
- [ ] Clear error message if attempting to unlock BANK/SECURITY locks

## Technical Notes

**API Endpoints:**
- `POST /api/indirect/users/{userId}/unlock` - Unlock user account
  - Request: `{ notes: string (optional), notifyUser: boolean }`
  - Response: `UserDTO`
- `GET /api/indirect/users/{userId}/lock-status` - Check lock status and type
  - Response: `{ isLocked: boolean, lockType: string, canUnlock: boolean, reason: string }`

**Database:**
- Update `user_locks` table:
  - Set `status` to 'RESOLVED'
  - Set `unlocked_by` to admin user ID
  - Set `unlocked_at` to current timestamp
  - Add `unlock_notes` if provided
- Update `indirect_client_users.status` to 'ACTIVE'
- Do not delete lock records (maintain audit trail)

**Business Rules:**
- Check lock type before unlocking
- CLIENT locks: Can be unlocked by indirect client admin
- BANK locks: Can only be unlocked by bank administrator
- SECURITY locks: Can only be unlocked by security team
- If user has multiple active locks, only resolve CLIENT level locks

**Security:**
- Requires `INDIRECT_CLIENT_ADMIN` role
- Can only unlock users within same indirect client organization
- Cannot unlock locks created by higher authority levels
- Audit log entry includes: user ID, lock type, notes, admin ID, timestamp

**Email Notification (if enabled):**
- Subject: "Your account has been unlocked"
- Body includes: unlock notes (if provided), next steps for login
- Template configurable per organization

## Dependencies

- US-ICP-005: Lock User (must have locked users to unlock)
- US-ICP-001: View Users (display unlock action)
- US-ICP-010: User Detail Page (unlock action)
- Email service (optional notification)

## Test Cases

1. **Unlock User Successfully**: Select unlock for CLIENT-locked user and verify status changes to active
2. **Enable Login**: Unlock user and verify they can log in immediately
3. **Cannot Unlock BANK Lock**: Attempt to unlock BANK-locked user and verify error message
4. **Cannot Unlock SECURITY Lock**: Attempt to unlock SECURITY-locked user and verify error message
5. **Unlock with Notes**: Provide notes and verify they're stored in database
6. **Email Notification**: Unlock user with notification enabled and verify email sent
7. **Skip Notification**: Unlock user with notification disabled and verify no email sent
8. **Audit Trail**: Verify original lock record is preserved with resolved status
9. **Audit Log**: Unlock user and verify audit log entry created with notes
10. **Multiple Locks**: Unlock user with both CLIENT and BANK locks and verify only CLIENT lock resolved
11. **Cross-Organization Security**: Verify cannot unlock users from other indirect clients
12. **Already Unlocked**: Attempt to unlock unlocked user and verify appropriate message

## UI/UX

**Unlock Confirmation Dialog:**
```
Unlock User Account?
--------------------

Are you sure you want to unlock this user?

Name: John Smith
Email: john.smith@example.com
Login ID: jsmith

Original Lock Reason: Suspicious activity detected
Locked By: admin@example.com
Locked At: 2025-12-28 14:20:00

When unlocked, this user will:
- Be able to sign in immediately
- Regain all their permissions

Unlock Notes (optional):
[_____________________________________________]
[_____________________________________________]

[ ] Send email notification to user

[Cancel]  [Unlock Account]
```

**Cannot Unlock Dialog (for BANK/SECURITY locks):**
```
Cannot Unlock User
------------------

This user is locked at the {BANK/SECURITY} level.

Name: John Smith
Email: john.smith@example.com
Lock Type: BANK
Locked By: bank.admin@example.com
Locked At: 2025-12-28 14:20:00
Reason: Compliance review in progress

Only a {Bank Administrator/Security Team member} can unlock this account.

Contact: {contact.email@example.com}

[Close]
```

**Error Messages:**
- Higher-level lock: "This user has a {BANK/SECURITY} lock that can only be removed by {appropriate authority}. Contact {contact info}."
- Already unlocked: "This user is not currently locked."

**Success Message:**
"User {name} has been unlocked successfully"

**Grid Display:**
- Unlock action only appears for CLIENT-locked users
- Disabled unlock button with tooltip for BANK/SECURITY locks: "Contact {authority} to unlock"
- Status changes to green dot + "Active" after unlock

**User Detail Page:**
```
Lock History
------------
Lock Type: CLIENT (Resolved)
Locked By: admin@example.com
Locked At: 2025-12-28 14:20:00
Lock Reason: Suspicious activity detected
Unlocked By: admin2@example.com
Unlocked At: 2025-12-30 10:30:00
Unlock Notes: Issue resolved, user verified

[View Full Lock History]
```
