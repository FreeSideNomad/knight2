# US-AU-012: Step-Up Authentication

## Story

**As a** user performing sensitive operations
**I want** to re-authenticate with MFA for high-risk actions
**So that** my account remains secure even if my session is compromised

## Acceptance Criteria

- [ ] Sensitive operations trigger step-up authentication requirement
- [ ] User must complete fresh MFA challenge regardless of recent login
- [ ] Step-up authentication is valid for 5 minutes
- [ ] User can complete multiple sensitive operations within 5-minute window
- [ ] After 5 minutes, step-up authentication is required again
- [ ] Guardian push or passkey can be used for step-up
- [ ] Recovery codes work for step-up authentication
- [ ] Failed step-up attempts are logged
- [ ] User receives clear explanation why re-authentication is needed
- [ ] RAR (Risk and Authorization Request) claim is included in tokens
- [ ] Step-up level is configurable per operation type
- [ ] Admin operations always require step-up authentication

## Technical Notes

**Sensitive Operations Requiring Step-Up:**
- Password change
- Email change
- MFA enrollment/removal
- API key generation
- Payment method changes
- Account deletion
- Permission changes (admin operations)
- Accessing PII (Personally Identifiable Information)
- High-value transactions
- Security settings changes

**Step-Up Authentication Levels:**
```javascript
const STEP_UP_LEVELS = {
  NONE: 0,           // No step-up required
  LOW: 1,            // Recent login (< 1 hour) acceptable
  MEDIUM: 2,         // Fresh MFA (< 5 minutes) required
  HIGH: 3,           // Fresh MFA + additional verification required
};

const OPERATION_LEVELS = {
  'change_password': 'MEDIUM',
  'change_email': 'MEDIUM',
  'enroll_mfa': 'MEDIUM',
  'remove_mfa': 'HIGH',
  'generate_api_key': 'MEDIUM',
  'delete_account': 'HIGH',
  'view_pii': 'MEDIUM',
  'admin_permission_change': 'HIGH',
};
```

**Database Schema:**
```sql
CREATE TABLE step_up_sessions (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    session_id VARCHAR(255) REFERENCES user_sessions(session_id),
    step_up_token VARCHAR(255) UNIQUE NOT NULL,
    level VARCHAR(20) NOT NULL, -- 'MEDIUM', 'HIGH'
    mfa_method VARCHAR(50), -- 'guardian', 'passkey', 'recovery_code'
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    used_for_operations TEXT[], -- List of operations performed
    ip_address VARCHAR(45),
    user_agent TEXT
);

CREATE INDEX idx_step_up_user ON step_up_sessions(user_id);
CREATE INDEX idx_step_up_token ON step_up_sessions(step_up_token);
CREATE INDEX idx_step_up_expires ON step_up_sessions(expires_at);

CREATE TABLE step_up_audit_log (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    operation VARCHAR(100) NOT NULL,
    step_up_token VARCHAR(255),
    required_level VARCHAR(20),
    success BOOLEAN,
    failure_reason VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_step_up_audit_user ON step_up_audit_log(user_id);
CREATE INDEX idx_step_up_audit_operation ON step_up_audit_log(operation);
CREATE INDEX idx_step_up_audit_created ON step_up_audit_log(created_at);
```

**API Endpoints:**
```
POST /api/auth/step-up/initiate
- Request: {
    operation: string,
    sessionId: string
  }
- Response: {
    stepUpRequired: boolean,
    level: string,
    challenge: object, // MFA challenge details
    expiresIn: 300
  }

POST /api/auth/step-up/verify
- Request: {
    sessionId: string,
    mfaMethod: string,
    proof: object // Guardian confirmation or passkey assertion
  }
- Response: {
    stepUpToken: string,
    expiresAt: timestamp,
    level: string
  }

POST /api/auth/step-up/validate
- Request: {
    stepUpToken: string,
    operation: string
  }
- Response: {
    valid: boolean,
    level: string,
    expiresIn: number
  }
```

**RAR (Rich Authorization Requests) Implementation:**

*Access Token with RAR Claim:*
```json
{
  "sub": "user_id",
  "iat": 1609459200,
  "exp": 1609460000,
  "rar": {
    "type": "step_up",
    "level": "MEDIUM",
    "timestamp": 1609459200,
    "expires": 1609459500
  }
}
```

**Step-Up Flow:**
1. User initiates sensitive operation (e.g., change password)
2. Backend checks if step-up token exists and is valid
3. If not, return 403 with step-up required error
4. Frontend displays step-up challenge modal
5. User completes MFA (Guardian push or passkey)
6. Backend validates MFA and issues step-up token
7. Frontend includes step-up token in original operation request
8. Backend validates step-up token and processes operation
9. Step-up token remains valid for 5 minutes
10. Log step-up authentication and operation in audit log

**Middleware for Step-Up Protection:**
```javascript
function requireStepUp(level) {
  return async (req, res, next) => {
    const stepUpToken = req.headers['x-step-up-token'];

    if (!stepUpToken) {
      return res.status(403).json({
        error: 'step_up_required',
        message: 'This operation requires additional authentication',
        level: level
      });
    }

    const stepUp = await validateStepUpToken(stepUpToken);

    if (!stepUp || !stepUp.valid) {
      return res.status(403).json({
        error: 'invalid_step_up_token',
        message: 'Step-up authentication expired or invalid'
      });
    }

    if (stepUp.level < STEP_UP_LEVELS[level]) {
      return res.status(403).json({
        error: 'insufficient_step_up_level',
        message: `This operation requires ${level} level authentication`
      });
    }

    // Log operation
    await logStepUpOperation(stepUp.userId, req.path, stepUpToken);

    req.stepUp = stepUp;
    next();
  };
}

// Usage
app.post('/api/users/password', requireStepUp('MEDIUM'), changePasswordHandler);
app.delete('/api/users/account', requireStepUp('HIGH'), deleteAccountHandler);
```

**Step-Up Token Structure:**
```javascript
{
  token: "cryptographically_random_string",
  userId: "user_id",
  sessionId: "session_id",
  level: "MEDIUM",
  createdAt: timestamp,
  expiresAt: timestamp + 300, // 5 minutes
  usedForOperations: ["change_password", "change_email"]
}
```

## Dependencies

- US-AU-005 (Guardian MFA for step-up)
- US-AU-006 (Passkey MFA for step-up)
- US-AU-007 (Active session required)
- US-AU-011 (Session management)

## Test Cases

1. **Step-Up Required for Password Change**
   - Given user initiates password change
   - When request is sent without step-up token
   - Then 403 error returned with "step_up_required"

2. **Guardian Push for Step-Up**
   - Given step-up required
   - When user selects Guardian push
   - Then push notification sent to user's device

3. **Successful Step-Up Authentication**
   - Given user receives Guardian push
   - When user approves push
   - Then step-up token is issued with 5-minute expiration

4. **Step-Up Token Allows Operation**
   - Given valid step-up token
   - When user submits password change with token
   - Then operation succeeds without additional MFA

5. **Step-Up Token Expires After 5 Minutes**
   - Given step-up token created 6 minutes ago
   - When user attempts operation with token
   - Then token is invalid and new step-up required

6. **Multiple Operations Within 5 Minutes**
   - Given step-up token created 2 minutes ago
   - When user changes password then changes email
   - Then both operations succeed with same token

7. **Step-Up Token Reuse**
   - Given step-up token created 1 minute ago for password change
   - When user attempts to change email
   - Then same token can be reused (within 5 minutes)

8. **Passkey for Step-Up**
   - Given step-up required
   - When user selects passkey authentication
   - Then WebAuthn challenge is presented

9. **Recovery Code for Step-Up**
   - Given step-up required and user lost MFA device
   - When user enters valid recovery code
   - Then step-up token is issued

10. **Failed Step-Up Logged**
    - Given step-up challenge sent
    - When user denies Guardian push
    - Then failure is logged in audit trail

11. **HIGH Level Step-Up for Account Deletion**
    - Given user initiates account deletion
    - When step-up is completed at MEDIUM level
    - Then operation is rejected as "insufficient_step_up_level"
    - And HIGH level step-up is required

12. **RAR Claim in Access Token**
    - Given step-up authentication completed
    - When new access token is issued
    - Then token includes RAR claim with step-up details

13. **Step-Up Bypass Attempt**
    - Given expired step-up token
    - When user tries to use it for password change
    - Then operation is rejected and attempt is logged

14. **Admin Operation Always Requires Step-Up**
    - Given admin logged in 2 minutes ago
    - When admin attempts to change user permissions
    - Then step-up is required regardless of recent login

## UI/UX (if applicable)

**Step-Up Challenge Modal:**
```
Additional Verification Required

For your security, this action requires re-authentication.

Action: Change Password

Please verify your identity:

○ Guardian Push Notification
○ Passkey (Biometric/Security Key)
○ Recovery Code

[Continue] [Cancel]

Why am I seeing this?
Sensitive operations require fresh authentication to protect
your account, even if you're already signed in.
```

**Guardian Push Step-Up:**
```
Verify Your Identity

We've sent a push notification to your Guardian app.

[Approve] the request to continue changing your password.

Waiting for approval... (5:00)

[Try Another Method] [Cancel]
```

**Passkey Step-Up:**
```
Verify Your Identity

Use your passkey to confirm this action.

[Authenticate with Passkey]

Action: Change Password

[Try Another Method] [Cancel]
```

**Step-Up Success:**
```
✓ Verification Complete

You can now proceed with sensitive operations for the next 5 minutes
without re-authenticating.

[Continue]
```

**Operation Confirmation (with Step-Up):**
```
Change Password

You've been verified. This confirmation is valid for 5 minutes.

Current Password
[••••••••••••]

New Password
[••••••••••••]

Confirm New Password
[••••••••••••]

⏱ Verification expires in: 4:32

[Change Password] [Cancel]
```

**Step-Up Expired:**
```
Verification Expired

Your verification has expired after 5 minutes of inactivity.

Please verify your identity again to continue.

[Verify Again] [Cancel]
```

**Admin Operation Step-Up:**
```
⚠️ Admin Action Verification Required

Action: Change User Permissions
User: john.doe
New Role: Administrator

This is a high-risk admin operation. Please verify your identity:

[Verify with Guardian] [Verify with Passkey]

[Cancel]
```

**Account Settings - Recent Sensitive Operations:**
```
Recent Security-Sensitive Actions

Dec 30, 2024 3:45 PM | Password Changed
  Verification: Guardian Push
  IP: 192.168.1.100

Dec 30, 2024 3:42 PM | Email Changed
  Verification: Guardian Push (reused from 3:41 PM)
  IP: 192.168.1.100

Dec 29, 2024 10:15 AM | MFA Device Added
  Verification: Passkey
  IP: 192.168.1.100

[View All Activity]
```

**Step-Up Audit Log (Admin View):**
```
Step-Up Authentication Log - john.doe

Dec 30, 2024 3:41 PM | ✓ Success | Guardian Push
  Operation: change_password
  Level: MEDIUM
  IP: 192.168.1.100
  Token used for: change_password, change_email

Dec 30, 2024 2:15 PM | ✗ Failed | Guardian Push
  Operation: remove_mfa
  Level: HIGH
  Reason: User denied push
  IP: 192.168.1.100

Dec 29, 2024 10:15 AM | ✓ Success | Passkey
  Operation: enroll_mfa
  Level: MEDIUM
  IP: 192.168.1.105
  Token used for: enroll_mfa
```

**Error Messages:**
```
❌ Additional verification required
   This action requires you to re-authenticate for security.

❌ Verification expired
   Your verification has expired. Please verify again.

❌ Insufficient verification level
   This action requires higher security verification.

❌ Step-up authentication failed
   Verification was denied or failed. Please try again.
```
