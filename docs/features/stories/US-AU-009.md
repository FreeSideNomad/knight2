# US-AU-009: Re-enroll MFA (Admin Initiated)

## Story

**As an** administrator
**I want** to reset a user's MFA enrollment
**So that** users who lost their MFA device can regain access to their account by re-enrolling in MFA on their next login

## Acceptance Criteria

- [ ] Admin can view user's current MFA enrollment status
- [ ] Admin can initiate MFA reset from user management interface
- [ ] MFA reset requires admin to provide reason/justification
- [ ] User's existing MFA enrollments (Guardian/Passkey) are cleared
- [ ] User's recovery codes are invalidated
- [ ] User is notified via email about MFA reset
- [ ] User is forced to re-enroll in MFA on next login
- [ ] User cannot access the system until MFA re-enrollment is complete
- [ ] User can choose any supported MFA method during re-enrollment
- [ ] All admin MFA reset actions are logged for audit
- [ ] Admin must have appropriate permissions to reset MFA
- [ ] Confirmation prompt before MFA reset to prevent accidents

## Technical Notes

**Auth0 Configuration:**
- Use Auth0 Management API to delete authenticators
- Reset MFA flags in user metadata
- Configure authentication rule to check MFA enrollment status

**API Endpoints:**
```
GET /api/admin/users/:userId/mfa
- Request: { userId: string }
- Response: {
    enrolled: boolean,
    methods: ["guardian", "passkey"],
    enrolledAt: timestamp,
    devices: [
      { type: "guardian", deviceName: string },
      { type: "passkey", deviceName: string }
    ]
  }

POST /api/admin/users/:userId/mfa/reset
- Request: {
    userId: string,
    reason: string,
    adminId: string
  }
- Response: {
    success: boolean,
    mfaResetAt: timestamp,
    notificationSent: boolean
  }

GET /api/admin/users/:userId/mfa/reset-history
- Request: { userId: string }
- Response: {
    resets: [
      {
        resetBy: string,
        reason: string,
        timestamp: timestamp,
        reEnrolledAt: timestamp
      }
    ]
  }
```

**Database Changes:**
```sql
CREATE TABLE mfa_reset_log (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    admin_id UUID REFERENCES users(id),
    reason TEXT NOT NULL,
    previous_methods TEXT[], -- ["guardian", "passkey"]
    reset_at TIMESTAMP DEFAULT NOW(),
    re_enrolled_at TIMESTAMP,
    re_enrolled_method VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent TEXT
);

CREATE INDEX idx_mfa_reset_user ON mfa_reset_log(user_id);
CREATE INDEX idx_mfa_reset_admin ON mfa_reset_log(admin_id);
CREATE INDEX idx_mfa_reset_date ON mfa_reset_log(reset_at);

ALTER TABLE users ADD COLUMN mfa_reset_required BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_reset_at TIMESTAMP;
ALTER TABLE users ADD COLUMN mfa_reset_by UUID REFERENCES users(id);
```

**MFA Reset Process:**
1. Admin navigates to user profile
2. Admin clicks "Reset MFA"
3. System displays confirmation with current MFA methods
4. Admin enters reason (required)
5. System deletes all MFA enrollments from Auth0
6. System invalidates recovery codes
7. System sets `mfa_reset_required = true` flag
8. System sends notification email to user
9. System logs reset action with admin ID and reason
10. On next login, user is redirected to MFA enrollment

**Force Re-enrollment Logic:**
```javascript
// Auth0 Rule or Action
function (user, context, callback) {
  if (user.app_metadata && user.app_metadata.mfa_reset_required) {
    return callback(new UnauthorizedError('MFA re-enrollment required'));
  }
  callback(null, user, context);
}
```

**Notification Email:**
```
Subject: Multi-Factor Authentication Reset

Your multi-factor authentication has been reset by a system administrator.

Reason: User reported lost device

Reset by: Admin Name (admin.user@company.com)
Time: Dec 30, 2024 at 4:00 PM EST

What this means:
- Your existing MFA devices are no longer active
- You'll need to set up MFA again on your next login
- You can choose Guardian push or Passkey

If you didn't request this, please contact support immediately.

[Contact Support]
```

**Security Considerations:**
- Require elevated admin privileges for MFA reset
- Log IP address and user agent of admin performing reset
- Send notification to both user and admin's supervisor
- Rate limit MFA resets per user (max 3 per week)
- Flag accounts with multiple resets for review
- Require admin to verify user identity before reset
- Consider requiring admin MFA for this sensitive operation

## Dependencies

- US-AU-005 (Guardian MFA enrollment)
- US-AU-006 (Passkey MFA enrollment)
- Admin user management system

## Test Cases

1. **Admin Views User MFA Status**
   - Given admin navigates to user profile
   - When admin views security settings
   - Then current MFA methods are displayed

2. **Admin Initiates MFA Reset**
   - Given user has Guardian and Passkey enrolled
   - When admin clicks "Reset MFA" and provides reason
   - Then all MFA methods are cleared

3. **Confirmation Prompt**
   - Given admin clicks "Reset MFA"
   - When confirmation dialog appears
   - Then admin must confirm action and provide reason

4. **Missing Reset Reason**
   - Given admin attempts to reset MFA
   - When admin submits without reason
   - Then error shows "Reason is required"

5. **User Email Notification**
   - Given admin resets user's MFA
   - When reset completes
   - Then user receives email notification

6. **Recovery Codes Invalidated**
   - Given user has 10 recovery codes
   - When admin resets MFA
   - Then all recovery codes become invalid

7. **User Forced to Re-enroll on Login**
   - Given admin reset user's MFA
   - When user logs in with correct password
   - Then user is redirected to MFA enrollment page
   - And cannot access system until MFA re-enrolled

8. **User Cannot Bypass Re-enrollment**
   - Given MFA reset required
   - When user tries to access protected resources
   - Then user is redirected to MFA enrollment

9. **User Chooses Different MFA Method**
   - Given user previously used Guardian
   - When re-enrolling after reset
   - Then user can choose Passkey instead

10. **Audit Log Entry Created**
    - Given admin resets user's MFA
    - When reset completes
    - Then log entry includes admin ID, reason, timestamp, and previous methods

11. **Admin Lacks Permission**
    - Given admin without MFA reset permission
    - When admin attempts to reset MFA
    - Then request is denied with "Insufficient permissions"

12. **Multiple Resets Flagged**
    - Given user has 3 MFA resets in 7 days
    - When admin attempts 4th reset
    - Then warning appears "Multiple resets detected - verify user identity"

13. **View Reset History**
    - Given user had MFA reset 2 times
    - When admin views reset history
    - Then both reset events are displayed with dates and admins

## UI/UX (if applicable)

**User Profile - MFA Section (Admin View):**
```
Multi-Factor Authentication

Status: Enrolled
Methods:
  • Guardian Push (iPhone 12)
    Enrolled: Dec 15, 2024

  • Passkey (MacBook Pro Touch ID)
    Enrolled: Dec 20, 2024

Recovery Codes: 8 remaining

Last MFA Reset: Never

[Reset MFA]
```

**MFA Reset Confirmation Dialog:**
```
Reset Multi-Factor Authentication?

This will remove all MFA methods for user: john.doe

Current MFA methods:
  • Guardian Push (iPhone 12)
  • Passkey (MacBook Pro Touch ID)

The user will be required to re-enroll in MFA on their next login.

Reason for reset: *
[User reported lost device_____________]

[Cancel] [Reset MFA]

⚠️ This action will be logged for audit purposes.
```

**Success Message:**
```
✓ MFA Reset Successfully

User john.doe will be required to re-enroll in MFA on next login.

• Email notification sent to: john@example.com
• Reset logged for audit
• Recovery codes invalidated

[OK]
```

**MFA Reset History:**
```
MFA Reset History for john.doe

Dec 30, 2024 at 4:00 PM
  Reset by: admin.user@company.com
  Reason: User reported lost device
  Previous methods: Guardian, Passkey
  Re-enrolled: Dec 30, 2024 at 5:15 PM (Guardian)

Dec 1, 2024 at 2:30 PM
  Reset by: support.admin@company.com
  Reason: Security incident response
  Previous methods: Guardian
  Re-enrolled: Dec 1, 2024 at 3:00 PM (Passkey)
```

**User Login Flow After Reset:**
```
Multi-Factor Authentication Required

Your MFA has been reset by a system administrator.

Please set up MFA to continue accessing your account.

[Set Up MFA Now]

Questions? [Contact Support]
```

**Admin Audit Log:**
```
Recent MFA Reset Actions

Dec 30, 2024 4:00 PM | admin.user | Reset MFA for john.doe
  Reason: User reported lost device
  Methods removed: Guardian, Passkey

Dec 29, 2024 2:15 PM | admin.user | Reset MFA for jane.smith
  Reason: New device enrollment
  Methods removed: Passkey

[View All Audit Logs]
```

**Warning for Multiple Resets:**
```
⚠️ Multiple MFA Resets Detected

User john.doe has had 3 MFA resets in the past 7 days.

Please verify user identity before proceeding.

Recent resets:
  • Dec 30, 2024 by admin.user
  • Dec 28, 2024 by support.admin
  • Dec 26, 2024 by admin.user

[Cancel] [Verify and Continue]
```
