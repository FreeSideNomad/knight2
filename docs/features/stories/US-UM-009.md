# US-UM-009: View User Activity

## Story

**As a** bank administrator
**I want** to view a timeline of user activities including logins, password changes, and MFA events
**So that** I can monitor user behavior, investigate security incidents, and audit account changes

## Acceptance Criteria

- [ ] Activity timeline shows chronological list of user events
- [ ] Events include: logins, failed logins, password changes, MFA enrollment, MFA reset, account locks/unlocks, role changes
- [ ] Each event displays: timestamp, event type, source IP, user agent, result (success/failure)
- [ ] Timeline supports filtering by event type and date range
- [ ] Timeline supports pagination for users with many events
- [ ] Failed login attempts are highlighted
- [ ] Suspicious activity patterns are flagged (e.g., multiple failed logins, location changes)
- [ ] Export activity log to CSV
- [ ] Activity data is retained for minimum 90 days

## Technical Notes

**Database Schema:**
```sql
CREATE TABLE user_activity_log (
  id BIGSERIAL PRIMARY KEY,
  user_id VARCHAR(255) NOT NULL,
  event_type VARCHAR(50) NOT NULL,
  event_timestamp TIMESTAMP NOT NULL,
  ip_address VARCHAR(45),
  user_agent TEXT,
  location VARCHAR(255),
  result VARCHAR(20), -- SUCCESS, FAILURE, BLOCKED
  details JSONB,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_activity_user_id ON user_activity_log(user_id);
CREATE INDEX idx_user_activity_timestamp ON user_activity_log(event_timestamp);
CREATE INDEX idx_user_activity_type ON user_activity_log(event_type);
```

**Event Types:**
- LOGIN_SUCCESS
- LOGIN_FAILURE
- LOGOUT
- PASSWORD_CHANGED
- PASSWORD_RESET_REQUESTED
- PASSWORD_RESET_COMPLETED
- MFA_ENROLLED
- MFA_VERIFIED
- MFA_RESET
- ACCOUNT_LOCKED
- ACCOUNT_UNLOCKED
- ROLE_ADDED
- ROLE_REMOVED
- EMAIL_CHANGED
- PROFILE_UPDATED

**Event Details (JSONB):**
```json
{
  "reason": "Too many failed attempts",
  "performedBy": "admin@example.com",
  "oldValue": "EMPLOYEE",
  "newValue": "BANK_ADMIN",
  "failureReason": "Invalid password",
  "deviceInfo": {
    "browser": "Chrome",
    "os": "Windows 10",
    "device": "Desktop"
  }
}
```

**Implementation:**
- Create ActivityLogService to record events
- Integrate with authentication flow to log login events
- Create event listeners for domain events (password changed, role updated, etc.)
- Implement IP geolocation lookup for location data
- Add anomaly detection for suspicious patterns
- Implement data retention policy (archive after 90 days, delete after 2 years)

**API Endpoints:**
```
GET /api/users/{id}/activity
Query Parameters:
  - eventType: string (optional, filter by event type)
  - startDate: ISO date (optional)
  - endDate: ISO date (optional)
  - page: number (default: 0)
  - size: number (default: 50)

Response: 200 OK
{
  "userId": "usr_123456",
  "activities": [
    {
      "id": 1001,
      "eventType": "LOGIN_SUCCESS",
      "timestamp": "2025-12-30T15:30:00Z",
      "ipAddress": "192.168.1.100",
      "location": "New York, US",
      "userAgent": "Mozilla/5.0...",
      "result": "SUCCESS",
      "details": {
        "deviceInfo": {
          "browser": "Chrome",
          "os": "Windows 10"
        }
      }
    }
  ],
  "totalElements": 150,
  "totalPages": 3,
  "currentPage": 0
}

GET /api/users/{id}/activity/export
Response: CSV file download
```

## Dependencies

- None

## Test Cases

1. **Login Success Logged**: Verify successful login creates activity entry
2. **Login Failure Logged**: Verify failed login creates activity entry
3. **Password Change Logged**: Verify password change creates activity entry
4. **MFA Events Logged**: Verify MFA enrollment and reset are logged
5. **Lock/Unlock Logged**: Verify account lock and unlock events are logged
6. **IP Address Captured**: Verify IP address is recorded for events
7. **User Agent Captured**: Verify user agent is recorded for events
8. **Location Lookup**: Verify IP geolocation is performed and stored
9. **Event Filtering**: Verify filtering by event type works
10. **Date Range Filtering**: Verify filtering by date range works
11. **Pagination**: Verify pagination works for large activity logs
12. **Export to CSV**: Verify activity log can be exported to CSV
13. **Data Retention**: Verify old activity logs are archived/deleted per policy
14. **Suspicious Activity Detection**: Verify multiple failed logins are flagged

## UI/UX (if applicable)

**User Activity Timeline:**
```
┌─────────────────────────────────────────────────────────┐
│ John Doe - Activity Log                                │
├─────────────────────────────────────────────────────────┤
│ Filters:                                                │
│ Event Type: [All ▾]  Date: [Last 30 days ▾]  [Export] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ Today, 3:30 PM                                          │
│ ✓ Login Successful                                      │
│   IP: 192.168.1.100 • New York, US • Chrome/Windows    │
│                                                         │
│ Today, 9:15 AM                                          │
│ ✓ Login Successful                                      │
│   IP: 192.168.1.100 • New York, US • Chrome/Windows    │
│                                                         │
│ Yesterday, 11:45 PM                                     │
│ ⚠ Login Failed - Invalid password                      │
│   IP: 203.0.113.42 • Unknown location • Firefox/Mac    │
│                                                         │
│ Yesterday, 11:42 PM                                     │
│ ⚠ Login Failed - Invalid password                      │
│   IP: 203.0.113.42 • Unknown location • Firefox/Mac    │
│                                                         │
│ December 29, 2:30 PM                                    │
│ 🔒 Account Locked                                       │
│   Reason: Too many failed login attempts                │
│   Locked by: System                                     │
│                                                         │
│ December 28, 10:00 AM                                   │
│ 🔑 Password Changed                                     │
│   IP: 192.168.1.100 • New York, US • Chrome/Windows    │
│                                                         │
│ December 27, 4:15 PM                                    │
│ 📱 MFA Enrolled                                         │
│   IP: 192.168.1.100 • New York, US • Chrome/Windows    │
│                                                         │
│           [Load More]  Showing 1-7 of 45                │
└─────────────────────────────────────────────────────────┘
```

**Event Type Filter Dropdown:**
```
[All                              ▾]
  All Events
  ─────────────
  Login Success
  Login Failure
  Logout
  ─────────────
  Password Changed
  Password Reset
  ─────────────
  MFA Enrolled
  MFA Reset
  ─────────────
  Account Locked
  Account Unlocked
  ─────────────
  Role Changed
  Profile Updated
```

**Suspicious Activity Alert:**
```
┌─────────────────────────────────────────┐
│ ⚠ Suspicious Activity Detected          │
├─────────────────────────────────────────┤
│ Multiple failed login attempts detected │
│ from unknown location:                  │
│                                         │
│ • 3 failed attempts                     │
│ • IP: 203.0.113.42                      │
│ • Location: Unknown                     │
│ • Time: Dec 29, 11:42 PM - 11:45 PM     │
│                                         │
│ Account was automatically locked.       │
│                                         │
│ [View Details]  [Contact User]          │
└─────────────────────────────────────────┘
```

**Activity Details Expanded:**
```
┌─────────────────────────────────────────┐
│ Login Failed                            │
├─────────────────────────────────────────┤
│ Timestamp: Dec 29, 2025 11:45:23 PM EST │
│ Result: FAILURE                         │
│ Reason: Invalid password                │
│                                         │
│ Network Information:                    │
│ • IP Address: 203.0.113.42              │
│ • Location: Unknown (VPN suspected)     │
│ • ISP: Example ISP                      │
│                                         │
│ Device Information:                     │
│ • Browser: Firefox 120.0                │
│ • OS: macOS 14.0                        │
│ • Device: Desktop                       │
│                                         │
│ User Agent:                             │
│ Mozilla/5.0 (Macintosh; Intel Mac OS X  │
│ 14_0) AppleWebKit/537.36 ...            │
│                                         │
│              [Close]                    │
└─────────────────────────────────────────┘
```

**CSV Export Format:**
```
Timestamp,Event Type,Result,IP Address,Location,Browser,OS,Details
2025-12-30T15:30:00Z,LOGIN_SUCCESS,SUCCESS,192.168.1.100,"New York, US",Chrome,Windows 10,
2025-12-30T09:15:00Z,LOGIN_SUCCESS,SUCCESS,192.168.1.100,"New York, US",Chrome,Windows 10,
2025-12-29T23:45:00Z,LOGIN_FAILURE,FAILURE,203.0.113.42,Unknown,Firefox,macOS,Invalid password
...
```
