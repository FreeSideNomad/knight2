# US-ICP-007: Re-enroll MFA

## Story

**As an** Indirect Client Administrator
**I want** to force a user to re-enroll their MFA device
**So that** I can help users who have lost access to their MFA device or resolve security concerns

## Acceptance Criteria

- [ ] Re-enroll MFA action is available from user detail page
- [ ] Confirmation dialog appears before resetting MFA
- [ ] User's current MFA enrollments are removed
- [ ] User is forced to enroll MFA on next login
- [ ] User cannot bypass MFA enrollment
- [ ] User receives email notification about MFA reset
- [ ] Email includes instructions for MFA re-enrollment
- [ ] Audit log records MFA reset action
- [ ] Active sessions are invalidated after MFA reset
- [ ] User can choose from available MFA methods during re-enrollment (SMS, authenticator app, passkey)

## Technical Notes

**API Endpoints:**
- `POST /api/indirect/users/{userId}/mfa/reset` - Reset MFA enrollment
  - Request: `{ reason: string (optional), notifyUser: boolean }`
  - Response: `{ success: boolean, message: string }`
- `GET /api/indirect/users/{userId}/mfa/status` - Get current MFA enrollment status
  - Response: `{ enrolled: boolean, methods: string[], enrolledAt: timestamp }`

**Auth0 Integration:**
- Call Auth0 Management API to remove MFA enrollments:
  - `DELETE /api/v2/users/{auth0UserId}/authenticators/{authenticatorId}`
- Set user metadata: `{ mfa_reset_required: true, mfa_reset_at: timestamp, mfa_reset_by: adminId }`
- Update user app_metadata to force MFA enrollment on next login
- Available MFA methods: SMS, TOTP (authenticator app), WebAuthn (passkey)

**Database:**
- Update `indirect_client_users` table:
  - Set `mfa_enrolled` to false
  - Set `mfa_reset_at` to current timestamp
  - Set `mfa_reset_by` to admin user ID
- Insert into `user_mfa_history` table for audit trail

**Authentication Flow After Reset:**
1. User logs in with username/password
2. System detects `mfa_reset_required = true`
3. User is redirected to MFA enrollment page
4. User must complete MFA enrollment before accessing portal
5. After successful enrollment, `mfa_reset_required` is set to false

**Security:**
- Requires `INDIRECT_CLIENT_ADMIN` role
- Can only reset MFA for users within same indirect client organization
- Cannot reset own MFA (prevents lockout)
- All active sessions and refresh tokens are invalidated
- Audit log entry includes: user ID, admin ID, reason, timestamp, IP address

**Email Notification:**
- Subject: "MFA has been reset for your account"
- Body includes:
  - Notification that MFA was reset by administrator
  - Reason (if provided)
  - Instructions for re-enrollment on next login
  - Available MFA methods
  - Support contact information
- Template configurable per organization

## Dependencies

- US-ICP-010: User Detail Page (MFA reset action)
- Auth0 MFA management
- Email service
- Session management for invalidation

## Test Cases

1. **Reset MFA Successfully**: Reset user's MFA and verify enrollments removed
2. **Force Re-enrollment**: User logs in after reset and must complete MFA enrollment
3. **Cannot Bypass**: Verify user cannot access portal without completing MFA enrollment
4. **Email Notification**: Reset MFA and verify user receives email with instructions
5. **Session Invalidation**: Reset MFA for logged-in user and verify session terminates
6. **MFA Method Selection**: During re-enrollment, verify user can choose SMS, authenticator app, or passkey
7. **Cannot Reset Self**: Admin tries to reset own MFA and verify error
8. **Audit Log**: Reset MFA and verify audit log entry created
9. **MFA Status**: After reset, verify user detail page shows "Not Enrolled"
10. **Cross-Organization Security**: Verify cannot reset MFA for users in other indirect clients
11. **Multiple Enrollments**: Reset user with multiple MFA methods and verify all are removed
12. **Re-enrollment Success**: Complete MFA enrollment and verify user can access portal

## UI/UX

**Re-enroll MFA Action (User Detail Page):**
```
Multi-Factor Authentication
---------------------------
Status: Enrolled
Methods:
  - Authenticator App (enrolled 2025-11-15)
  - SMS (***-***-1234)

[Reset MFA]
```

**Reset MFA Confirmation Dialog:**
```
Reset Multi-Factor Authentication?
----------------------------------

Are you sure you want to reset MFA for this user?

Name: John Smith
Email: john.smith@example.com
Login ID: jsmith

Current MFA Methods:
  - Authenticator App
  - SMS (***-***-1234)

When MFA is reset:
- All current MFA enrollments will be removed
- User will be required to re-enroll MFA on next login
- Active sessions will be terminated
- User will receive email instructions

Reason (optional):
[_____________________________________________]

[x] Send email notification to user

[Cancel]  [Reset MFA]
```

**Error Messages:**
- Self-reset: "You cannot reset your own MFA. Please contact another administrator."
- No MFA enrolled: "This user has not enrolled in MFA yet."

**Success Message:**
"MFA has been reset for {user name}. The user will be required to re-enroll on next login."

**Email Template:**
```
Subject: MFA has been reset for your account

Hello John,

Your multi-factor authentication (MFA) has been reset by an administrator.

Reason: Lost access to authenticator device

What happens next:
1. On your next login, you will be required to set up MFA
2. You can choose from these methods:
   - Authenticator app (recommended)
   - SMS text message
   - Passkey/biometric authentication

Instructions:
1. Log in with your username and password
2. Follow the on-screen instructions to set up MFA
3. You must complete MFA setup before accessing the portal

If you have questions, contact your administrator at:
support@example.com

Thank you,
Security Team
```

**MFA Re-enrollment Screen (after login):**
```
Set Up Multi-Factor Authentication
-----------------------------------

To keep your account secure, you must set up multi-factor
authentication before continuing.

Choose your MFA method:

( ) Authenticator App (Recommended)
    Use Google Authenticator, Microsoft Authenticator, or similar app

( ) SMS Text Message
    Receive codes via text message to your phone

( ) Passkey
    Use biometric authentication (fingerprint, Face ID)

[Continue]
```
