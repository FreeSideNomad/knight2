# US-ICP-009: Passkey MFA Option

## Story

**As an** Indirect Client User
**I want** to use passkey/WebAuthn as my MFA method
**So that** I can have a more secure and convenient authentication experience using biometrics

## Acceptance Criteria

- [ ] Passkey option appears during MFA enrollment
- [ ] Users can register passkey using biometric authentication (Face ID, Touch ID, Windows Hello, etc.)
- [ ] Users can register multiple passkeys (e.g., for different devices)
- [ ] Passkey registration works on supported browsers (Chrome, Safari, Edge, Firefox)
- [ ] Clear error message if browser/device doesn't support passkeys
- [ ] User can name their passkeys (e.g., "iPhone", "MacBook")
- [ ] User can manage (view, delete) their registered passkeys from profile settings
- [ ] Passkey can be used as primary MFA method (no need for SMS/authenticator)
- [ ] Fallback to other MFA methods if passkey not available
- [ ] Passkey authentication is faster than traditional MFA methods
- [ ] Security key hardware (like YubiKey) can also be registered as passkey

## Technical Notes

**WebAuthn/FIDO2 Implementation:**
- Use WebAuthn API for passkey registration and authentication
- Support platform authenticators (built-in biometrics)
- Support roaming authenticators (security keys like YubiKey)
- Credential creation with public key cryptography
- Store public key on server, private key remains on device

**API Endpoints:**
- `POST /api/auth/passkey/register/begin` - Initiate passkey registration
  - Response: `{ challengeId: string, options: PublicKeyCredentialCreationOptions }`
- `POST /api/auth/passkey/register/complete` - Complete passkey registration
  - Request: `{ challengeId: string, credential: PublicKeyCredential, name: string }`
  - Response: `{ success: boolean, credentialId: string }`
- `POST /api/auth/passkey/authenticate/begin` - Initiate passkey authentication
  - Response: `{ challengeId: string, options: PublicKeyCredentialRequestOptions }`
- `POST /api/auth/passkey/authenticate/complete` - Complete passkey authentication
  - Request: `{ challengeId: string, credential: PublicKeyCredential }`
  - Response: `{ success: boolean, token: string }`
- `GET /api/users/{userId}/passkeys` - List user's registered passkeys
  - Response: `PasskeyDTO[]`
- `DELETE /api/users/{userId}/passkeys/{credentialId}` - Remove passkey

**Auth0 Integration:**
- Use Auth0 WebAuthn support or custom implementation
- Store passkey credentials in Auth0 user metadata
- Alternative: Store in database table `user_passkeys`:
  - `id`: UUID
  - `user_id`: UUID
  - `credential_id`: TEXT (unique)
  - `public_key`: TEXT
  - `counter`: BIGINT (for replay attack prevention)
  - `name`: VARCHAR(100)
  - `authenticator_type`: ENUM('platform', 'roaming')
  - `created_at`: TIMESTAMP
  - `last_used_at`: TIMESTAMP

**WebAuthn Configuration:**
```javascript
{
  rpName: "Knight Indirect Client Portal",
  rpId: "portal.knight.com",
  userId: userId,
  userName: userEmail,
  userDisplayName: userFullName,
  challenge: randomBytes(32),
  pubKeyCredParams: [
    { type: "public-key", alg: -7 },  // ES256
    { type: "public-key", alg: -257 } // RS256
  ],
  authenticatorSelection: {
    authenticatorAttachment: "platform", // or "cross-platform"
    userVerification: "required",
    residentKey: "preferred"
  },
  timeout: 60000,
  attestation: "none"
}
```

**Browser Support:**
- Chrome/Edge: Full support
- Safari (iOS 16+, macOS 13+): Full support
- Firefox: Full support
- Detect browser support using: `window.PublicKeyCredential`

**Security:**
- Challenge must be unique and expire in 5 minutes
- Validate origin and RP ID during authentication
- Increment and verify counter to prevent replay attacks
- Require user verification (biometric or PIN)
- Audit log all passkey registrations and authentications

**User Experience:**
- Show device-specific prompts (Face ID on iPhone, Touch ID on Mac, Windows Hello on Windows)
- Clear instructions for passkey registration
- Fallback to SMS/authenticator if passkey fails
- Allow users to skip passkey and use traditional MFA

## Dependencies

- WebAuthn API support in browsers
- Auth0 or custom WebAuthn implementation
- HTTPS (required for WebAuthn)
- User device with biometric capability or security key

## Test Cases

1. **Browser Support Detection**: Open enrollment on supported browser and see passkey option
2. **Unsupported Browser**: Open enrollment on old browser and verify graceful degradation
3. **Register Passkey - Platform**: Register using Face ID/Touch ID and verify success
4. **Register Passkey - Roaming**: Register YubiKey and verify success
5. **Name Passkey**: Provide custom name during registration and verify it's stored
6. **Multiple Passkeys**: Register passkeys on multiple devices and verify all listed
7. **Authenticate with Passkey**: Log in using passkey and verify faster than traditional MFA
8. **Passkey Fallback**: Attempt passkey on device without it and verify fallback to SMS/authenticator
9. **Delete Passkey**: Remove passkey from settings and verify it's deleted
10. **Last Used Tracking**: Use passkey and verify last_used_at timestamp updated
11. **Cross-Device**: Register on Mac, attempt to use on iPhone and verify prompts correctly
12. **Audit Log**: Register and use passkey and verify audit entries created
13. **Counter Validation**: Replay attack attempt and verify it's rejected
14. **Challenge Expiration**: Start registration, wait 6 minutes, and verify challenge expired

## UI/UX

**MFA Enrollment - Passkey Option:**
```
Set Up Multi-Factor Authentication
-----------------------------------

Choose your MFA method:

( ) Authenticator App
    Use Google Authenticator, Microsoft Authenticator, or similar app

( ) SMS Text Message
    Receive codes via text message to your phone

( ) Passkey (Recommended)
    Use fingerprint, face recognition, or security key
    ✓ More secure
    ✓ Faster login
    ✓ No codes to type

[Continue]
```

**Passkey Registration Screen:**
```
Set Up Passkey
--------------

A passkey lets you sign in using your fingerprint, face,
or screen lock instead of typing a code.

Your passkey is stored securely on this device and never
leaves it.

Name this passkey (optional):
[MacBook Pro               ]

[Cancel]  [Create Passkey]
```

**Browser Prompt (varies by device):**
- iOS: "Use Face ID to create a passkey"
- macOS: "Use Touch ID to create a passkey"
- Windows: "Use Windows Hello to create a passkey"
- Android: "Use fingerprint to create a passkey"

**Passkey Registration Success:**
```
Passkey Created Successfully
----------------------------

✓ Your passkey has been registered

Name: MacBook Pro
Type: Platform Authenticator
Created: 2025-12-30 10:30 AM

You can now use this passkey to sign in.

Want to add another passkey for a different device?
[Add Another Passkey]

[Done]
```

**Manage Passkeys (Profile Settings):**
```
Registered Passkeys
-------------------

MacBook Pro
Platform Authenticator
Registered: 2025-12-30 10:30 AM
Last used: 2025-12-30 2:45 PM
[Remove]

iPhone 14 Pro
Platform Authenticator
Registered: 2025-12-29 3:15 PM
Last used: Never
[Remove]

YubiKey 5C
Security Key
Registered: 2025-12-15 9:00 AM
Last used: 2025-12-28 11:20 AM
[Remove]

[Add New Passkey]
```

**Login with Passkey:**
```
Welcome back, John!
-------------------

Email: john.smith@example.com

Use your passkey to sign in

[Use Passkey]

[Use a different method]
```

**Unsupported Browser Message:**
```
Passkeys Not Supported
----------------------

Your browser doesn't support passkeys yet.

Please choose another MFA method or update your browser:
- Chrome 108+
- Safari 16+
- Edge 108+
- Firefox 119+

[Use Authenticator App]  [Use SMS]
```

**Error Messages:**
- Registration failed: "Failed to create passkey. Please try again or use another MFA method."
- Authentication failed: "Failed to authenticate with passkey. Please try again or use another MFA method."
- Device not found: "Passkey not found on this device. Use another method or register a new passkey."
- Timeout: "Passkey registration timed out. Please try again."
