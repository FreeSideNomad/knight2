# US-UG-008: Delete User Group

## Story

**As a** Profile Administrator
**I want** to delete a user group with confirmation and warning about permission impact
**So that** I can remove obsolete groups while understanding the consequences for members

## Acceptance Criteria

- [ ] Delete button available on group detail page
- [ ] Clicking delete shows confirmation dialog
- [ ] Confirmation shows group name, member count, and permission count
- [ ] Warning message explains members will lose inherited permissions
- [ ] Confirmation requires typing group name or clicking checkbox
- [ ] Delete processes only after explicit confirmation
- [ ] Success message displayed after deletion
- [ ] User redirected to groups list after deletion
- [ ] Group members lose inherited permissions immediately
- [ ] Group members retain their direct permissions
- [ ] Audit log records deletion with timestamp and actor
- [ ] Cancel option allows backing out safely

## Technical Notes

**API Changes:**
- DELETE `/api/profiles/{profileId}/user-groups/{groupId}`
  - Returns 204 No Content on success
  - Returns 403 Forbidden if user lacks MANAGE_USERS permission
  - Returns 404 Not Found if group doesn't exist
  - Returns 409 Conflict if deletion has dependencies (future protection)

- GET `/api/profiles/{profileId}/user-groups/{groupId}/deletion-impact`
  - Returns impact analysis before deletion:
    ```json
    {
      "groupName": "Engineering",
      "memberCount": 15,
      "permissionCount": 8,
      "affectedUsers": [
        {
          "userId": "uuid",
          "userName": "John Doe",
          "permissionsToLose": 5
        }
      ],
      "canDelete": true,
      "warnings": ["15 users will lose 8 inherited permissions"]
    }
    ```

**Domain Model:**
- Add `delete()` method to `UserGroup` aggregate
- Mark as deleted (soft delete) or remove entirely (hard delete)
- Emit `UserGroupDeleted` domain event
- Domain event triggers:
  - Removal of all group members
  - Removal of all group permissions
  - Permission cache invalidation for affected users

**Soft vs Hard Delete:**
- Recommendation: Soft delete for audit trail
- Add `deleted_at` and `deleted_by` columns
- Exclude deleted groups from queries
- Allow restoration within retention period

## Dependencies

- US-UG-001: Create User Group must be completed
- US-UG-005: View User Groups must be completed
- Permission evaluation system must handle group deletion

## Test Cases

1. **Delete Group with Confirmation**
   - Given group "Old Project Team" exists with 5 members
   - When admin clicks "Delete Group"
   - Then confirmation dialog appears
   - When admin types group name and confirms
   - Then group is deleted
   - And members lose inherited permissions
   - And redirected to groups list

2. **Cancel Deletion**
   - Given admin clicks "Delete Group"
   - When confirmation dialog appears
   - And admin clicks "Cancel"
   - Then group is not deleted
   - And members retain all permissions
   - And remains on group detail page

3. **Delete Empty Group**
   - Given group "Temporary" has 0 members
   - When admin deletes group
   - Then deletion succeeds immediately
   - And no users are affected

4. **Members Lose Only Inherited Permissions**
   - Given user "john@example.com" has:
     - Direct CLIENT_ACCESS to "TechCo"
     - Group-inherited CLIENT_ACCESS to "StartupXYZ"
   - When admin deletes the group
   - Then john loses access to "StartupXYZ"
   - But john retains access to "TechCo"

5. **Impact Warning Display**
   - Given group "Sales" has 20 members and 10 permissions
   - When admin clicks delete
   - Then confirmation shows:
     - "20 members will lose inherited permissions"
     - "10 permissions will be removed"
   - And lists affected members

6. **Deletion Impact Analysis**
   - Given group with members having various permission combinations
   - When admin views deletion impact
   - Then sees breakdown of which users lose what permissions
   - And can review impact before confirming

7. **Type Group Name Confirmation**
   - Given group "Critical-Production-Access" is being deleted
   - When confirmation requires typing group name
   - And admin types incorrect name
   - Then "Delete" button remains disabled
   - When admin types correct name
   - Then "Delete" button becomes enabled

8. **Audit Trail**
   - Given admin deletes group "Engineering"
   - When viewing audit log
   - Then log shows:
     - Action: "USER_GROUP_DELETED"
     - Group name: "Engineering"
     - Deleted by: admin username
     - Timestamp
     - Member count at deletion
     - Permission count at deletion

9. **Deleted Group Not in Lists**
   - Given group "Engineering" is deleted
   - When admin views groups list
   - Then "Engineering" does not appear
   - And total group count decreases by 1

10. **User History Shows Removed Group**
    - Given user was member of "Engineering" group
    - When group is deleted
    - And admin views user's group history
    - Then shows "Engineering (deleted)" with date range

11. **Unauthorized Access**
    - Given user without MANAGE_USERS permission
    - When they attempt to delete group
    - Then they receive 403 Forbidden error

12. **Prevent Accidental Deletion**
    - Given admin attempts to delete group with many members
    - When confirmation dialog appears
    - Then delete button has warning color (red)
    - And requires explicit action (not just click)

## UI/UX (if applicable)

**Delete Group Interface:**
- "Delete Group" danger button on group detail page
- Icon: Trash/delete icon with red color
- Located in actions menu or bottom of page

**Confirmation Dialog:**
- Title: "Delete User Group?"
- Warning icon (red/orange)
- Group information box:
  - Group Name: "Engineering"
  - Members: 15
  - Permissions: 8
- Warning message:
  - "This action cannot be undone."
  - "15 members will lose 8 inherited permissions"
  - "Members will retain their direct permissions"
- Confirmation method (choose one):
  - Option A: Type group name to confirm
  - Option B: Checkbox "I understand the consequences"
- Impact preview (expandable):
  - List of affected users
  - Permissions they will lose
- "Delete Group" danger button (disabled until confirmed)
- "Cancel" secondary button

**After Deletion:**
- Toast notification: "Group deleted successfully"
- Redirect to groups list
- Brief message showing what happened
- Option to undo (if soft delete within time window)

**Impact Preview:**
- Expandable section in confirmation
- Table showing:
  - User Name
  - Current Group Permissions
  - Will Retain (direct permissions)
- Helps admin understand full impact
