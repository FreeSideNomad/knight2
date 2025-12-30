# US-AG-003: Remove Accounts from Group

## Story

**As a** service profile administrator
**I want** to remove accounts from an account group
**So that** I can maintain accurate account collections and update permission scope

## Acceptance Criteria

- [ ] Remove button/icon displays next to each account in the group
- [ ] Confirmation dialog appears before removal
- [ ] Confirmation shows account name and warns about permission impact
- [ ] User can cancel the removal operation
- [ ] Success message displays after successful removal
- [ ] Account count for the group updates immediately
- [ ] Removed account no longer appears in group member list
- [ ] Permissions using this group immediately reflect the change
- [ ] User can remove multiple accounts at once via bulk selection

## Technical Notes

### API Endpoints
- DELETE `/api/profiles/{profileId}/account-groups/{groupId}/accounts/{accountId}`
  - Response: Updated AccountGroupDto with account count
- DELETE `/api/profiles/{profileId}/account-groups/{groupId}/accounts`
  - Request body: `{ "accountIds": [UUID] }`
  - Response: Updated AccountGroupDto with account count

### Domain Model
- Add `removeAccount(ClientId)` method to AccountGroup aggregate
- Add `removeAccounts(List<ClientId>)` method for bulk removal
- Create `RemoveAccountFromGroupCommand`
- Publish `AccountRemovedFromGroupEvent` for permission updates

### Domain Events
- `AccountRemovedFromGroupEvent` containing:
  - accountGroupId
  - accountId (or accountIds for bulk)
  - profileId
  - removedAt timestamp

## Dependencies

- US-AG-002: Add Accounts to Group (must have accounts to remove)
- US-AG-009: Dynamic Group Membership (for real-time permission updates)

## Test Cases

1. **Remove single account from group**
   - Click remove button next to an account
   - Confirm removal in dialog
   - Verify account is removed from group
   - Verify account count decrements by 1

2. **Cancel account removal**
   - Click remove button
   - Click Cancel in confirmation dialog
   - Verify account remains in group

3. **Remove multiple accounts via bulk selection**
   - Select 3 accounts using checkboxes
   - Click "Remove Selected" button
   - Confirm removal
   - Verify all 3 accounts are removed
   - Verify account count decrements by 3

4. **Verify permission impact warning**
   - Remove account from group used in permissions
   - Verify confirmation dialog mentions permission impact
   - Confirm removal
   - Verify permissions no longer grant access to removed account

5. **Remove last account from group**
   - Remove the only account in a group
   - Verify group still exists with 0 accounts
   - Verify group can still be used and have accounts added later

## UI/UX

### Remove Account Interface
- Individual remove:
  - Trash/X icon next to each account in group members list
  - Hover shows "Remove from group" tooltip
- Bulk remove:
  - Checkboxes next to each account
  - "Remove Selected" button appears when items are checked
  - Shows count of selected items

### Confirmation Dialog
- Title: "Remove Account(s) from Group?"
- Message:
  - "Are you sure you want to remove [Account Name] from [Group Name]?"
  - For bulk: "Are you sure you want to remove X accounts from [Group Name]?"
  - Warning: "This will immediately affect any permissions using this group."
- Buttons:
  - Remove (destructive action, red)
  - Cancel (secondary)
