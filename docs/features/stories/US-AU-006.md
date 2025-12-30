# US-AU-006: MFA Enrollment - Passkey

## Story

**As a** new user
**I want** to enroll in multi-factor authentication using a passkey (WebAuthn)
**So that** I can secure my account using biometrics or a hardware security key for passwordless or phishing-resistant authentication

## Acceptance Criteria

- [ ] User can choose passkey as MFA enrollment option
- [ ] System initiates WebAuthn registration ceremony
- [ ] User can register biometric (Face ID, Touch ID, Windows Hello) or hardware security key
- [ ] Passkey is associated with user's account
- [ ] User-friendly device name can be assigned to passkey
- [ ] System supports multiple passkeys per user
- [ ] Passkey attestation is validated
- [ ] Test authentication is performed to verify passkey works
- [ ] Recovery codes are generated after successful enrollment
- [ ] User is informed about passkey device requirements
- [ ] Enrollment works across browsers (Chrome, Safari, Firefox, Edge)

## Technical Notes

**WebAuthn Configuration:**
- Use Auth0 WebAuthn API or implement custom WebAuthn flow
- RP Name: "Knight Platform"
- RP ID: Domain name (e.g., "knight.com")
- User verification: "required"
- Attestation: "direct" for enterprise, "none" for consumer
- Authenticator attachment: "cross-platform" (allow USB keys) and "platform" (allow biometrics)

**API Endpoints:**
```
POST /api/auth/mfa/passkey/registration-options
- Request: { userId: string }
- Response: {
    challenge: string,
    rp: { name: string, id: string },
    user: { id: string, name: string, displayName: string },
    pubKeyCredParams: array,
    timeout: number,
    attestation: string
  }

POST /api/auth/mfa/passkey/register
- Request: {
    userId: string,
    credential: PublicKeyCredential,
    deviceName: string
  }
- Response: {
    credentialId: string,
    enrolled: boolean,
    recoveryCodes: string[]
  }

POST /api/auth/mfa/passkey/verification-options
- Request: { userId: string }
- Response: {
    challenge: string,
    allowCredentials: array,
    timeout: number
  }

POST /api/auth/mfa/passkey/verify
- Request: {
    userId: string,
    credential: PublicKeyCredential
  }
- Response: { verified: boolean }

GET /api/auth/mfa/passkey/list
- Request: { userId: string }
- Response: {
    passkeys: [
      { id: string, deviceName: string, createdAt: timestamp, lastUsed: timestamp }
    ]
  }

DELETE /api/auth/mfa/passkey/:credentialId
- Request: { userId: string }
- Response: { deleted: boolean }
```

**Database Schema:**
```sql
CREATE TABLE webauthn_credentials (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    credential_id TEXT UNIQUE NOT NULL,
    public_key TEXT NOT NULL,
    counter BIGINT DEFAULT 0,
    device_name VARCHAR(255),
    transports TEXT[], -- usb, nfc, ble, internal
    authenticator_attachment VARCHAR(50), -- platform, cross-platform
    created_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP
);

CREATE INDEX idx_webauthn_user ON webauthn_credentials(user_id);
CREATE INDEX idx_webauthn_credential ON webauthn_credentials(credential_id);

ALTER TABLE users ADD COLUMN mfa_passkey_enrolled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN mfa_passkey_enrolled_at TIMESTAMP;
```

**WebAuthn Flow:**
1. Frontend requests registration options from backend
2. Backend generates challenge and returns PublicKeyCredentialCreationOptions
3. Frontend calls `navigator.credentials.create()`
4. User interacts with authenticator (biometric/PIN/security key)
5. Browser returns PublicKeyCredential
6. Frontend sends credential to backend
7. Backend validates attestation and stores public key
8. Backend sends test verification challenge
9. User authenticates with passkey to verify it works
10. Backend confirms successful verification
11. Generate and display recovery codes

**Security Considerations:**
- Challenge must be cryptographically random (32+ bytes)
- Challenge expires after 5 minutes
- Validate origin matches RP ID
- Verify attestation signature
- Store public key, never private key
- Increment and verify counter to detect cloned authenticators
- Log all passkey operations for audit

**Browser Compatibility:**
- Chrome/Edge: Full WebAuthn support
- Safari: Full WebAuthn support (iOS 14+, macOS Big Sur+)
- Firefox: Full WebAuthn support
- Graceful fallback message for unsupported browsers

## Dependencies

- US-AU-001 (User account exists)
- US-AU-002 (Registration initiated)
- US-AU-003 (Email verified)
- US-AU-004 (Password set)

## Test Cases

1. **Display Passkey Option**
   - Given user reaches MFA enrollment page
   - When page loads
   - Then passkey option is available alongside Guardian

2. **Successful Biometric Registration (Face ID)**
   - Given user on macOS with Touch ID/Face ID
   - When user selects "Set up Passkey" and clicks register
   - Then Face ID prompt appears

3. **Successful Biometric Registration (Touch ID)**
   - Given user on device with Touch ID
   - When user scans fingerprint
   - Then passkey is registered successfully

4. **Successful Security Key Registration**
   - Given user has USB security key (YubiKey)
   - When user inserts key and taps it
   - Then passkey is registered successfully

5. **Device Name Assignment**
   - Given passkey registration successful
   - When user enters "MacBook Pro Touch ID"
   - Then device name is saved with passkey

6. **Test Authentication After Registration**
   - Given passkey just registered
   - When system sends test challenge
   - Then user authenticates and test passes

7. **Multiple Passkeys Support**
   - Given user already has one passkey registered
   - When user registers second passkey
   - Then both passkeys are active and listed

8. **Browser Not Supported**
   - Given user on old browser without WebAuthn support
   - When user attempts passkey enrollment
   - Then error message suggests using Guardian or updating browser

9. **User Cancels Biometric Prompt**
   - Given biometric prompt appears
   - When user clicks "Cancel"
   - Then enrollment fails gracefully with option to retry

10. **Recovery Codes After Passkey Enrollment**
    - Given user successfully enrolls passkey
    - When enrollment completes
    - Then 10 recovery codes are generated and displayed

11. **Challenge Timeout**
    - Given registration challenge generated
    - When 6 minutes pass without response
    - Then challenge expires and new one must be requested

12. **Remove Passkey**
    - Given user has multiple passkeys
    - When user removes one passkey
    - Then passkey is deleted and no longer usable

## UI/UX (if applicable)

**MFA Enrollment Choice:**
```
Choose Your Multi-Factor Authentication Method

Option 1: Passkey (Recommended)
✓ Fastest and most secure
✓ Use Face ID, Touch ID, or security key
✓ Phishing-resistant
[Set up Passkey]

Option 2: Guardian Push Notification
✓ Mobile app notifications
✓ Easy to use
[Set up Guardian]

What are passkeys? [Learn more]
```

**Passkey Registration Page:**
```
Set Up Passkey

Use your device's biometric authentication or a hardware security key.

Supported methods:
• Face ID / Touch ID (Mac, iPhone, iPad)
• Windows Hello (Windows PC)
• Fingerprint sensor (Android)
• Security keys (YubiKey, etc.)

[Register Passkey]

Your device must support WebAuthn/FIDO2.
```

**Browser Prompt (Chrome example):**
```
[Browser Native Dialog]

knight.com wants to create a passkey

Continue with:
○ This device (Touch ID)
○ iPhone, iPad, or Android device
○ USB security key

[Cancel] [Continue]
```

**Device Name Entry:**
```
Name Your Passkey

Give this passkey a memorable name:

[MacBook Pro Touch ID________]

This helps you identify it if you have multiple passkeys.

[Continue]
```

**Passkey List (Profile Settings):**
```
Your Passkeys

1. MacBook Pro Touch ID
   Created: Dec 30, 2024
   Last used: 2 hours ago
   [Remove]

2. YubiKey 5 NFC
   Created: Dec 25, 2024
   Last used: 5 days ago
   [Remove]

[Add Another Passkey]
```

**Test Authentication:**
```
Verify Your Passkey

Let's make sure your passkey works.

[Authenticate Now]

You'll be prompted to use your biometric or security key.
```

**Success Message:**
```
✓ Passkey Setup Complete!

Your passkey has been registered successfully.

Device: MacBook Pro Touch ID

You can use this passkey to sign in quickly and securely.

[Continue]
```

**Error States:**
```
❌ Passkey registration cancelled

❌ Your browser doesn't support passkeys. Please use Guardian or update your browser.

❌ Passkey registration failed. Please try again or choose Guardian.

❌ This passkey is already registered.
```
