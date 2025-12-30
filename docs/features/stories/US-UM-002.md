# US-UM-002: Separate Sync Events from Login

## Story

**As a** system administrator
**I want** to distinguish between user login events and other sync events
**So that** I can accurately track when users actually log in versus when their profile data is synchronized

## Acceptance Criteria

- [ ] `last_synced_at` timestamp column added to user table
- [ ] `last_synced_at` is updated for non-login Auth0 events (password set, MFA enrolled, profile updates)
- [ ] `last_logged_in_at` is updated only for actual login events
- [ ] Both timestamps are visible in user detail view
- [ ] Sync events do not overwrite login timestamps
- [ ] Event type is logged for audit purposes

## Technical Notes

**Database Changes:**
- Add `last_synced_at TIMESTAMP` column to `users` table
- Default value: NULL
- Index not required (used for display only, not filtering)

**Implementation:**
- Update Auth0 webhook/event handler to distinguish event types
- Login events (e.g., `s` Success Login): update `last_logged_in_at`
- Sync events (e.g., `scpn` Change Password, `gd_enrollment_complete`): update `last_synced_at`
- Add event type mapping configuration
- Update User entity and DTOs

**Event Type Mapping:**
```
Login Events: s, ss, slo, slo_session_expired
Sync Events: scpn, pwd_leak, gd_enrollment_complete, gd_unenroll, u,
            gd_send_pn, gd_update_device_account, limit_wc, limit_mu
```

**API Changes:**
- Include `lastSyncedAt` in user response DTOs
- Add both timestamps to user detail endpoint

## Dependencies

- US-UM-001: Track User Login Time

## Test Cases

1. **Login Event**: Verify only `last_logged_in_at` is updated on login
2. **Password Change**: Verify only `last_synced_at` is updated on password change
3. **MFA Enrollment**: Verify only `last_synced_at` is updated on MFA enrollment
4. **Profile Update**: Verify only `last_synced_at` is updated on profile sync
5. **Multiple Events**: Verify both timestamps can have different values
6. **Event Type Mapping**: Verify all event types are correctly categorized
7. **Null Handling**: Verify both fields handle NULL values correctly
8. **API Response**: Verify both timestamps are included in API responses

## UI/UX (if applicable)

**User Detail View:**
- Display both timestamps in a "Activity" section:
  - Last Login: December 30, 2025 at 3:45 PM EST (2 hours ago)
  - Last Synced: December 30, 2025 at 5:30 PM EST (15 minutes ago)
- Show "Never" for NULL values
- Use different icons for each timestamp type (login icon vs sync icon)

**User List View:**
- Primary display: Last Login column
- Optional: Add tooltip or secondary column for Last Synced
