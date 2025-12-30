# US-AU-010: Track Email Verification

## Story

**As a** system administrator
**I want** to track whether a user's email has been verified and when
**So that** I can ensure users have verified email addresses before granting access and can audit email verification status

## Acceptance Criteria

- [ ] User table has `email_verified` boolean field (default: false)
- [ ] User table has `email_verified_at` timestamp field (nullable)
- [ ] Email verification status is synced with Auth0
- [ ] Email verification is set to true only after OTP verification
- [ ] Timestamp is recorded at the moment of successful verification
- [ ] Admin can view email verification status in user management
- [ ] System prevents unverified users from completing registration
- [ ] API endpoints return email verification status
- [ ] Email verification status appears in user profile
- [ ] Reports can filter by email verification status
- [ ] Audit logs track email verification events
- [ ] Email change triggers re-verification requirement

## Technical Notes

**Database Schema:**
```sql
ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP;
ALTER TABLE users ADD COLUMN email_verification_token VARCHAR(255);
ALTER TABLE users ADD COLUMN email_verification_expires_at TIMESTAMP;

CREATE INDEX idx_users_email_verified ON users(email_verified);
CREATE INDEX idx_users_email_verified_at ON users(email_verified_at);

CREATE TABLE email_verification_log (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    email VARCHAR(255) NOT NULL,
    verified BOOLEAN,
    verification_method VARCHAR(50), -- 'otp', 'link', 'admin_override'
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_email_verification_user ON email_verification_log(user_id);
CREATE INDEX idx_email_verification_created ON email_verification_log(created_at);
```

**Auth0 Synchronization:**
- Read `email_verified` from Auth0 user object
- Update Auth0 `email_verified` when verified locally
- Use Auth0 Management API to sync verification status
- Handle conflicts (prefer most recent verification)

**API Endpoints:**
```
GET /api/users/:userId/email-verification
- Request: { userId: string }
- Response: {
    emailVerified: boolean,
    verifiedAt: timestamp | null,
    email: string
  }

PATCH /api/users/:userId/email
- Request: {
    userId: string,
    newEmail: string
  }
- Response: {
    email: string,
    emailVerified: false, // reset to false
    verificationSent: boolean
  }

POST /api/admin/users/:userId/verify-email
- Request: {
    userId: string,
    adminId: string,
    reason: string
  }
- Response: {
    emailVerified: true,
    verifiedAt: timestamp,
    verifiedBy: "admin",
    reason: string
  }
```

**Email Verification Update Logic:**
```sql
-- On successful OTP verification
UPDATE users
SET
  email_verified = TRUE,
  email_verified_at = NOW()
WHERE id = :user_id;

INSERT INTO email_verification_log
  (user_id, email, verified, verification_method, ip_address, user_agent)
VALUES
  (:user_id, :email, TRUE, 'otp', :ip, :user_agent);
```

**Email Change Workflow:**
1. User requests email change
2. System sets `email_verified = false`
3. System clears `email_verified_at`
4. System sends OTP to new email
5. User verifies new email via OTP
6. System sets `email_verified = true`
7. System records new `email_verified_at` timestamp

**Access Control:**
- Unverified users cannot complete registration
- Unverified users cannot access protected resources
- Login allowed only for users with `email_verified = true`
- Admins can override and manually verify emails (logged)

**Reporting Queries:**
```sql
-- Users with unverified emails
SELECT id, login_id, email, created_at
FROM users
WHERE email_verified = FALSE
ORDER BY created_at DESC;

-- Recently verified emails (last 7 days)
SELECT id, login_id, email, email_verified_at
FROM users
WHERE email_verified_at > NOW() - INTERVAL '7 days'
ORDER BY email_verified_at DESC;

-- Users pending verification for > 7 days
SELECT id, login_id, email, created_at
FROM users
WHERE email_verified = FALSE
  AND created_at < NOW() - INTERVAL '7 days'
ORDER BY created_at ASC;
```

## Dependencies

- US-AU-001 (User account with email)
- US-AU-002 (Registration email)
- US-AU-003 (Email verification OTP)

## Test Cases

1. **Email Verification Sets Fields**
   - Given user verifies email with OTP
   - When verification succeeds
   - Then `email_verified` is set to true
   - And `email_verified_at` is set to current timestamp

2. **New User Has Unverified Email**
   - Given new user account created
   - When user record is created
   - Then `email_verified` is false
   - And `email_verified_at` is null

3. **Admin Views Verification Status**
   - Given admin views user profile
   - When profile loads
   - Then email verification status is displayed
   - And verification timestamp is shown if verified

4. **Unverified User Cannot Complete Registration**
   - Given user with `email_verified = false`
   - When user attempts to set password
   - Then system blocks with "Email must be verified first"

5. **Email Change Resets Verification**
   - Given user with verified email (verified Dec 1, 2024)
   - When user changes email address
   - Then `email_verified` is set to false
   - And `email_verified_at` is cleared
   - And new verification OTP is sent

6. **Re-verification After Email Change**
   - Given user changed email and `email_verified = false`
   - When user verifies new email
   - Then `email_verified` is set to true
   - And `email_verified_at` is set to new timestamp

7. **Verification Log Entry Created**
   - Given user verifies email
   - When verification completes
   - Then entry is created in `email_verification_log`
   - And entry includes IP, user agent, and timestamp

8. **Admin Manual Verification**
   - Given admin has override permission
   - When admin manually verifies user's email with reason
   - Then `email_verified` is set to true
   - And verification log shows "admin_override" method

9. **API Returns Verification Status**
   - Given API request for user profile
   - When response is returned
   - Then response includes `emailVerified` boolean
   - And `emailVerifiedAt` timestamp if applicable

10. **Report: Unverified Users**
    - Given 5 users with unverified emails
    - When admin runs unverified users report
    - Then all 5 users are listed
    - And sorted by registration date

11. **Auth0 Sync**
    - Given email verified in local database
    - When sync process runs
    - Then Auth0 user object `email_verified` is updated to true

12. **Multiple Verification Attempts Logged**
    - Given user attempts verification 3 times (2 fail, 1 succeeds)
    - When verification log is queried
    - Then all 3 attempts are recorded with success/failure status

## UI/UX (if applicable)

**User Profile (Admin View):**
```
Email Information

Email: john@example.com
Status: ✓ Verified
Verified: Dec 30, 2024 at 2:15 PM EST

[Change Email]
```

**User Profile (Unverified):**
```
Email Information

Email: john@example.com
Status: ⚠️ Not Verified
Registered: Dec 30, 2024 at 1:00 PM EST

[Resend Verification Email]
```

**User Management Table:**
```
Login ID      | Email              | Status      | Verified At
-----------------------------------------------------------------
john.doe      | john@example.com   | ✓ Verified  | Dec 30, 2024 2:15 PM
jane.smith    | jane@example.com   | ⚠️ Pending  | —
bob.jones     | bob@example.com    | ✓ Verified  | Dec 29, 2024 3:30 PM
```

**Email Change Form:**
```
Change Email Address

Current Email: john@example.com (Verified)

New Email *
[newjohn@example.com_____________]

⚠️ Changing your email will require re-verification.
    You'll need to verify the new email before you can use it.

[Update Email]
```

**Email Change Confirmation:**
```
Email Change Initiated

A verification code has been sent to:
newjohn@example.com

You must verify your new email address before it becomes active.

Your current email (john@example.com) will remain active until
verification is complete.

[Verify New Email Now]
```

**Admin Manual Verification:**
```
Manually Verify Email

User: john.doe
Email: john@example.com
Current Status: Not Verified

Reason for manual verification: *
[User verified via phone call________________]

⚠️ This action will be logged for audit purposes.

[Cancel] [Verify Email]
```

**Unverified Users Report:**
```
Users with Unverified Emails

Total: 15 users

Pending > 7 days (action required):
• john.doe | john@example.com | Registered: Dec 20, 2024
• jane.smith | jane@example.com | Registered: Dec 18, 2024

Pending < 7 days:
• bob.jones | bob@example.com | Registered: Dec 28, 2024
• alice.wong | alice@example.com | Registered: Dec 29, 2024

[Export CSV] [Send Reminder Emails]
```

**Verification Log (Admin Audit):**
```
Email Verification Log - john.doe

Dec 30, 2024 2:15 PM | ✓ Verified | Method: OTP
  IP: 192.168.1.100 | Browser: Chrome 120

Dec 30, 2024 2:10 PM | ✗ Failed | Method: OTP
  IP: 192.168.1.100 | Browser: Chrome 120
  Reason: Invalid OTP

Dec 30, 2024 2:05 PM | ✗ Failed | Method: OTP
  IP: 192.168.1.100 | Browser: Chrome 120
  Reason: Expired OTP
```

**Registration Progress Indicator:**
```
Complete Your Registration

1. ✓ Email Verification
   Verified Dec 30, 2024 at 2:15 PM

2. → Set Password
   Current step

3. ○ Multi-Factor Authentication

4. ○ Complete Profile
```
