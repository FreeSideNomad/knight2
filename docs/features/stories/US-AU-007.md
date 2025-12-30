# US-AU-007: Login Flow

## Story

**As a** registered user
**I want** to log in with my login ID, password, and MFA
**So that** I can securely access my account and the platform's features

## Acceptance Criteria

- [ ] Login page displays login ID and password fields
- [ ] User enters login ID (not email)
- [ ] User enters password
- [ ] System validates credentials against Auth0
- [ ] After successful password validation, MFA challenge is presented
- [ ] User completes MFA (Guardian push or passkey)
- [ ] Session is created upon successful MFA
- [ ] Access token and refresh token are issued
- [ ] User is redirected to dashboard or intended destination
- [ ] Failed login attempts are logged
- [ ] Account is locked after 5 failed password attempts
- [ ] Clear error messages for invalid credentials
- [ ] "Remember this device" option for trusted devices (optional MFA skip for 30 days)
- [ ] Support for "Forgot Password?" link
- [ ] Support for recovery code login if MFA unavailable

## Technical Notes

**Auth0 Login Flow:**
- Use Auth0 Universal Login or custom login page
- Flow: Resource Owner Password Grant with MFA
- Steps:
  1. POST to /oauth/token with username/password
  2. Receive mfa_token if MFA required
  3. POST to /oauth/token with mfa_token and MFA proof
  4. Receive access_token and refresh_token

**API Endpoints:**
```
POST /api/auth/login
- Request: {
    loginId: string,
    password: string,
    deviceId?: string
  }
- Response: {
    requiresMfa: boolean,
    mfaToken: string,
    availableMethods: ["guardian", "passkey", "recovery_code"]
  }

POST /api/auth/mfa/challenge
- Request: {
    mfaToken: string,
    method: "guardian" | "passkey" | "recovery_code"
  }
- Response: {
    challenge: string, // for passkey
    pushSent: boolean, // for guardian
    recoveryCodeRequired: boolean // for recovery_code
  }

POST /api/auth/mfa/verify
- Request: {
    mfaToken: string,
    method: string,
    proof: string | object // OTP, passkey assertion, or recovery code
  }
- Response: {
    accessToken: string,
    refreshToken: string,
    expiresIn: number,
    user: object
  }

POST /api/auth/logout
- Request: { refreshToken: string }
- Response: { success: boolean }
```

**Session Management:**
- Access token: JWT, 30 minute expiration
- Refresh token: Opaque token, 8 hour expiration
- Store tokens securely (httpOnly cookies or secure storage)
- CSRF protection enabled

**Database Changes:**
```sql
CREATE TABLE login_attempts (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    login_id VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN,
    failure_reason VARCHAR(100),
    mfa_method VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_login_attempts_user ON login_attempts(user_id);
CREATE INDEX idx_login_attempts_ip ON login_attempts(ip_address);
CREATE INDEX idx_login_attempts_created ON login_attempts(created_at);

CREATE TABLE trusted_devices (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    device_id VARCHAR(255) UNIQUE,
    device_name VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    trusted_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_trusted_devices_user ON trusted_devices(user_id);
CREATE INDEX idx_trusted_devices_device ON trusted_devices(device_id);

ALTER TABLE users ADD COLUMN account_locked_until TIMESTAMP;
ALTER TABLE users ADD COLUMN failed_login_count INT DEFAULT 0;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN last_login_ip VARCHAR(45);
```

**Account Lockout Policy:**
- 5 failed password attempts within 15 minutes
- Account locked for 30 minutes
- Admin can manually unlock
- Email notification sent to user on lockout

**Trusted Devices:**
- Device fingerprint: combination of IP, user agent, browser features
- Device ID stored in cookie
- Trust expires after 30 days
- User can revoke trusted devices from settings

**Security Logging:**
- Log all login attempts (success/failure)
- Log MFA completions
- Log token issuance
- Log suspicious patterns (geolocation changes, velocity attacks)

## Dependencies

- US-AU-001 (Login ID exists)
- US-AU-004 (Password set)
- US-AU-005 OR US-AU-006 (MFA enrolled)
- US-AU-011 (Session management)

## Test Cases

1. **Successful Login - Guardian MFA**
   - Given registered user with Guardian MFA
   - When user enters correct login ID and password
   - Then Guardian push is sent and user accepts
   - Then session is created and user redirected to dashboard

2. **Successful Login - Passkey MFA**
   - Given registered user with passkey MFA
   - When user enters correct login ID and password
   - Then passkey challenge is presented
   - When user authenticates with biometric
   - Then session is created and user redirected to dashboard

3. **Invalid Login ID**
   - Given user enters non-existent login ID
   - When user submits login form
   - Then error shows "Invalid login ID or password"

4. **Invalid Password**
   - Given user enters correct login ID but wrong password
   - When user submits login form
   - Then error shows "Invalid login ID or password"
   - And failed attempt is logged

5. **Account Lockout After 5 Failures**
   - Given user failed login 4 times in past 10 minutes
   - When user fails 5th login attempt
   - Then account is locked for 30 minutes
   - And error shows "Account locked due to too many failed attempts"
   - And email notification is sent

6. **MFA Challenge Timeout**
   - Given user passed password check and MFA challenge sent
   - When 5 minutes pass without MFA completion
   - Then MFA challenge expires
   - And user must restart login

7. **Guardian Push Denied**
   - Given user passes password check
   - When Guardian push is sent and user denies it
   - Then login fails with "MFA verification denied"

8. **Recovery Code Login**
   - Given user lost access to MFA device
   - When user clicks "Use recovery code instead"
   - And enters valid recovery code
   - Then login succeeds and recovery code is marked as used

9. **Remember This Device**
   - Given user checks "Remember this device for 30 days"
   - When user logs in successfully
   - Then device is marked as trusted
   - And future logins skip MFA for 30 days

10. **Trusted Device MFA Skip**
    - Given user logged in from trusted device 5 days ago
    - When user logs in with same device
    - Then only password is required (MFA skipped)
    - And session is created

11. **Expired Trusted Device**
    - Given device trusted 31 days ago
    - When user logs in
    - Then MFA is required again

12. **Redirect to Intended Destination**
    - Given user tries to access /reports without session
    - When user completes login flow
    - Then user is redirected to /reports (not dashboard)

## UI/UX (if applicable)

**Login Page:**
```
Sign In to Knight Platform

Login ID *
[________________]

Password *
[________________]
[Show/Hide]

☐ Remember this device for 30 days

[Sign In]

[Forgot Password?]

────────────────
Don't have an account? Contact your administrator
```

**MFA Challenge Page (Guardian):**
```
Multi-Factor Authentication

We've sent a push notification to your Guardian app.

[Approve] the request on your mobile device to continue.

Waiting for approval... (5:00)

[Cancel]

Having trouble?
[Use recovery code instead]
[Resend push notification]
```

**MFA Challenge Page (Passkey):**
```
Multi-Factor Authentication

Verify your identity using your passkey.

[Authenticate with Passkey]

Your browser will prompt you to use biometrics or security key.

Having trouble?
[Use recovery code instead]
```

**Recovery Code Entry:**
```
Enter Recovery Code

Enter one of your backup recovery codes:

[____-____-____]

Each code can only be used once.

[Verify]

[Back to normal login]
```

**Error Messages:**
```
❌ Invalid login ID or password. Please try again.

❌ Account locked due to too many failed login attempts.
   Please try again in 30 minutes or contact support.

❌ MFA verification failed. Please try again.

❌ MFA verification denied. Login cancelled.

❌ Session expired. Please log in again.

❌ Invalid recovery code. Please try again.
   (X recovery codes remaining)
```

**Success States:**
```
✓ Signing you in...

✓ MFA verified successfully
```

**Account Lockout Email:**
```
Subject: Account Security Alert - Login Attempts

Your Knight Platform account has been temporarily locked due to multiple failed login attempts.

Account: john.doe
Time: Dec 30, 2024 at 2:45 PM EST
IP Address: 192.168.1.100

If this was you, your account will be unlocked in 30 minutes.

If this wasn't you, please contact support immediately.

[Contact Support]
```
