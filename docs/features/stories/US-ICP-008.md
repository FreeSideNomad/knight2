# US-ICP-008: Forgot Password

## Story

**As an** Indirect Client User
**I want** to reset my password when I forget it
**So that** I can regain access to my account without contacting an administrator

## Acceptance Criteria

- [ ] "Forgot Password?" link appears on login page
- [ ] User enters their email address to initiate reset
- [ ] System sends OTP (One-Time Password) to user's email
- [ ] OTP is valid for 15 minutes
- [ ] User enters OTP to verify identity
- [ ] User can request a new OTP if expired (max 3 attempts per hour)
- [ ] After OTP verification, user enters new password
- [ ] New password must meet complexity requirements
- [ ] Password complexity: minimum 8 characters, uppercase, lowercase, number, special character
- [ ] New password cannot be the same as previous password
- [ ] Success message confirms password reset
- [ ] User is redirected to login page after successful reset
- [ ] All active sessions are invalidated after password change
- [ ] Password reset is logged in audit trail

## Technical Notes

**API Endpoints:**
- `POST /api/auth/forgot-password` - Initiate password reset
  - Request: `{ email: string }`
  - Response: `{ success: boolean, message: string }`
- `POST /api/auth/verify-otp` - Verify OTP code
  - Request: `{ email: string, otp: string }`
  - Response: `{ token: string, expiresIn: number }`
- `POST /api/auth/reset-password` - Set new password
  - Request: `{ token: string, newPassword: string }`
  - Response: `{ success: boolean, message: string }`
- `POST /api/auth/resend-otp` - Resend OTP
  - Request: `{ email: string }`
  - Response: `{ success: boolean, attemptsRemaining: number }`

**Auth0 Integration:**
- Use Auth0 password reset flow or custom implementation
- Store OTP in Auth0 user metadata or separate cache (Redis)
- Validate password against Auth0 password policy
- Update user password via Auth0 Management API
- Invalidate all refresh tokens after password change

**Database:**
- Store OTP attempts in cache (Redis) with TTL
- Key format: `password_reset:{email}:{timestamp}`
- Value: `{ otp: hashedOTP, attempts: number, expiresAt: timestamp }`
- Track reset attempts to prevent abuse
- Insert into `password_reset_history` table for audit

**OTP Generation:**
- Generate 6-digit numeric code
- Hash OTP before storing (bcrypt or similar)
- Set expiration to 15 minutes
- Limit to 3 OTP requests per hour per email

**Password Complexity Rules:**
- Minimum 8 characters
- At least one uppercase letter (A-Z)
- At least one lowercase letter (a-z)
- At least one number (0-9)
- At least one special character (!@#$%^&*)
- Cannot be same as previous password
- Cannot contain user's email or name

**Security:**
- Rate limiting: 5 reset attempts per email per hour
- OTP is single-use (invalidated after successful verification)
- Reset token is single-use and expires in 1 hour
- Log all password reset attempts (successful and failed)
- Send notification email when password is changed
- Implement CAPTCHA if too many failed attempts (optional)

**Email Notifications:**
1. OTP Email:
   - Subject: "Password Reset Code"
   - Body: 6-digit OTP code, expiration time, warning not to share
2. Password Changed Email:
   - Subject: "Your password has been changed"
   - Body: Confirmation, timestamp, contact info if not initiated by user

## Dependencies

- Auth0 password management
- Email service
- Redis or cache for OTP storage
- Session management for invalidation

## Test Cases

1. **Initiate Reset**: Click "Forgot Password?", enter email, and verify OTP sent
2. **Valid OTP**: Enter correct OTP and verify access to password reset form
3. **Invalid OTP**: Enter incorrect OTP and verify error message
4. **Expired OTP**: Wait 15 minutes and verify OTP is expired
5. **Resend OTP**: Request new OTP and verify previous one is invalidated
6. **OTP Rate Limit**: Request OTP 4 times in an hour and verify 4th attempt blocked
7. **Password Complexity**: Enter weak password and verify validation errors
8. **Same Password**: Enter current password as new password and verify error
9. **Successful Reset**: Complete flow and verify password changed, can log in
10. **Session Invalidation**: Reset password while logged in and verify session terminates
11. **Email Notifications**: Complete reset and verify both OTP and confirmation emails sent
12. **Invalid Email**: Enter non-existent email and verify generic message (don't reveal if email exists)
13. **Audit Log**: Complete reset and verify audit log entry created
14. **Reset Attempts**: Try 6 resets in an hour and verify rate limit enforced

## UI/UX

**Login Page - Forgot Password Link:**
```
Login to Indirect Client Portal
--------------------------------

Email
[____________________]

Password
[____________________]

[Forgot Password?]

[Login]
```

**Step 1: Enter Email:**
```
Reset Your Password
-------------------

Enter your email address and we'll send you a code to reset your password.

Email
[____________________]

[Cancel]  [Send Code]
```

**Step 2: Verify OTP:**
```
Enter Verification Code
-----------------------

We've sent a 6-digit code to:
john.smith@example.com

The code will expire in 15 minutes.

Verification Code
[___] [___] [___] [___] [___] [___]

Didn't receive the code?
[Resend Code]

[Back]  [Verify]
```

**Step 3: Set New Password:**
```
Create New Password
-------------------

Your new password must meet these requirements:
- At least 8 characters
- Include uppercase and lowercase letters
- Include at least one number
- Include at least one special character (!@#$%^&*)

New Password
[____________________]
[Show/Hide]

Confirm New Password
[____________________]
[Show/Hide]

Password Strength: [=========== ] Strong

[Back]  [Reset Password]
```

**Success Page:**
```
Password Reset Successful
-------------------------

Your password has been successfully reset.

You can now log in with your new password.

[Go to Login]
```

**Error Messages:**
- Invalid email: "If an account exists with this email, you will receive a verification code."
- Invalid OTP: "Invalid verification code. Please try again. {X} attempts remaining."
- Expired OTP: "This verification code has expired. Please request a new one."
- Rate limit: "Too many reset attempts. Please try again in {X} minutes."
- Weak password: "Password does not meet complexity requirements."
- Same password: "New password must be different from your current password."
- Passwords don't match: "Passwords do not match. Please try again."

**Email - OTP Code:**
```
Subject: Password Reset Code

Hello,

You requested to reset your password for your Indirect Client Portal account.

Your verification code is: 123456

This code will expire in 15 minutes.

If you did not request this reset, please ignore this email and contact
your administrator immediately.

Thank you,
Security Team
```

**Email - Password Changed:**
```
Subject: Your password has been changed

Hello,

Your password was successfully changed on 2025-12-30 at 10:30 AM UTC.

If you did not make this change, please contact your administrator
immediately at support@example.com.

Thank you,
Security Team
```
