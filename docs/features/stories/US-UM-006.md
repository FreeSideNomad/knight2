# US-UM-006: User Registration Flow

## Story

**As a** newly invited user
**I want** to complete my registration after receiving an invitation
**So that** I can set up my credentials and access the system securely

## Acceptance Criteria

- [ ] User receives invitation email with registration link
- [ ] Registration link expires after 7 days
- [ ] Registration flow includes: email OTP verification, password setup, MFA enrollment
- [ ] OTP is sent to user's email address
- [ ] OTP expires after 10 minutes
- [ ] Password must meet security requirements
- [ ] MFA enrollment supports authenticator app (TOTP)
- [ ] User status changes from PENDING_REGISTRATION to ACTIVE after completion
- [ ] User can log in immediately after completing registration
- [ ] Failed registration attempts are logged

## Technical Notes

**Registration Flow:**

1. **Email Verification (OTP)**
   - Click registration link in email
   - System validates token (not expired, not used)
   - Send 6-digit OTP to email
   - User enters OTP
   - Verify OTP (max 3 attempts)

2. **Password Setup**
   - User enters password and confirmation
   - Validate password requirements:
     - Minimum 12 characters
     - At least one uppercase letter
     - At least one lowercase letter
     - At least one number
     - At least one special character
   - Hash and store password in Auth0

3. **MFA Enrollment**
   - Display QR code for authenticator app
   - User scans QR code
   - User enters verification code
   - Verify code and enable MFA
   - Generate recovery codes (10 codes)
   - User must save recovery codes

4. **Completion**
   - Update user status to ACTIVE
   - Set `registered_at` timestamp
   - Mark registration token as used
   - Send welcome email
   - Redirect to login page

**API Endpoints:**
```
POST /api/auth/register/verify-token
POST /api/auth/register/send-otp
POST /api/auth/register/verify-otp
POST /api/auth/register/set-password
POST /api/auth/register/enroll-mfa
POST /api/auth/register/verify-mfa
POST /api/auth/register/complete
```

**Database Changes:**
- Add `registration_token` VARCHAR(255) to users table
- Add `registration_token_expires_at` TIMESTAMP to users table
- Add `registered_at` TIMESTAMP to users table
- Add `mfa_enabled` BOOLEAN to users table

## Dependencies

- US-UM-005: Create New User

## Test Cases

1. **Valid Registration Link**: Verify link opens registration page
2. **Expired Link**: Verify error message for expired registration token
3. **Used Link**: Verify error when token has already been used
4. **OTP Sent**: Verify OTP is sent to user's email
5. **Valid OTP**: Verify registration proceeds with valid OTP
6. **Invalid OTP**: Verify error message for invalid OTP
7. **OTP Expiry**: Verify OTP expires after 10 minutes
8. **OTP Retry Limit**: Verify max 3 OTP attempts
9. **Password Validation**: Verify all password requirements are enforced
10. **Password Mismatch**: Verify error when passwords don't match
11. **MFA QR Code**: Verify QR code is displayed and scannable
12. **MFA Verification**: Verify MFA code validation works
13. **Recovery Codes**: Verify recovery codes are generated and displayed
14. **Registration Completion**: Verify user status changes to ACTIVE
15. **Welcome Email**: Verify welcome email is sent after completion
16. **Login After Registration**: Verify user can log in with new credentials

## UI/UX (if applicable)

**Step 1: Email Verification**
```
┌─────────────────────────────────────────┐
│        Complete Your Registration       │
├─────────────────────────────────────────┤
│                                         │
│ Welcome, John Doe!                      │
│                                         │
│ We've sent a verification code to:     │
│ john.doe@example.com                    │
│                                         │
│ Enter the 6-digit code:                │
│ [___] [___] [___] [___] [___] [___]    │
│                                         │
│ Code expires in 9:45                    │
│                                         │
│ Didn't receive the code?               │
│ [Resend Code]                           │
│                                         │
│              [Continue]                 │
└─────────────────────────────────────────┘
```

**Step 2: Set Password**
```
┌─────────────────────────────────────────┐
│           Set Your Password             │
├─────────────────────────────────────────┤
│                                         │
│ Create a strong password                │
│                                         │
│ Password                                │
│ [____________________________] [👁]     │
│                                         │
│ Confirm Password                        │
│ [____________________________] [👁]     │
│                                         │
│ Password Requirements:                  │
│ ✓ At least 12 characters                │
│ ✓ Uppercase letter                      │
│ ✓ Lowercase letter                      │
│ ✓ Number                                │
│ ✗ Special character                     │
│                                         │
│              [Continue]                 │
└─────────────────────────────────────────┘
```

**Step 3: Set Up MFA**
```
┌─────────────────────────────────────────┐
│      Set Up Two-Factor Authentication   │
├─────────────────────────────────────────┤
│                                         │
│ Scan this QR code with your            │
│ authenticator app:                      │
│                                         │
│        ┌─────────────┐                  │
│        │             │                  │
│        │  QR  CODE   │                  │
│        │             │                  │
│        └─────────────┘                  │
│                                         │
│ Can't scan? Use this code:              │
│ ABCD EFGH IJKL MNOP                     │
│                                         │
│ Enter the 6-digit code from your app:  │
│ [___] [___] [___] [___] [___] [___]    │
│                                         │
│              [Verify]                   │
└─────────────────────────────────────────┘
```

**Step 4: Recovery Codes**
```
┌─────────────────────────────────────────┐
│         Save Your Recovery Codes        │
├─────────────────────────────────────────┤
│                                         │
│ Keep these codes in a safe place.      │
│ You can use them to access your account│
│ if you lose your authenticator device. │
│                                         │
│ 1234-5678-9012    6789-0123-4567       │
│ 2345-6789-0123    7890-1234-5678       │
│ 3456-7890-1234    8901-2345-6789       │
│ 4567-8901-2345    9012-3456-7890       │
│ 5678-9012-3456    0123-4567-8901       │
│                                         │
│ ☐ I have saved these codes              │
│                                         │
│ [Download]  [Print]  [Complete Setup]  │
└─────────────────────────────────────────┘
```

**Progress Indicator:**
```
[1] Email → [2] Password → [3] MFA → [4] Recovery Codes
   ✓           ✓             ▶          ○
```
