# US-UM-008: Re-enroll MFA

## Story

**As a** bank administrator
**I want** to force users to re-enroll in MFA on their next login
**So that** I can address security concerns or help users who have lost access to their authenticator device

## Acceptance Criteria

- [ ] Admin can trigger MFA re-enrollment for a user
- [ ] User's existing MFA enrollment is invalidated
- [ ] User is prompted to set up MFA on next login
- [ ] User cannot bypass MFA setup
- [ ] Old recovery codes are invalidated
- [ ] New recovery codes are generated after re-enrollment
- [ ] Re-enrollment action is logged in audit trail
- [ ] Optional reason for re-enrollment can be provided
- [ ] User receives email notification about MFA reset
- [ ] Active sessions are revoked when MFA is reset

## Technical Notes

**Database Changes:**
```sql
-- Add MFA tracking columns
ALTER TABLE users ADD COLUMN mfa_enrolled_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN mfa_reset_required BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_reset_reason TEXT NULL;
ALTER TABLE users ADD COLUMN mfa_reset_by VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN mfa_reset_at TIMESTAMP NULL;
```

**Implementation:**
1. Admin triggers MFA reset via API
2. Set `mfa_reset_required = TRUE`
3. Record reset metadata (reason, reset_by, reset_at)
4. Invalidate existing MFA enrollment in Auth0
5. Revoke all active sessions
6. Send notification email to user
7. On next login, redirect to MFA enrollment flow
8. After successful enrollment:
   - Set `mfa_reset_required = FALSE`
   - Set `mfa_enrolled_at` to current timestamp
   - Generate new recovery codes

**Auth0 Integration:**
- Use Auth0 Management API to reset MFA: `DELETE /api/v2/users/{id}/authenticators`
- Update user app_metadata with MFA reset flag
- Configure Auth0 rules to enforce MFA enrollment before allowing access

**API Endpoints:**
```
POST /api/users/{id}/reset-mfa
Request:
{
  "reason": "User reported lost device"
}

Response: 200 OK
{
  "userId": "usr_123456",
  "mfaResetRequired": true,
  "mfaResetAt": "2025-12-30T15:30:00Z",
  "mfaResetBy": "admin@example.com",
  "mfaResetReason": "User reported lost device"
}
```

**Email Notification:**
- Subject: "Multi-Factor Authentication Reset Required"
- Content: Inform user that MFA has been reset and they need to re-enroll on next login
- Include security tips and support contact

## Dependencies

- None

## Test Cases

1. **Reset MFA Success**: Verify MFA is reset and flag is set
2. **Reset Metadata**: Verify reason, reset_by, and reset_at are recorded
3. **Auth0 Update**: Verify MFA enrollment is removed in Auth0
4. **Session Revocation**: Verify all active sessions are terminated
5. **Email Notification**: Verify user receives MFA reset email
6. **Login Redirect**: Verify user is redirected to MFA enrollment on next login
7. **Cannot Bypass**: Verify user cannot access system without completing MFA enrollment
8. **Recovery Codes Invalidated**: Verify old recovery codes don't work
9. **New Recovery Codes**: Verify new recovery codes are generated after re-enrollment
10. **Audit Log**: Verify MFA reset action is logged
11. **Permission Check**: Verify only authorized admins can reset MFA
12. **Flag Cleared**: Verify mfa_reset_required is set to FALSE after enrollment
13. **Enrollment Timestamp**: Verify mfa_enrolled_at is updated after re-enrollment

## UI/UX (if applicable)

**User Detail View - Reset MFA Action:**
```
┌─────────────────────────────────────────┐
│ John Doe                    [⋮ Actions] │
│ jdoe                                    │
├─────────────────────────────────────────┤
│ Security                                │
│ MFA: Enabled ✓                          │
│ Enrolled: Dec 15, 2025                  │
│                                         │
│ [Actions Dropdown]                      │
│   Reset Password                        │
│   Reset MFA                             │
│   View Activity                         │
└─────────────────────────────────────────┘
```

**Reset MFA Confirmation Dialog:**
```
┌─────────────────────────────────────────┐
│ ⚠ Reset Multi-Factor Authentication [X]│
├─────────────────────────────────────────┤
│                                         │
│ Are you sure you want to reset MFA for │
│ this user?                              │
│                                         │
│ User: John Doe (jdoe)                   │
│ Email: john.doe@example.com             │
│                                         │
│ This action will:                       │
│ • Remove current MFA enrollment         │
│ • Invalidate all recovery codes         │
│ • Log out all active sessions           │
│ • Require MFA setup on next login       │
│ • Send notification email to user       │
│                                         │
│ Reason for reset (optional):            │
│ [_________________________________]     │
│ [_________________________________]     │
│                                         │
│         [Cancel]  [Reset MFA]           │
└─────────────────────────────────────────┘
```

**User Login - MFA Required:**
```
┌─────────────────────────────────────────┐
│         Multi-Factor Authentication     │
│              Setup Required             │
├─────────────────────────────────────────┤
│                                         │
│ Your multi-factor authentication has   │
│ been reset and must be set up again.   │
│                                         │
│ Reason: User reported lost device       │
│                                         │
│ Click Continue to set up your           │
│ authenticator app.                      │
│                                         │
│              [Continue]                 │
│                                         │
│ Need help? Contact support              │
└─────────────────────────────────────────┘
```

**User Detail View - After Reset:**
```
┌─────────────────────────────────────────┐
│ John Doe                                │
│ jdoe                                    │
├─────────────────────────────────────────┤
│ Security                                │
│ MFA: ⚠ Re-enrollment Required           │
│ Reset: Dec 30, 2025 by admin@ex.com     │
│ Reason: User reported lost device       │
│                                         │
│ User will be prompted to re-enroll MFA  │
│ on next login.                          │
└─────────────────────────────────────────┘
```

**Email Notification Template:**
```
Subject: Multi-Factor Authentication Reset Required

Hi John,

Your multi-factor authentication (MFA) has been reset
by a system administrator.

Reason: User reported lost device
Reset by: admin@example.com
Date: December 30, 2025

What this means:
• Your current authenticator app is no longer active
• All recovery codes have been invalidated
• You've been logged out of all sessions

Next steps:
1. Log in to your account
2. Follow the prompts to set up MFA again
3. Save your new recovery codes in a safe place

If you didn't request this change, please contact
support immediately.

Questions? Contact support@example.com

Best regards,
The Security Team
```
