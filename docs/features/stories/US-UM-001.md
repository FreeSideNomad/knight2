# US-UM-001: Track User Login Time

## Story

**As a** system administrator
**I want** to track when users last logged in
**So that** I can monitor user activity and identify inactive accounts

## Acceptance Criteria

- [ ] `last_logged_in_at` timestamp column added to user table
- [ ] Column is updated automatically on successful login
- [ ] Column is visible in user list and user detail views
- [ ] Column supports null values for users who have never logged in
- [ ] Column is indexed for performance on large datasets
- [ ] Login time is recorded in UTC timezone

## Technical Notes

**Database Changes:**
- Add `last_logged_in_at TIMESTAMP` column to `users` table
- Create index on `last_logged_in_at` for filtering and sorting
- Default value: NULL

**Implementation:**
- Update login event handler to set `last_logged_in_at` when authentication succeeds
- Ensure timezone conversion to UTC before storage
- Add field to User entity and DTOs
- Update user repository methods to include the new field

**API Changes:**
- Include `lastLoggedInAt` in user response DTOs
- Add optional query parameter to filter users by last login date range

## Dependencies

- None

## Test Cases

1. **First Login**: Verify `last_logged_in_at` is NULL for newly created user
2. **Successful Login**: Verify timestamp is updated after successful authentication
3. **Failed Login**: Verify timestamp is NOT updated on failed login attempts
4. **Multiple Logins**: Verify timestamp is updated to most recent login time
5. **Timezone Handling**: Verify timestamp is stored in UTC regardless of client timezone
6. **API Response**: Verify `lastLoggedInAt` is included in user API responses
7. **Null Handling**: Verify API correctly handles and returns NULL for never-logged-in users

## UI/UX (if applicable)

**User List View:**
- Add "Last Login" column to user grid
- Display in user's local timezone with relative time (e.g., "2 days ago")
- Show "Never" for NULL values
- Support sorting by last login time

**User Detail View:**
- Display last login timestamp in detail section
- Format: "December 30, 2025 at 3:45 PM EST"
- Include relative time in parentheses
