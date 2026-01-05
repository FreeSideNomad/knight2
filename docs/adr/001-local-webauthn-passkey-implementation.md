# ADR-001: Local WebAuthn/Passkey Implementation

**Status:** Proposed
**Date:** 2026-01-05
**Decision Makers:** TBD (requires security team review)

## Context

Issue #10 requires implementing passkey enrollment and authentication for indirect client users. Passkeys (WebAuthn/FIDO2) provide phishing-resistant, passwordless authentication that improves both security and user experience.

Our system uses Auth0 as the identity provider for indirect client users, with a **custom login UI** (`client-login`) rather than Auth0's Universal Login. This architectural choice was made to provide a branded, integrated login experience.

### Auth0 Passkey API Assessment

We evaluated Auth0's passkey support for custom UI integration:

| Auth0 Feature | Status | Limitation |
|--------------|--------|------------|
| Native Passkeys API | Early Access | Designed for native mobile apps (iOS/Android), not web custom UI |
| Universal Login Passkeys | GA | Requires redirecting to Auth0-hosted login page |
| MFA API WebAuthn enrollment | Roadmap (Q3 2024) | `/mfa/associate` does not support WebAuthn authenticator type |
| MFA API WebAuthn challenge | Partial | Requires Actions + Universal Login redirect |
| Management API | Available | Can store/retrieve credentials but no WebAuthn ceremony support |

**Key Finding:** Auth0's passkey implementation is primarily designed for their Universal Login experience. Custom UI support via APIs is limited and still in early access/roadmap phases.

## Decision

We will implement WebAuthn/passkey functionality locally using the **WebAuthn4j** library, storing passkey credentials in our database rather than Auth0.

### Implementation Approach

1. **WebAuthn4j Library**: Use the FIDO2-conformant Java library (same library used by Keycloak and Spring Security)
2. **Local Credential Storage**: Store passkey credentials in our `passkeys` table
3. **User Aggregate Tracking**: Track passkey status (`passkeyOffered`, `passkeyEnrolled`, `passkeyHasUv`) on User aggregate
4. **Relying Party**: Our application acts as the WebAuthn Relying Party
5. **Step-up Authentication**: Use passkey verification for sensitive operations

### Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  client-login   │────▶│ PasskeyController│────▶│  PasskeyService │
│  (Browser)      │     │ (REST API)       │     │  (WebAuthn4j)   │
└─────────────────┘     └──────────────────┘     └─────────────────┘
        │                                                 │
        │ navigator.credentials                           │
        │ .create() / .get()                             ▼
        ▼                                        ┌─────────────────┐
┌─────────────────┐                              │  PasskeyRepo    │
│  Authenticator  │                              │  (Database)     │
│  (Platform/USB) │                              └─────────────────┘
└─────────────────┘
```

### Data Model

```sql
CREATE TABLE passkeys (
    passkey_id UNIQUEIDENTIFIER PRIMARY KEY,
    user_id UNIQUEIDENTIFIER NOT NULL,
    credential_id VARCHAR(1024) NOT NULL UNIQUE,
    public_key VARCHAR(2048) NOT NULL,
    aaguid VARCHAR(36),
    display_name NVARCHAR(255) NOT NULL,
    sign_count BIGINT NOT NULL DEFAULT 0,
    user_verification BIT NOT NULL DEFAULT 0,
    backup_eligible BIT NOT NULL DEFAULT 0,
    backup_state BIT NOT NULL DEFAULT 0,
    transports VARCHAR(255),
    last_used_at DATETIME2,
    created_at DATETIME2 NOT NULL,
    updated_at DATETIME2 NOT NULL,
    CONSTRAINT FK_passkeys_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);
```

## Alternatives Considered

### 1. Redirect to Auth0 Universal Login for Passkeys
- **Pros:** Uses Auth0's built-in passkey support, credentials managed by Auth0
- **Cons:** Breaks custom UI experience, requires context switching, Auth0 manages credentials not us
- **Rejected:** Inconsistent UX with rest of login flow

### 2. Wait for Auth0 Native Passkeys API General Availability
- **Pros:** Official Auth0 support, credentials in Auth0
- **Cons:** Timeline uncertain, currently limited to mobile native apps
- **Rejected:** Unacceptable delay for security feature

### 3. Use Third-Party Passkey Service (e.g., Authsignal, Hanko)
- **Pros:** Purpose-built passkey infrastructure
- **Cons:** Additional vendor dependency, cost, data residency concerns
- **Rejected:** Adds complexity and vendor lock-in

### 4. Hybrid: Local WebAuthn + Sync to Auth0 Metadata
- **Pros:** Best of both worlds
- **Cons:** Complex sync logic, Auth0 metadata size limits
- **Deferred:** Could be future enhancement

## Consequences

### Positive
- Full control over passkey UX and enrollment flow
- No dependency on Auth0 passkey roadmap
- Consistent custom UI experience
- Can implement step-up authentication for any operation
- FIDO2-conformant implementation via WebAuthn4j

### Negative
- We own the passkey infrastructure and security
- Passkeys not visible in Auth0 dashboard
- Must maintain WebAuthn implementation ourselves
- Credential recovery falls to us (not Auth0)

### Security Considerations

**Requires Security Team Review:**

1. **Credential Storage**: Public keys stored in database (private keys never leave authenticator)
2. **Replay Protection**: Sign count validation prevents cloned authenticator attacks
3. **Origin Validation**: Strict RP ID and origin checking
4. **User Verification**: Track and enforce UV requirements for sensitive operations
5. **Attestation**: Consider whether to validate authenticator attestation
6. **Transport Security**: All WebAuthn ceremonies over HTTPS

### Risks

| Risk | Mitigation |
|------|------------|
| Implementation vulnerabilities | Use well-tested WebAuthn4j library, security review |
| Credential recovery | Implement secure recovery flow, keep password as backup |
| Browser compatibility | Progressive enhancement, graceful fallback |
| Authenticator loss | Support multiple passkeys per user, recovery codes |

## Action Items

- [ ] Security team review of this ADR
- [ ] Penetration testing of WebAuthn implementation
- [ ] Define attestation policy (none, indirect, direct)
- [ ] Define credential recovery procedures
- [ ] Document browser support matrix

## References

- [WebAuthn Specification](https://www.w3.org/TR/webauthn-2/)
- [WebAuthn4j Documentation](https://webauthn4j.github.io/webauthn4j/en/)
- [Auth0 Passkeys Documentation](https://auth0.com/docs/authenticate/database-connections/passkeys)
- [Auth0 Native Passkeys API](https://auth0.com/docs/native-passkeys-api)
- [FIDO Alliance Passkeys](https://fidoalliance.org/passkeys/)
