# US-AU-008: Forgot Password Flow

## Story

**As a** user who forgot their password
**I want** to reset my password securely
**So that** I can regain access to my account without administrator intervention

## Acceptance Criteria

- [ ] "Forgot Password?" link is visible on login page
- [ ] User can enter either login ID or email address
- [ ] System sends password reset email to verified email
- [ ] Email contains secure reset link with time-limited token
- [ ] Reset link expires after 1 hour
- [ ] User verifies identity via OTP sent to email
- [ ] OTP is 6 digits and valid for 10 minutes
- [ ] After OTP verification, user can set new password
- [ ] New password must meet complexity requirements
- [ ] New password cannot be same as current password
- [ ] Password is updated in Auth0
- [ ] All existing sessions are invalidated upon password change
- [ ] User receives confirmation email after successful reset
- [ ] Rate limiting prevents abuse (max 3 requests per hour)
- [ ] Process works even if MFA device is unavailable

## Technical Notes

**Auth0 Configuration:**
- Use Auth0 Change Password API
- Configure custom password reset email template
- Set reset token TTL to 3600 seconds (1 hour)
- Enable password history to prevent reuse

**Password Reset Flow:**
1. User requests password reset with login ID or email
2. System looks up user account
3. Send OTP to user's verified email
4. User enters OTP to verify identity
5. Upon OTP verification, display password reset form
6. User sets new password
7. Validate password meets requirements and not in history
8. Update password in Auth0
9. Invalidate all refresh tokens
10. Send confirmation email
11. Redirect to login page

**API Endpoints:**
```
POST /api/auth/password/forgot
- Request: {
    identifier: string // login ID or email
  }
- Response: {
    otpSent: boolean,
    email: string, // masked (j***@example.com)
    expiresIn: number
  }

POST /api/auth/password/verify-otp
- Request: {
    identifier: string,
    otp: string
  }
- Response: {
    verified: boolean,
    resetToken: string,
    expiresAt: timestamp
  }

POST /api/auth/password/reset
- Request: {
    resetToken: string,
    newPassword: string,
    confirmPassword: string
  }
- Response: {
    success: boolean,
    message: string
  }
```

**Database Changes:**
```sql
CREATE TABLE password_reset_attempts (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    identifier VARCHAR(255), -- login ID or email used
    otp_hash VARCHAR(255),
    reset_token VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    otp_verified BOOLEAN DEFAULT FALSE,
    password_reset BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_password_reset_user ON password_reset_attempts(user_id);
CREATE INDEX idx_password_reset_token ON password_reset_attempts(reset_token);
CREATE INDEX idx_password_reset_expires ON password_reset_attempts(expires_at);

ALTER TABLE users ADD COLUMN password_reset_count INT DEFAULT 0;
ALTER TABLE users ADD COLUMN last_password_reset_at TIMESTAMP;
```

**Security Measures:**
- Rate limiting: 3 password reset requests per email per hour
- OTP rate limiting: 5 attempts per reset request
- All password reset activity logged
- Email notifications for successful resets
- Suspicious activity monitoring (multiple IPs, rapid requests)
- CAPTCHA after 2 failed reset attempts
- Reset tokens are single-use
- Constant-time responses to prevent user enumeration

**Email Templates:**

*Password Reset Request Email:*
```
Subject: Password Reset Request

We received a request to reset your password.

Your verification code is: 123456

This code will expire in 10 minutes.

If you didn't request this, please ignore this email.

---
Knight Platform Team
```

*Password Reset Confirmation Email:*
```
Subject: Password Changed Successfully

Your Knight Platform password has been changed successfully.

Time: Dec 30, 2024 at 3:15 PM EST
IP Address: 192.168.1.100

If you didn't make this change, please contact support immediately.

[Contact Support]
```

## Dependencies

- US-AU-001 (Login ID exists)
- US-AU-003 (Email verification OTP system)
- US-AU-004 (Password complexity requirements)
- US-AU-010 (Email must be verified)

## Test Cases

1. **Successful Password Reset with Login ID**
   - Given user enters valid login ID
   - When password reset is requested
   - Then OTP is sent to associated email

2. **Successful Password Reset with Email**
   - Given user enters valid email
   - When password reset is requested
   - Then OTP is sent to that email

3. **OTP Verification**
   - Given user receives OTP "123456"
   - When user enters correct OTP
   - Then password reset form is displayed

4. **Invalid OTP**
   - Given user receives OTP "123456"
   - When user enters "654321"
   - Then error shows "Invalid verification code"

5. **OTP Expiration**
   - Given OTP sent 11 minutes ago
   - When user enters OTP
   - Then error shows "Verification code expired"

6. **New Password Same as Current**
   - Given user verified OTP
   - When user enters current password as new password
   - Then error shows "New password must be different from current password"

7. **New Password Meets Requirements**
   - Given user verified OTP
   - When user enters "NewSecureP@ss123"
   - Then password is updated successfully

8. **All Sessions Invalidated**
   - Given user has active sessions on 2 devices
   - When password is reset
   - Then both sessions are invalidated
   - And user must log in again on both devices

9. **Rate Limiting on Reset Requests**
   - Given user requested reset 3 times in past hour
   - When user requests 4th reset
   - Then request is blocked with "Too many requests"

10. **User Enumeration Protection**
    - Given non-existent login ID entered
    - When reset is requested
    - Then response is identical to successful request (no user enumeration)

11. **Reset Token Single Use**
    - Given user completes password reset with token
    - When user tries to use same token again
    - Then error shows "Reset link already used"

12. **Reset Token Expiration**
    - Given reset token created 61 minutes ago
    - When user accesses reset link
    - Then error shows "Reset link expired"

## UI/UX (if applicable)

**Forgot Password Page:**
```
Reset Your Password

Enter your Login ID or Email Address:

[________________]

[Continue]

[Back to Sign In]

Need help? [Contact Support]
```

**OTP Verification Page:**
```
Verify Your Identity

We've sent a verification code to j***@example.com

Enter your verification code:
[___] [___] [___] [___] [___] [___]

Code expires in: 9:45

[Verify]

Didn't receive the code?
[Resend Code] (Available in 0:30)

Attempts remaining: 5/5
```

**New Password Entry:**
```
Create New Password

Password *
[________________]
[Show/Hide]

Password strength: ████████░░ Good

Confirm Password *
[________________]

Password requirements:
✓ At least 8 characters
✓ One uppercase letter
✓ One lowercase letter
✓ One number
✗ One special character

[Reset Password]
```

**Success Page:**
```
✓ Password Reset Successful

Your password has been changed successfully.

For your security, you've been signed out of all devices.

[Sign In Now]
```

**Error Messages:**
```
❌ Invalid verification code. Please try again.
   (4 attempts remaining)

❌ Verification code expired. Please request a new code.

❌ Reset link has expired. Please start the process again.

❌ Reset link already used. Please request a new password reset.

❌ New password must be different from your current password.

❌ Too many password reset requests. Please try again in 45 minutes.

❌ Password must meet the complexity requirements shown above.
```

**Email Masking Examples:**
```
j***@example.com         (john@example.com)
j***.d***@example.com    (john.doe@example.com)
j***@e***.com            (john@ex.com)
```

**Confirmation Email:**
```
Subject: Password Changed Successfully

Your password for Knight Platform has been changed.

Account: john.doe
Time: Dec 30, 2024 at 3:15 PM EST
IP Address: 192.168.1.100
Location: New York, USA (approximate)

If you made this change, no action is needed.

If you didn't make this change:
1. Your account may be compromised
2. Contact support immediately: support@knightplatform.com
3. We can help secure your account

[Contact Support] [Sign In to Account]

---
Knight Platform Security Team
```
