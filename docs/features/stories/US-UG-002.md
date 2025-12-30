# US-UG-002: Add Users to Group

## Story

**As a** Profile Administrator
**I want** to add multiple users to a group using a user picker
**So that** I can quickly build group membership and users can belong to multiple groups

## Acceptance Criteria

- [ ] User picker displays all users in the profile
- [ ] User picker supports search/filter by name or email
- [ ] Multiple users can be selected at once
- [ ] Selected users are visually indicated
- [ ] User can belong to multiple groups simultaneously
- [ ] Adding users who are already members shows warning but doesn't fail
- [ ] Success message shows count of users added
- [ ] Group member list updates immediately after adding users
- [ ] Audit log records user additions with timestamp and actor

## Technical Notes

**Database Changes:**
- Create `user_group_members` table:
  - `id` (UUID, primary key)
  - `user_group_id` (UUID, foreign key to user_groups)
  - `user_id` (UUID, foreign key to users)
  - `added_at` (TIMESTAMP)
  - `added_by` (VARCHAR(255))
- Add unique constraint on (user_group_id, user_id)
- Add indexes on user_group_id and user_id for query performance

**API Changes:**
- POST `/api/profiles/{profileId}/user-groups/{groupId}/members`
  - Request body: `{ "userIds": ["uuid1", "uuid2", ...] }`
  - Response: `{ "added": 5, "skipped": 2, "members": [UserDto...] }`
  - Returns 200 OK with summary
  - Returns 403 Forbidden if user lacks MANAGE_USERS permission
  - Returns 404 Not Found if group doesn't exist

- GET `/api/profiles/{profileId}/user-groups/{groupId}/available-users`
  - Query params: `search`, `page`, `size`
  - Returns list of users not yet in group

**Domain Model:**
- Add `addMembers(List<UserId>)` method to `UserGroup` aggregate
- Ensure idempotent behavior (adding existing member is no-op)
- Emit `UserAddedToGroup` domain event

## Dependencies

- US-UG-001: Create User Group must be completed
- User management system must be operational
- Profile user listing API must exist

## Test Cases

1. **Add Single User to Group**
   - Given a group "Engineering" with no members
   - When admin adds user "john@example.com"
   - Then user is added to group
   - And member count shows 1
   - And success message displays

2. **Add Multiple Users at Once**
   - Given a group "Sales" with 2 members
   - When admin selects and adds 5 users
   - Then all 5 users are added
   - And member count shows 7
   - And success message shows "5 users added"

3. **Add User Already in Group**
   - Given user "jane@example.com" is already in group "Marketing"
   - When admin tries to add jane@example.com again
   - Then operation succeeds with warning
   - And message shows "1 user already in group"
   - And member count remains unchanged

4. **User Belongs to Multiple Groups**
   - Given user "bob@example.com" is in group "Engineering"
   - When admin adds bob to group "Leadership"
   - Then bob is successfully added to Leadership
   - And bob remains in Engineering
   - And bob's profile shows both group memberships

5. **Search Users in Picker**
   - Given profile has 100 users
   - When admin types "john" in search field
   - Then only users with "john" in name or email are displayed
   - And admin can select from filtered results

6. **Unauthorized Access**
   - Given user without MANAGE_USERS permission
   - When they attempt to add users to group
   - Then they receive 403 Forbidden error

## UI/UX (if applicable)

**Add Members Interface:**
- "Add Members" button on group detail page
- Opens modal with user picker component
- Search field at top with placeholder "Search users..."
- Scrollable list of users with checkboxes
- Selected users highlighted with count badge
- "Add Selected Users" primary button (disabled if none selected)
- "Cancel" secondary button
- Loading spinner while fetching users
- Success toast notification after adding users
