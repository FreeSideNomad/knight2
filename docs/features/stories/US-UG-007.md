# US-UG-007: Edit User Group

## Story

**As a** Profile Administrator
**I want** to edit a user group's name and description
**So that** I can keep group information accurate and up-to-date

## Acceptance Criteria

- [ ] Edit button available on group detail page
- [ ] Edit form pre-populates with current name and description
- [ ] Name field validation same as creation (unique, max length)
- [ ] Description field validation same as creation (max length)
- [ ] Name uniqueness validated in real-time
- [ ] Success message displayed after save
- [ ] Group detail page updates with new values
- [ ] Group list reflects updated name/description
- [ ] Audit log records changes with timestamp and actor
- [ ] Cancel button reverts changes without saving

## Technical Notes

**API Changes:**
- PUT `/api/profiles/{profileId}/user-groups/{groupId}`
  - Request body: `{ "name": "string", "description": "string" }`
  - Response: Updated `UserGroupDto`
  - Returns 200 OK on success
  - Returns 400 Bad Request if name conflicts with another group
  - Returns 403 Forbidden if user lacks MANAGE_USERS permission
  - Returns 404 Not Found if group doesn't exist

- PATCH `/api/profiles/{profileId}/user-groups/{groupId}` (alternative)
  - Allows partial updates
  - Request body: `{ "name": "string" }` or `{ "description": "string" }`

**Domain Model:**
- Add `updateName(String)` method to `UserGroup` aggregate
- Add `updateDescription(String)` method to `UserGroup` aggregate
- Validate name uniqueness in domain service
- Emit `UserGroupUpdated` domain event

**Validation:**
- Name must be unique within profile (excluding current group)
- Name required, max 100 characters
- Description optional, max 500 characters

## Dependencies

- US-UG-001: Create User Group must be completed
- US-UG-005: View User Groups must be completed

## Test Cases

1. **Edit Group Name**
   - Given group "Engineering Team" exists
   - When admin changes name to "Engineering Department"
   - And saves changes
   - Then group name is updated
   - And success message displays
   - And group list shows new name

2. **Edit Group Description**
   - Given group has description "All engineers"
   - When admin changes description to "All engineering staff including contractors"
   - And saves changes
   - Then description is updated
   - And group detail shows new description

3. **Edit Both Name and Description**
   - Given group "Sales" with description "Sales team"
   - When admin changes name to "Sales Department" and description to "All sales staff"
   - And saves changes
   - Then both fields are updated
   - And audit log shows both changes

4. **Prevent Duplicate Name**
   - Given groups "Engineering" and "Sales" exist
   - When admin tries to rename "Sales" to "Engineering"
   - Then validation error displays
   - And message shows "Group name already exists"
   - And changes are not saved

5. **Name Required Validation**
   - Given admin is editing group
   - When admin clears the name field
   - And attempts to save
   - Then validation error "Name is required" displays
   - And form is not submitted

6. **Max Length Validation**
   - Given admin is editing group description
   - When admin enters 501 characters
   - Then validation error displays
   - And form cannot be submitted

7. **Cancel Edit**
   - Given admin opens edit form for group "Marketing"
   - When admin changes name to "Sales"
   - And clicks "Cancel"
   - Then changes are discarded
   - And group name remains "Marketing"
   - And returns to group detail page

8. **Real-time Name Validation**
   - Given groups "Engineering" and "Sales" exist
   - When admin edits "Sales" and types "Engineering" in name field
   - Then validation error appears immediately
   - Before form submission

9. **Edit Does Not Affect Members or Permissions**
   - Given group "Support" has 10 members and 5 permissions
   - When admin changes group name to "Customer Support"
   - Then members remain unchanged
   - And permissions remain unchanged
   - And member count shows 10
   - And permission count shows 5

10. **Unauthorized Access**
    - Given user without MANAGE_USERS permission
    - When they attempt to edit group
    - Then they receive 403 Forbidden error

11. **Audit Trail**
    - Given admin edits group name
    - When viewing audit log
    - Then log shows:
      - Who made the change
      - When it was made
      - Old value and new value
      - Action type: "USER_GROUP_UPDATED"

## UI/UX (if applicable)

**Edit Group Interface:**
- "Edit" button on group detail page (top right)
- Opens edit form (modal or inline)
- Form fields:
  - Name field with current value
  - Description textarea with current value
  - Character counters on both fields
- Real-time validation feedback
- "Save Changes" primary button
- "Cancel" secondary button
- Loading state during save

**Edit Form Variations:**
- Option 1: Modal dialog overlay
- Option 2: Inline editing on detail page
- Option 3: Dedicated edit page

**Success Feedback:**
- Toast notification: "Group updated successfully"
- Form closes automatically
- Group detail page refreshes with new values
- Visual highlight on changed fields (brief animation)

**Validation Display:**
- Inline error messages below fields
- Red border on invalid fields
- Green checkmark on valid unique name
- Save button disabled while invalid
