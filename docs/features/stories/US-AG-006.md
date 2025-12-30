# US-AG-006: Delete Account Group

## Story

**As a** service profile administrator
**I want** to delete an account group that is no longer needed
**So that** I can maintain a clean and relevant list of account groups

## Acceptance Criteria

- [ ] Delete button is available on account group details page
- [ ] Confirmation dialog displays before deletion
- [ ] Warning displays if group is used in any permissions
- [ ] Warning shows count of permissions that will be affected
- [ ] User must type group name to confirm deletion if group is in use
- [ ] Cannot delete if user cancels confirmation
- [ ] Success message displays after successful deletion
- [ ] User is redirected to account groups list after deletion
- [ ] Group is permanently removed from database
- [ ] Permissions using the deleted group are handled appropriately

## Technical Notes

### API Endpoints
- DELETE `/api/profiles/{profileId}/account-groups/{groupId}`
  - Response: 204 No Content or error if in use
- GET `/api/profiles/{profileId}/account-groups/{groupId}/usage`
  - Response: `{ "permissionCount": number, "permissions": [PermissionSummaryDto] }`

### Deletion Strategy Options
1. **Soft Delete**: Mark as deleted, keep data for audit
2. **Hard Delete with Cascade**: Remove group and update permissions
3. **Prevent Delete if In Use**: Require removal from all permissions first

**Recommended**: Option 3 initially, with Option 2 as enhancement

### Database Constraints
- Add foreign key with appropriate cascade behavior on account_group_members
- Consider soft delete flag: `deleted_at` (TIMESTAMP, nullable)

### Domain Model
- Add `delete()` method to AccountGroup aggregate
- Create `DeleteAccountGroupCommand`
- Add domain rule: cannot delete if referenced by permissions
- Publish `AccountGroupDeletedEvent`

### Permission Impact
- Query all permissions referencing this group
- Options for handling:
  - Prevent deletion if in use (recommended for MVP)
  - Remove group from permissions automatically
  - Replace group with individual accounts in permissions

## Dependencies

- US-AG-001: Create Account Group (must have groups to delete)
- US-AG-007: Use Account Group in Permissions (to check usage)

## Test Cases

1. **Delete unused account group**
   - Create group with no permissions using it
   - Click Delete button
   - Confirm deletion
   - Verify group is removed from list
   - Verify group cannot be retrieved via API

2. **Attempt to delete group in use**
   - Create group and use it in a permission
   - Click Delete button
   - Verify warning displays showing permission count
   - Confirm deletion with name confirmation
   - Verify appropriate action based on strategy

3. **Cancel delete operation**
   - Click Delete button
   - Click Cancel in confirmation dialog
   - Verify group still exists
   - Verify user remains on group details page

4. **Delete with name confirmation**
   - Attempt to delete group "Important Accounts" used in permissions
   - Type incorrect name in confirmation field
   - Verify Delete button remains disabled
   - Type correct name
   - Verify Delete button becomes enabled

5. **Cascade delete account memberships**
   - Create group with 5 accounts
   - Delete the group
   - Verify all account_group_members records are removed
   - Verify accounts themselves still exist

6. **Verify permission impact**
   - Create group used in 3 permissions
   - Delete the group (if allowed)
   - Verify those 3 permissions are updated appropriately
   - Or verify deletion is prevented with clear message

## UI/UX

### Delete Button Location
- Group details page header (trash icon, destructive styling)
- Account groups list (delete icon in actions column)
- Group context menu

### Confirmation Dialog - Unused Group
- Title: "Delete Account Group?"
- Message: "Are you sure you want to delete '[Group Name]'? This action cannot be undone."
- Buttons:
  - Delete (destructive, red)
  - Cancel (secondary)

### Confirmation Dialog - Group In Use
- Title: "Delete Account Group?"
- Warning icon (amber/red)
- Message:
  - "This account group is currently used in X permission(s)."
  - List of affected permissions (if few) or "View affected permissions" link
  - "Deleting this group will [impact description based on strategy]."
  - "To confirm, please type the group name: [Group Name]"
- Text input for name confirmation
- Buttons:
  - Delete (destructive, red, disabled until name matches)
  - Cancel (secondary)

### Alternative: Prevent Delete if In Use
- Prevent delete button from being clickable
- Show tooltip: "Cannot delete group used in permissions"
- Provide link to "View Usage" (US-AG-008)
- Message: "Remove this group from all permissions before deleting"

### Success State
- Toast notification: "Account group deleted successfully"
- Redirect to account groups list
- Optional: Show undo option for soft delete (future enhancement)
