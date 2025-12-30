# US-UG-003: Remove Users from Group

## Story

**As a** Profile Administrator
**I want** to remove users from a group with confirmation
**So that** I can maintain accurate group membership and prevent accidental removals

## Acceptance Criteria

- [ ] Each group member has a remove button/action
- [ ] Clicking remove shows confirmation dialog
- [ ] Confirmation dialog shows user name and group name
- [ ] Confirmation dialog warns about permission implications
- [ ] User is removed only after confirmation
- [ ] Success message displayed after removal
- [ ] Member list updates immediately
- [ ] Member count decreases
- [ ] Audit log records removal with timestamp and actor
- [ ] Removed user retains any direct permissions they have

## Technical Notes

**API Changes:**
- DELETE `/api/profiles/{profileId}/user-groups/{groupId}/members/{userId}`
  - Returns 204 No Content on success
  - Returns 403 Forbidden if user lacks MANAGE_USERS permission
  - Returns 404 Not Found if group or user doesn't exist or user not in group

- DELETE `/api/profiles/{profileId}/user-groups/{groupId}/members` (bulk)
  - Request body: `{ "userIds": ["uuid1", "uuid2", ...] }`
  - Returns 200 OK with `{ "removed": 5 }`
  - Supports removing multiple users at once

**Domain Model:**
- Add `removeMember(UserId)` method to `UserGroup` aggregate
- Add `removeMembers(List<UserId>)` for bulk removal
- Emit `UserRemovedFromGroup` domain event
- Domain event triggers permission recalculation for affected users

## Dependencies

- US-UG-002: Add Users to Group must be completed
- Permission evaluation system must handle group membership changes

## Test Cases

1. **Remove Single User with Confirmation**
   - Given user "john@example.com" is in group "Engineering"
   - When admin clicks remove button
   - Then confirmation dialog appears with warning
   - When admin confirms
   - Then john is removed from group
   - And member count decreases by 1
   - And success message displays

2. **Cancel Removal**
   - Given user "jane@example.com" is in group "Marketing"
   - When admin clicks remove button
   - And clicks "Cancel" on confirmation dialog
   - Then jane remains in group
   - And no changes are made

3. **Remove User Retains Direct Permissions**
   - Given user "bob@example.com" has direct permission P1 and group permission P2
   - When admin removes bob from group
   - Then bob loses P2 from group
   - But bob retains P1 direct permission

4. **Bulk Remove Users**
   - Given 5 users are selected in group member list
   - When admin clicks "Remove Selected"
   - And confirms the action
   - Then all 5 users are removed
   - And success message shows "5 users removed"

5. **Remove Last Member**
   - Given group "Sales" has only 1 member
   - When admin removes that member
   - Then group becomes empty
   - And group still exists with 0 members

6. **User Not in Group**
   - Given user "alice@example.com" is not in group "Engineering"
   - When API receives remove request for alice
   - Then returns 404 Not Found

7. **Unauthorized Access**
   - Given user without MANAGE_USERS permission
   - When they attempt to remove user from group
   - Then they receive 403 Forbidden error

## UI/UX (if applicable)

**Remove Member Interface:**
- Remove icon/button next to each member in list
- Checkbox selection for bulk removal
- "Remove Selected" button when multiple users selected

**Confirmation Dialog:**
- Title: "Remove User from Group?"
- Message: "Remove [User Name] from [Group Name]?"
- Warning: "This user will lose permissions inherited from this group."
- "Remove" danger button
- "Cancel" secondary button

**After Removal:**
- Toast notification: "User removed from group"
- Member list refreshes automatically
- Member count badge updates
