# US-AG-005: Edit Account Group

## Story

**As a** service profile administrator
**I want** to edit an account group's name and description
**So that** I can keep group information accurate and up-to-date

## Acceptance Criteria

- [ ] Edit button is available on account group details page
- [ ] Form pre-populates with current name and description
- [ ] Name validation enforces uniqueness within profile
- [ ] Name validation enforces character limit (max 100)
- [ ] Description validation enforces character limit (max 500)
- [ ] Cannot save without making changes
- [ ] Success message displays after successful update
- [ ] Updated information displays immediately
- [ ] Audit trail records who made the change and when

## Technical Notes

### API Endpoints
- PUT `/api/profiles/{profileId}/account-groups/{groupId}`
  - Request body: `{ "name": string, "description": string }`
  - Response: Updated AccountGroupDto
- GET `/api/profiles/{profileId}/account-groups/{groupId}`
  - Response: AccountGroupDto with full details

### Database Changes
- Add audit columns to account_groups table:
  - `created_by` (UUID, foreign key to users)
  - `updated_by` (UUID, foreign key to users)

### Domain Model
- Add `updateDetails(String name, String description)` method to AccountGroup
- Create `UpdateAccountGroupCommand`
- Validate name uniqueness in domain service
- Publish `AccountGroupUpdatedEvent`

### Validation
- Name uniqueness check excluding current group
- Trim whitespace from name and description
- Prevent saving if no changes detected

## Dependencies

- US-AG-001: Create Account Group (must have groups to edit)

## Test Cases

1. **Edit group name successfully**
   - Open edit form for existing group
   - Change name from "Group A" to "Group B"
   - Click Save
   - Verify name updates in UI
   - Verify name updates in database

2. **Edit group description successfully**
   - Open edit form
   - Update description
   - Click Save
   - Verify description updates

3. **Attempt to edit name to duplicate**
   - Create groups "Group A" and "Group B"
   - Edit "Group B" name to "Group A"
   - Verify validation error displays
   - Verify save is prevented

4. **Edit with no changes**
   - Open edit form
   - Don't change any values
   - Verify Save button is disabled or shows "No changes" message

5. **Edit name exceeding character limit**
   - Input name with 101 characters
   - Attempt to save
   - Verify validation error displays

6. **Cancel edit operation**
   - Open edit form
   - Make changes
   - Click Cancel
   - Verify changes are not saved
   - Verify original values remain

7. **Audit trail verification**
   - Edit group as User A
   - Query audit information
   - Verify updated_by shows User A
   - Verify updated_at shows current timestamp

## UI/UX

### Edit Account Group Form
- Same form layout as create
- Pre-populated fields:
  - Name (text input with current value)
  - Description (textarea with current value)
- Visual indicator if field has been modified
- Character counters for both fields
- Buttons:
  - Save Changes (primary, disabled if no changes)
  - Cancel (secondary)

### Edit Access Points
1. Edit button on group details page header
2. Edit icon in account groups list
3. Edit option in group context menu

### Validation Display
- Inline validation as user types
- Show uniqueness check after blur event
- Highlight fields with errors in red
- Show success checkmark for valid fields

### Success State
- Toast notification: "Account group updated successfully"
- Return to group details page or stay on edit form (configurable)
- Show last updated timestamp and user
