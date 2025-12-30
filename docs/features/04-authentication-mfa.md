# Authentication & MFA

## Overview

Authentication is handled through Auth0 with support for multiple MFA methods. This document covers the registration flow, authentication flow, MFA options, and password recovery.

## Auth0 Configuration

### User Profile Fields

| Field | Auth0 Location | Description |
|-------|---------------|-------------|
| `user_id` | `user_id` | Auth0 unique identifier |
| `email` | `email` | User's email address (for communications) |
| `username` | `username` | Login ID (separate from email) |
| `name` | `name` | Display name |
| `email_verified` | `email_verified` | Email verification status |
| `user_metadata` | `user_metadata` | Custom user data |
| `app_metadata` | `app_metadata` | Application-managed data |

### MFA Options

1. **Guardian Push** (Primary recommended)
   - Push notifications to Auth0 Guardian app
   - One-tap approval on mobile device

2. **Passkeys** (WebAuthn/FIDO2)
   - Biometric authentication (fingerprint, face)
   - Hardware security keys (YubiKey)
   - Platform authenticators (Windows Hello, Touch ID)

3. **TOTP** (Optional backup)
   - Time-based one-time passwords
   - Compatible with authenticator apps

---

## User Stories

### US-AU-001: Login ID Separate from Email

**As a** new user
**I want** my login ID to be different from my email
**So that** I can use a memorable username while receiving emails at my address

#### Acceptance Criteria

- [ ] Auth0 configured to use `username` field for login
- [ ] Email used for communications and verification only
- [ ] Login screen accepts login ID (not email)
- [ ] Both displayed in user profile

#### Auth0 Configuration

```json
{
  "connection": {
    "requires_username": true,
    "username_policy": "alphanumeric_underscore"
  }
}
```

---

### US-AU-002: New User Registration Email

**As a** newly created user
**I want** to receive a registration email
**So that** I can complete my account setup

#### Acceptance Criteria

- [ ] Email sent when user is created
- [ ] Email contains unique registration link
- [ ] Link expires after 7 days
- [ ] Email includes: welcome message, login ID, next steps
- [ ] Email template branded with organization

#### Email Content

```
Subject: Complete Your Account Registration

Hello [Name],

You've been invited to join [Organization] on the Knight Platform.

Your Login ID: [login_id]

Click the link below to complete your registration:
[Registration Link]

This link will expire in 7 days.

If you did not expect this email, please ignore it.
```

---

### US-AU-003: Email Verification via OTP

**As a** new user completing registration
**I want** to verify my email using an OTP
**So that** the system confirms I have access to the registered email

#### Acceptance Criteria

- [ ] After entering login ID, OTP sent to email
- [ ] OTP valid for 10 minutes
- [ ] 6-digit numeric code
- [ ] Rate limit: max 5 OTP requests per hour
- [ ] Incorrect OTP: max 5 attempts before lockout
- [ ] `email_verified` set to true on success

#### Flow

```
┌─────────────────────────────────────────────────────────┐
│ Verify Your Email                                       │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ We've sent a verification code to:                      │
│ j***@example.com                                        │
│                                                         │
│ Enter the 6-digit code:                                 │
│ ┌───┬───┬───┬───┬───┬───┐                               │
│ │   │   │   │   │   │   │                               │
│ └───┴───┴───┴───┴───┴───┘                               │
│                                                         │
│ Didn't receive it? [Resend Code]                        │
│                                                         │
│                              [Verify]                   │
└─────────────────────────────────────────────────────────┘
```

---

### US-AU-004: Password Setup

**As a** new user
**I want** to set my password after email verification
**So that** I can secure my account

#### Acceptance Criteria

- [ ] Password form shown after email verification
- [ ] Password complexity requirements displayed
- [ ] Real-time strength indicator
- [ ] Confirm password field
- [ ] Submit only enabled when requirements met

#### Password Requirements

- Minimum 12 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one number
- At least one special character
- Not in common password list
- Not similar to email or name

---

### US-AU-005: MFA Enrollment - Guardian Push

**As a** new user
**I want** to enroll in Guardian push MFA
**So that** I can securely authenticate with my mobile device

#### Acceptance Criteria

- [ ] QR code displayed for Guardian app enrollment
- [ ] Instructions for downloading Guardian app
- [ ] User scans QR code with Guardian app
- [ ] Test push notification sent to verify enrollment
- [ ] User approves test push to complete enrollment

#### Flow

```
┌─────────────────────────────────────────────────────────┐
│ Set Up Push Notification MFA                            │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ 1. Download the Auth0 Guardian app:                     │
│    [App Store]  [Google Play]                           │
│                                                         │
│ 2. Scan this QR code with the Guardian app:             │
│    ┌─────────────────┐                                  │
│    │  [QR Code]      │                                  │
│    │                 │                                  │
│    └─────────────────┘                                  │
│                                                         │
│ 3. Once scanned, we'll send a test notification         │
│                                                         │
│                              [I've Scanned the Code]    │
└─────────────────────────────────────────────────────────┘
```

---

### US-AU-006: MFA Enrollment - Passkey

**As a** new user
**I want** to enroll a passkey for MFA
**So that** I can authenticate using biometrics or a security key

#### Acceptance Criteria

- [ ] Passkey option presented during MFA enrollment
- [ ] WebAuthn flow initiated
- [ ] User prompted by browser/device for biometric or security key
- [ ] Passkey registered with Auth0
- [ ] Multiple passkeys can be enrolled

#### Flow

```
┌─────────────────────────────────────────────────────────┐
│ Set Up Passkey MFA                                      │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Passkeys let you sign in with your fingerprint, face,   │
│ or security key.                                        │
│                                                         │
│ ┌───────────────────────────────────────────────────┐   │
│ │  [Fingerprint Icon]                               │   │
│ │  Use your device's built-in authentication       │   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│ ┌───────────────────────────────────────────────────┐   │
│ │  [Key Icon]                                       │   │
│ │  Use a security key (YubiKey, etc.)              │   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│                              [Set Up Passkey]           │
└─────────────────────────────────────────────────────────┘
```

---

### US-AU-007: Login Flow

**As a** registered user
**I want** to log in with my credentials and MFA
**So that** I can access the system securely

#### Acceptance Criteria

- [ ] Login form accepts Login ID and password
- [ ] "Forgot Password" link available
- [ ] After valid credentials, MFA challenge presented
- [ ] User completes MFA (push approval or passkey)
- [ ] Session established on success
- [ ] `last_logged_in_at` updated in database

#### Flow

```
Login ID + Password → MFA Challenge → Dashboard
         ↓
   Forgot Password
```

---

### US-AU-008: Forgot Password Flow

**As a** user who forgot their password
**I want** to reset my password via email
**So that** I can regain access to my account

#### Acceptance Criteria

- [ ] "Forgot Password" link on login screen
- [ ] User enters Login ID or email
- [ ] OTP sent to registered email
- [ ] User enters OTP to verify identity
- [ ] User sets new password (same requirements as initial)
- [ ] MFA not required for password reset (already verified via email OTP)
- [ ] Event logged: `USER_PASSWORD_RESET`

#### Flow

```
┌─────────────────────────────────────────────────────────┐
│ Reset Your Password                                     │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Enter your Login ID or email:                           │
│ ┌───────────────────────────────────────────────────┐   │
│ │                                                   │   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│ We'll send a verification code to your email.          │
│                                                         │
│                              [Send Code]                │
└─────────────────────────────────────────────────────────┘

          ↓ (OTP sent to email)

┌─────────────────────────────────────────────────────────┐
│ Enter Verification Code                                 │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Code sent to: j***@example.com                          │
│                                                         │
│ ┌───┬───┬───┬───┬───┬───┐                               │
│ │   │   │   │   │   │   │                               │
│ └───┴───┴───┴───┴───┴───┘                               │
│                                                         │
│                              [Verify]                   │
└─────────────────────────────────────────────────────────┘

          ↓ (OTP verified)

┌─────────────────────────────────────────────────────────┐
│ Set New Password                                        │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ New Password:                                           │
│ ┌───────────────────────────────────────────────────┐   │
│ │ ••••••••••••                                      │   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│ Confirm Password:                                       │
│ ┌───────────────────────────────────────────────────┐   │
│ │ ••••••••••••                                      │   │
│ └───────────────────────────────────────────────────┘   │
│                                                         │
│                              [Reset Password]           │
└─────────────────────────────────────────────────────────┘
```

---

### US-AU-009: Re-enroll MFA (Admin Initiated)

**As a** Client Administrator
**I want** to force a user to re-enroll MFA
**So that** I can help users who lost access to their MFA device

#### Acceptance Criteria

- [ ] "Re-enroll MFA" action in user management
- [ ] Confirmation dialog explains implications
- [ ] Action removes all MFA enrollments from Auth0
- [ ] User must enroll MFA on next login
- [ ] Event logged: `USER_MFA_RESET`
- [ ] User notified via email

#### Auth0 Implementation

```javascript
// Auth0 Management API
await auth0.users.deleteMultifactorProvider({
  id: userId,
  provider: 'guardian'
});

// Set flag for re-enrollment
await auth0.users.update({
  id: userId,
  user_metadata: { mfa_reset_required: true }
});
```

---

### US-AU-010: Track Email Verification

**As a** system
**I want** to track when a user verified their email
**So that** I have an audit trail of identity verification

#### Acceptance Criteria

- [ ] `email_verified` boolean stored in user record
- [ ] `email_verified_at` timestamp recorded
- [ ] Event published: `USER_EMAIL_VERIFIED`
- [ ] Status visible in user management UI

---

### US-AU-011: Session Management

**As a** security administrator
**I want** proper session management
**So that** user sessions are secure

#### Acceptance Criteria

- [ ] Session timeout after 30 minutes of inactivity
- [ ] Absolute session timeout after 8 hours
- [ ] Logout terminates session
- [ ] Sessions invalidated on password change
- [ ] Sessions invalidated on MFA reset

---

### US-AU-012: Step-Up Authentication

**As a** user performing a sensitive action
**I want** to be prompted for additional authentication
**So that** critical operations require fresh verification

#### Acceptance Criteria

- [ ] Step-up required for: permission changes, password reset, MFA changes
- [ ] User prompted for MFA even if recently authenticated
- [ ] Step-up token has short expiry (5 minutes)
- [ ] RAR (Rich Authorization Request) message shown

---

## Auth0 Actions

### Post-Login Action

```javascript
exports.onExecutePostLogin = async (event, api) => {
  // Update last login timestamp
  const userId = event.user.user_id;
  await updateLastLogin(userId);

  // Check if MFA reset required
  if (event.user.user_metadata?.mfa_reset_required) {
    if (event.authentication.methods.find(m => m.name === 'mfa')) {
      // MFA just enrolled, clear flag
      api.user.setUserMetadata({ mfa_reset_required: false });
    }
  }
};
```

### Pre-User Registration Action

```javascript
exports.onExecutePreUserRegistration = async (event, api) => {
  // Validate login ID uniqueness
  // Set initial metadata
  api.user.setUserMetadata({
    registration_initiated: new Date().toISOString()
  });

  api.user.setAppMetadata({
    status: 'PENDING_VERIFICATION'
  });
};
```

---

## Events

| Event | Trigger | Data |
|-------|---------|------|
| `USER_LOGIN` | Successful login | user_id, timestamp, ip |
| `USER_LOGIN_FAILED` | Failed login | user_id, timestamp, reason |
| `USER_PASSWORD_SET` | Password created/changed | user_id, timestamp |
| `USER_PASSWORD_RESET` | Password reset via forgot flow | user_id, timestamp |
| `USER_EMAIL_VERIFIED` | Email OTP verified | user_id, timestamp |
| `USER_MFA_ENROLLED` | MFA enrollment complete | user_id, method, timestamp |
| `USER_MFA_RESET` | MFA cleared by admin | user_id, admin_id, timestamp |

---

## Security Considerations

1. **Rate Limiting**: OTP requests limited to prevent abuse
2. **Brute Force Protection**: Account lockout after failed attempts
3. **Secure Transport**: All authentication over HTTPS
4. **Token Security**: JWT tokens signed and validated
5. **Session Binding**: Sessions tied to IP/device fingerprint
6. **Audit Logging**: All authentication events logged
