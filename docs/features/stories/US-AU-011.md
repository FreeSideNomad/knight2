# US-AU-011: Session Management

## Story

**As a** security-conscious platform
**I want** to manage user sessions with appropriate timeouts and invalidation rules
**So that** we minimize the risk of unauthorized access through abandoned or compromised sessions

## Acceptance Criteria

- [ ] Session idle timeout is 30 minutes of inactivity
- [ ] Session absolute timeout is 8 hours from creation
- [ ] User activity extends idle timeout but not absolute timeout
- [ ] Access tokens expire after 30 minutes
- [ ] Refresh tokens expire after 8 hours
- [ ] User receives warning 5 minutes before idle timeout
- [ ] User can extend session before timeout expires
- [ ] Session is invalidated on logout
- [ ] All sessions are invalidated on password change
- [ ] Multiple concurrent sessions are supported
- [ ] User can view active sessions in account settings
- [ ] User can revoke individual sessions
- [ ] Admin can revoke user sessions
- [ ] Session data includes device, location, and last activity
- [ ] Suspicious session activity triggers alerts

## Technical Notes

**Token Configuration:**
```javascript
// Access Token (JWT)
{
  "exp": current_time + 1800,        // 30 minutes
  "iat": current_time,
  "sub": "user_id",
  "scope": "openid profile email",
  "session_id": "unique_session_id"
}

// Refresh Token (Opaque)
{
  "token": "cryptographically_random_string",
  "expires": current_time + 28800,   // 8 hours
  "session_id": "unique_session_id",
  "absolute_exp": login_time + 28800 // Cannot extend beyond 8 hours
}
```

**Database Schema:**
```sql
CREATE TABLE user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    session_id VARCHAR(255) UNIQUE NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    last_activity_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    absolute_expires_at TIMESTAMP NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    device_name VARCHAR(255),
    location VARCHAR(255),
    revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP,
    revoked_by VARCHAR(50), -- 'user', 'admin', 'system'
    revoked_reason VARCHAR(255)
);

CREATE INDEX idx_sessions_user ON user_sessions(user_id);
CREATE INDEX idx_sessions_session_id ON user_sessions(session_id);
CREATE INDEX idx_sessions_expires ON user_sessions(expires_at);
CREATE INDEX idx_sessions_active ON user_sessions(user_id, revoked) WHERE revoked = FALSE;

CREATE TABLE session_activity_log (
    id UUID PRIMARY KEY,
    session_id VARCHAR(255) REFERENCES user_sessions(session_id),
    activity_type VARCHAR(50), -- 'login', 'refresh', 'activity', 'logout'
    ip_address VARCHAR(45),
    endpoint VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_session_activity_session ON session_activity_log(session_id);
CREATE INDEX idx_session_activity_created ON session_activity_log(created_at);
```

**API Endpoints:**
```
POST /api/auth/token/refresh
- Request: { refreshToken: string }
- Response: {
    accessToken: string,
    expiresIn: 1800,
    sessionExpiresAt: timestamp,
    idleTimeoutWarning: boolean
  }

POST /api/auth/session/extend
- Request: { sessionId: string }
- Response: {
    extended: boolean,
    expiresAt: timestamp
  }

GET /api/auth/sessions
- Request: { userId: string }
- Response: {
    sessions: [
      {
        sessionId: string,
        deviceName: string,
        ipAddress: string,
        location: string,
        createdAt: timestamp,
        lastActivityAt: timestamp,
        expiresAt: timestamp,
        current: boolean
      }
    ]
  }

DELETE /api/auth/sessions/:sessionId
- Request: { sessionId: string, userId: string }
- Response: { revoked: boolean }

DELETE /api/auth/sessions/all
- Request: { userId: string, exceptCurrent?: boolean }
- Response: { revokedCount: number }

POST /api/admin/sessions/:sessionId/revoke
- Request: {
    sessionId: string,
    adminId: string,
    reason: string
  }
- Response: { revoked: boolean }
```

**Session Management Logic:**

*Activity Tracking:*
```javascript
// On any API request with valid access token
function updateSessionActivity(sessionId) {
  const now = new Date();
  const session = getSession(sessionId);

  // Check absolute timeout (8 hours)
  if (now > session.absolute_expires_at) {
    revokeSession(sessionId, 'system', 'Absolute timeout exceeded');
    throw new SessionExpiredError('Session expired');
  }

  // Check idle timeout (30 minutes)
  const idleMinutes = (now - session.last_activity_at) / 60000;
  if (idleMinutes > 30) {
    revokeSession(sessionId, 'system', 'Idle timeout exceeded');
    throw new SessionExpiredError('Session expired due to inactivity');
  }

  // Update last activity
  updateSession(sessionId, {
    last_activity_at: now,
    expires_at: new Date(now.getTime() + 30 * 60000) // +30 minutes
  });

  // Check if warning threshold reached (25 minutes idle)
  const warningNeeded = idleMinutes >= 25;
  return { warningNeeded };
}
```

*Token Refresh:*
```javascript
function refreshAccessToken(refreshToken) {
  const session = getSessionByRefreshToken(refreshToken);

  // Validate refresh token not expired
  if (new Date() > session.expires_at) {
    throw new TokenExpiredError('Refresh token expired');
  }

  // Update activity
  updateSessionActivity(session.session_id);

  // Issue new access token
  const accessToken = generateAccessToken(session.user_id, session.session_id);

  return { accessToken, expiresIn: 1800 };
}
```

**Idle Timeout Warning:**
- Frontend polls session endpoint every 1 minute
- When 25 minutes of inactivity detected, show warning
- Warning gives user 5 minutes to extend session
- User can click "Stay Logged In" to reset idle timer
- After 30 minutes, force logout

**Cleanup Job:**
```sql
-- Run every hour
DELETE FROM user_sessions
WHERE expires_at < NOW() - INTERVAL '1 day'
  OR (revoked = TRUE AND revoked_at < NOW() - INTERVAL '30 days');

DELETE FROM session_activity_log
WHERE created_at < NOW() - INTERVAL '90 days';
```

## Dependencies

- US-AU-007 (Login flow creates sessions)

## Test Cases

1. **Session Created on Login**
   - Given user successfully logs in
   - When authentication completes
   - Then session is created with 8-hour absolute expiration
   - And initial idle timeout is 30 minutes

2. **Access Token Expires After 30 Minutes**
   - Given access token issued
   - When 31 minutes pass
   - Then API requests fail with "Token expired"
   - And user must refresh token

3. **Refresh Token Extends Idle Timeout**
   - Given session with 25 minutes of inactivity
   - When user refreshes access token
   - Then idle timeout is reset to 30 minutes
   - But absolute timeout remains unchanged

4. **Absolute Timeout After 8 Hours**
   - Given session created 8 hours ago
   - When user tries to refresh token
   - Then refresh fails with "Session expired"
   - And user must log in again

5. **Idle Timeout After 30 Minutes**
   - Given session with 30 minutes of inactivity
   - When user makes API request
   - Then request fails with "Session expired due to inactivity"

6. **Idle Timeout Warning**
   - Given session with 25 minutes of inactivity
   - When frontend checks session status
   - Then warning flag is returned
   - And UI shows timeout warning

7. **User Extends Session**
   - Given idle timeout warning shown
   - When user clicks "Stay Logged In"
   - Then idle timeout is reset to 30 minutes
   - And warning disappears

8. **Session Invalidated on Logout**
   - Given active session
   - When user logs out
   - Then refresh token is revoked
   - And session is marked as revoked

9. **All Sessions Invalidated on Password Change**
   - Given user has 3 active sessions
   - When user changes password
   - Then all 3 sessions are revoked
   - And user must log in again on all devices

10. **Multiple Concurrent Sessions**
    - Given user logged in on laptop
    - When user logs in on phone
    - Then both sessions are active
    - And each has independent timeouts

11. **View Active Sessions**
    - Given user has 2 active sessions
    - When user views account settings
    - Then both sessions are listed with device and location info

12. **Revoke Individual Session**
    - Given user has session on laptop and phone
    - When user revokes laptop session from phone
    - Then laptop session is invalidated
    - And phone session remains active

13. **Admin Revokes User Session**
    - Given admin views user's active sessions
    - When admin revokes a session with reason "Security incident"
    - Then session is immediately invalidated
    - And action is logged for audit

14. **Activity Updates Last Activity Timestamp**
    - Given session with last activity 10 minutes ago
    - When user makes API request
    - Then last_activity_at is updated to current time

15. **Session Cleanup Job**
    - Given expired sessions from 2 days ago
    - When cleanup job runs
    - Then old expired sessions are deleted

## UI/UX (if applicable)

**Idle Timeout Warning Modal:**
```
Your session is about to expire

You've been inactive for 25 minutes. For your security,
you'll be automatically signed out in 5 minutes.

Time remaining: 4:45

[Stay Logged In] [Sign Out Now]
```

**Active Sessions (Account Settings):**
```
Active Sessions

You are currently signed in on 2 devices:

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MacBook Pro - Chrome (Current Session)
  IP: 192.168.1.100
  Location: New York, NY
  Last active: 2 minutes ago
  Created: Dec 30, 2024 at 9:00 AM
  Expires: Dec 30, 2024 at 5:00 PM

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
iPhone 12 - Safari
  IP: 192.168.1.105
  Location: New York, NY
  Last active: 1 hour ago
  Created: Dec 30, 2024 at 8:00 AM
  Expires: Dec 30, 2024 at 4:00 PM

  [Revoke This Session]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[Sign Out All Other Sessions]
```

**Session Revoked Notification:**
```
Session Ended

Your session has been signed out.

Reason: Session revoked from another device

[Sign In Again]
```

**Admin Session Management:**
```
User Sessions - john.doe

Active Sessions: 2

Session 1
  Device: MacBook Pro - Chrome
  IP: 192.168.1.100
  Location: New York, NY
  Created: Dec 30, 2024 9:00 AM
  Last Activity: 5 minutes ago

  [Revoke Session]

Session 2
  Device: iPhone 12 - Safari
  IP: 192.168.1.105
  Location: New York, NY
  Created: Dec 30, 2024 8:00 AM
  Last Activity: 2 hours ago

  [Revoke Session]

[Revoke All Sessions]
```

**Session Revocation Dialog (Admin):**
```
Revoke User Session?

This will immediately sign out the user from this device.

User: john.doe
Device: iPhone 12 - Safari
Last Activity: 2 hours ago

Reason: *
[Suspicious activity detected_____________]

[Cancel] [Revoke Session]
```

**Session Timeout Page:**
```
Session Expired

Your session has expired due to inactivity.

Please sign in again to continue.

[Sign In]

Session details:
• Expired: Dec 30, 2024 at 3:45 PM
• Reason: 30 minutes of inactivity
```

**Auto-refresh Notification (subtle toast):**
```
⟳ Session refreshed
```

**Multiple Sessions Warning:**
```
ℹ️ You are signed in on multiple devices

For security, review your active sessions and sign out
from any devices you don't recognize.

[View Active Sessions] [Dismiss]
```
