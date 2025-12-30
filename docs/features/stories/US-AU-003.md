# US-AU-003: Email Verification via OTP

## Story

**As a** new user
**I want** to verify my email address using a one-time password
**So that** I can prove I have access to the email address and prevent unauthorized account creation

## Acceptance Criteria

- [ ] System sends 6-digit numeric OTP to user's email
- [ ] OTP is valid for 10 minutes
- [ ] OTP can only be used once
- [ ] User has maximum 3 attempts to enter correct OTP
- [ ] After 3 failed attempts, new OTP must be requested
- [ ] User can request new OTP (invalidates previous OTP)
- [ ] Rate limiting: maximum 5 OTP requests per hour per email
- [ ] OTP email clearly states the code and expiration time
- [ ] System tracks verification attempts for security monitoring
- [ ] Verified status is permanently recorded in user profile

## Technical Notes

**Auth0 Configuration:**
- Use Auth0 Email OTP passwordless flow
- Configure custom OTP email template
- Set OTP length to 6 digits
- Set OTP expiration to 600 seconds (10 minutes)

**OTP Generation:**
- Use cryptographically secure random number generator
- Format: 6 digits (000000-999999)
- Avoid sequential or repeated patterns (111111, 123456)

**API Endpoints:**
```
POST /api/auth/email-verification/send-otp
- Request: { email: string }
- Response: { sent: boolean, expiresAt: timestamp, attemptsRemaining: number }

POST /api/auth/email-verification/verify-otp
- Request: { email: string, otp: string }
- Response: { verified: boolean, attemptsRemaining: number }

POST /api/auth/email-verification/resend-otp
- Request: { email: string }
- Response: { sent: boolean, expiresAt: timestamp }
```

**Database Schema:**
```sql
CREATE TABLE email_verification_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempts_count INT DEFAULT 0,
    verified_at TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT
);

CREATE INDEX idx_email_verification_email ON email_verification_attempts(email);
CREATE INDEX idx_email_verification_expires ON email_verification_attempts(expires_at);
```

**Security Measures:**
- Store OTP as bcrypt hash, not plaintext
- Log all verification attempts with IP and user agent
- Implement CAPTCHA after 2 failed OTP requests
- Monitor for suspicious patterns (multiple emails from same IP)
- Auto-lock after 10 failed attempts in 1 hour

**Rate Limiting:**
- 5 OTP requests per email per hour
- 10 verification attempts per IP per hour
- Use Redis for distributed rate limiting

## Dependencies

- US-AU-001 (User account must exist)
- US-AU-002 (Registration flow initiated)

## Test Cases

1. **Successful OTP Email Delivery**
   - Given user requests email verification
   - When OTP is generated
   - Then 6-digit code is sent to user's email within 30 seconds

2. **Valid OTP Verification**
   - Given user receives OTP "123456"
   - When user enters "123456" within 10 minutes
   - Then email is marked as verified

3. **OTP Expiration After 10 Minutes**
   - Given OTP generated 11 minutes ago
   - When user enters the OTP
   - Then verification fails with "OTP expired" error

4. **Maximum 3 Verification Attempts**
   - Given user enters wrong OTP 3 times
   - When user attempts 4th verification
   - Then system rejects with "Maximum attempts exceeded. Request new OTP"

5. **OTP Single Use**
   - Given user successfully verifies with OTP
   - When user enters same OTP again
   - Then system rejects with "OTP already used"

6. **Resend OTP Invalidates Previous**
   - Given user has OTP "123456"
   - When user requests new OTP "789012"
   - Then old OTP "123456" is no longer valid

7. **Rate Limiting on OTP Requests**
   - Given user requested OTP 5 times in past hour
   - When user requests 6th OTP
   - Then system blocks request with "Too many requests. Try again in X minutes"

8. **Case Insensitive OTP Entry**
   - Given numeric OTP "123456"
   - When user enters " 123456 " (with spaces)
   - Then system strips whitespace and verifies successfully

9. **Security Monitoring**
   - Given 10 failed attempts from same IP in 1 hour
   - When 11th attempt is made
   - Then IP is temporarily blocked for 1 hour

## UI/UX (if applicable)

**OTP Email Template:**
```
Subject: Verify Your Email Address

Your verification code is:

123456

This code will expire in 10 minutes.

If you didn't request this code, please ignore this email.

---
Knight Platform Team
```

**Verification Page:**
```
Verify Your Email Address

We've sent a 6-digit code to john@example.com

Enter your verification code:
[___] [___] [___] [___] [___] [___]

Code expires in: 9:45

Didn't receive the code?
[Resend Code] (Available in 0:30)

[Verify]

Attempts remaining: 3/3
```

**Error States:**
```
❌ Invalid code. Please try again. (2 attempts remaining)

❌ Code expired. Please request a new code.

❌ Too many attempts. Please request a new code.

❌ Too many requests. Please try again in 45 minutes.
```

**Success State:**
```
✓ Email Verified Successfully

Your email address has been verified. You can now proceed to set up your password.

[Continue to Password Setup]
```
