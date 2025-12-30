# US-AG-002: Add Accounts to Group

## Story

**As a** service profile administrator
**I want** to add multiple accounts to an account group
**So that** I can build collections of accounts for permission assignment

## Acceptance Criteria

- [ ] Account picker displays all accounts not currently in the group
- [ ] User can search/filter accounts by name or account number
- [ ] User can select multiple accounts at once
- [ ] Selected accounts are highlighted/checked
- [ ] Add button is enabled only when at least one account is selected
- [ ] Success message displays showing number of accounts added
- [ ] Account count for the group updates immediately
- [ ] Same account can be added to multiple different groups
- [ ] Duplicate accounts cannot be added to the same group

## Technical Notes

### Database Schema
- Create junction table: `account_group_members`
  - `id` (UUID, primary key)
  - `account_group_id` (UUID, foreign key to account_groups)
  - `account_id` (UUID, foreign key to client_accounts)
  - `added_at` (TIMESTAMP)
  - Unique constraint on (account_group_id, account_id)

### API Endpoints
- POST `/api/profiles/{profileId}/account-groups/{groupId}/accounts`
  - Request body: `{ "accountIds": [UUID] }`
  - Response: Updated AccountGroupDto with account count
- GET `/api/profiles/{profileId}/account-groups/{groupId}/available-accounts`
  - Query params: `search`, `page`, `size`
  - Response: Page of available accounts not in the group

### Domain Model
- Add `addAccounts(List<ClientId>)` method to AccountGroup aggregate
- Create `AddAccountsToGroupCommand`
- Implement domain validation for duplicates

## Dependencies

- US-AG-001: Create Account Group (must have groups to add accounts to)

## Test Cases

1. **Add single account to group**
   - Select one account from picker
   - Click Add button
   - Verify account is added to group
   - Verify account count increments by 1

2. **Add multiple accounts to group**
   - Select 5 accounts from picker
   - Click Add button
   - Verify all 5 accounts are added
   - Verify account count increments by 5

3. **Search for accounts in picker**
   - Enter search term
   - Verify filtered results display matching accounts
   - Select and add filtered account

4. **Add same account to different groups**
   - Add Account A to Group 1
   - Add Account A to Group 2
   - Verify Account A appears in both groups

5. **Attempt to add duplicate account to same group**
   - Add Account A to Group 1
   - Attempt to add Account A to Group 1 again
   - Verify error message or account is silently skipped

## UI/UX

### Add Accounts Interface
- Account picker with:
  - Search box at top
  - Checkbox list of available accounts
  - Account name and account number displayed
  - Pagination controls
  - "Select All" and "Clear All" options
- Action buttons:
  - Add Selected (primary, shows count of selected)
  - Cancel (secondary)
- After adding:
  - Success toast notification
  - Picker closes or clears selection
  - Group details page refreshes to show new accounts
