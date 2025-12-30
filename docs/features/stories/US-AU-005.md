# US-AU-005: MFA Enrollment - Guardian Push

## Story

**As a** new user
**I want** to enroll in multi-factor authentication using Auth0 Guardian push notifications
**So that** I can secure my account with convenient mobile device-based verification

## Acceptance Criteria

- [ ] MFA enrollment screen is shown immediately after password setup
- [ ] QR code is displayed for Guardian app pairing
- [ ] User can download Auth0 Guardian app via provided links (iOS/Android)
- [ ] QR code contains enrollment information encrypted by Auth0
- [ ] Alternative manual entry code is available for users unable to scan QR
- [ ] Test push notification is sent after enrollment
- [ ] User must successfully accept test push to complete enrollment
- [ ] Enrollment status is saved in user profile
- [ ] User cannot skip MFA enrollment (required for all users)
- [ ] Recovery codes are generated and displayed after successful enrollment
- [ ] User must acknowledge they've saved recovery codes

## Technical Notes

**Auth0 Guardian Configuration:**
- Enable Guardian push notifications in Auth0 Dashboard
- Configure Guardian enrollment policy to "required"
- Customize Guardian push notification messages
- Set push notification timeout to 60 seconds

**API Endpoints:**
```
POST /api/auth/mfa/guardian/enroll
- Request: { userId: string }
- Response: {
    enrollmentTicket: string,
    qrCodeUrl: string,
    manualEntryCode: string,
    enrollmentId: string
  }

POST /api/auth/mfa/guardian/verify
- Request: {
    enrollmentId: string,
    userId: string
  }
- Response: {
    verified: boolean,
    recoveryCodes: string[]
  }

GET /api/auth/mfa/guardian/status
- Request: { userId: string }
- Response: {
    enrolled: boolean,
    enrolledAt: timestamp,
    deviceName: string
  }
```

**Guardian Enrollment Flow:**
1. Backend requests enrollment ticket from Auth0
2. Generate QR code from ticket URL
3. Display QR code and manual entry code to user
4. User scans QR code with Guardian app
5. Guardian app completes pairing
6. Backend sends test push notification
7. User accepts push on mobile device
8. Backend verifies acceptance
9. Generate 10 recovery codes
10. Display recovery codes to user
11. User confirms they've saved codes
12. Mark enrollment as complete

**Database Changes:**
```sql
ALTER TABLE users ADD COLUMN mfa_guardian_enrolled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_guardian_enrolled_at TIMESTAMP;
ALTER TABLE users ADD COLUMN mfa_guardian_device_name VARCHAR(255);

CREATE TABLE mfa_recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    code_hash VARCHAR(255) NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_mfa_recovery_user ON mfa_recovery_codes(user_id);
```

**Recovery Codes:**
- Generate 10 single-use recovery codes
- Format: XXXX-XXXX-XXXX (12 characters, hyphen-separated)
- Store as bcrypt hash
- User must download or copy codes before proceeding

**Security Considerations:**
- QR codes expire after 15 minutes if not used
- Enrollment tickets are single-use
- Recovery codes must be stored securely by user
- Log all MFA enrollment events
- Monitor for repeated enrollment failures

## Dependencies

- US-AU-001 (User account exists)
- US-AU-002 (Registration initiated)
- US-AU-003 (Email verified)
- US-AU-004 (Password set)

## Test Cases

1. **Display QR Code After Password Setup**
   - Given user completes password setup
   - When navigating to MFA enrollment
   - Then QR code is displayed with Guardian setup instructions

2. **Successful Guardian Enrollment**
   - Given QR code is displayed
   - When user scans code with Guardian app
   - Then app is paired and ready to receive push notifications

3. **Test Push Notification**
   - Given Guardian app is paired
   - When system sends test push
   - Then user receives push notification on mobile device

4. **Accept Test Push**
   - Given user receives test push notification
   - When user taps "Accept" in Guardian app
   - Then backend verifies acceptance and completes enrollment

5. **Decline Test Push**
   - Given user receives test push notification
   - When user taps "Deny" in Guardian app
   - Then enrollment fails with option to retry

6. **Test Push Timeout**
   - Given test push notification sent
   - When user doesn't respond within 60 seconds
   - Then enrollment fails with "Request timed out" message

7. **Recovery Codes Generation**
   - Given user successfully completes test push
   - When enrollment finalizes
   - Then 10 unique recovery codes are generated and displayed

8. **User Must Acknowledge Recovery Codes**
   - Given recovery codes are displayed
   - When user attempts to proceed without checking confirmation
   - Then system prevents proceeding until acknowledged

9. **Manual Entry Code Alternative**
   - Given user cannot scan QR code
   - When user clicks "Can't scan QR code?"
   - Then manual entry code is displayed for manual Guardian setup

10. **QR Code Expiration**
    - Given QR code generated 16 minutes ago
    - When user attempts to scan code
    - Then enrollment fails and new QR code must be generated

## UI/UX (if applicable)

**MFA Enrollment Page:**
```
Secure Your Account with Multi-Factor Authentication

Step 1: Download Auth0 Guardian

[App Store Icon] Download for iOS
[Google Play Icon] Download for Android

Step 2: Scan QR Code

Open the Guardian app and scan this QR code:

[QR CODE IMAGE]

Can't scan the code? [Use manual entry code]

Step 3: Accept Test Notification

After scanning, you'll receive a test push notification.
Tap "Accept" to complete setup.

Waiting for confirmation... (60 seconds)

Need help? [View setup guide]
```

**Manual Entry Code Display:**
```
Manual Entry Code

If you can't scan the QR code, enter this code in Guardian:

ABCD-EFGH-IJKL-MNOP

This code expires in 15 minutes.

[Back to QR Code]
```

**Recovery Codes Display:**
```
Save Your Recovery Codes

These codes can be used to access your account if you lose your mobile device.
Each code can only be used once.

1. XXXX-XXXX-XXXX
2. XXXX-XXXX-XXXX
3. XXXX-XXXX-XXXX
4. XXXX-XXXX-XXXX
5. XXXX-XXXX-XXXX
6. XXXX-XXXX-XXXX
7. XXXX-XXXX-XXXX
8. XXXX-XXXX-XXXX
9. XXXX-XXXX-XXXX
10. XXXX-XXXX-XXXX

[Download Codes] [Copy to Clipboard] [Print]

☐ I have saved these codes in a secure location

[Complete Setup]
```

**Success Message:**
```
✓ MFA Setup Complete!

Your account is now protected with Guardian push notifications.

You'll be asked to approve a push notification each time you sign in.

[Continue to Dashboard]
```

**Error States:**
```
❌ Push notification denied. Please try again.

❌ Request timed out. No response received within 60 seconds.

❌ QR code expired. Please refresh to generate a new code.

❌ Enrollment failed. Please try again or contact support.
```
