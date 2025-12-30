# US-AG-007: Use Account Group in Permissions

## Story

**As a** service profile administrator
**I want** to use account groups when assigning permissions to users
**So that** I can efficiently grant access to multiple accounts without selecting them individually

## Acceptance Criteria

- [ ] Permission assignment form includes option to select account group
- [ ] User can choose between "Individual Accounts" or "Account Group"
- [ ] Account group dropdown displays all available groups for the profile
- [ ] Selected group shows account count as preview
- [ ] Cannot select both individual accounts and account group simultaneously
- [ ] Permissions saved with account group reference stored correctly
- [ ] Users with group-based permissions can access all accounts in the group
- [ ] Permission display shows account group name, not individual accounts
- [ ] Can expand/view which accounts are included in the group

## Technical Notes

### Database Schema
- Modify `permission_policies` table or create new structure:
  - Add `account_selection_type` (ENUM: 'INDIVIDUAL', 'GROUP')
  - Add `account_group_id` (UUID, foreign key to account_groups, nullable)
  - Keep existing `account_id` references for individual selection
  - Constraint: if type=GROUP, account_group_id must be set
  - Constraint: if type=INDIVIDUAL, individual account references must exist

Alternative: Separate tables for different permission types

### API Endpoints
- POST `/api/profiles/{profileId}/permissions`
  - Request body enhanced:
  ```json
  {
    "userId": "uuid",
    "accountSelectionType": "GROUP",
    "accountGroupId": "uuid",
    "permissions": ["VIEW", "EDIT"]
  }
  ```
- GET `/api/profiles/{profileId}/account-groups`
  - For populating group dropdown

### Domain Model
- Create `AccountSelection` value object with variants:
  - `IndividualAccountSelection(Set<ClientId>)`
  - `AccountGroupSelection(AccountGroupId)`
- Modify `PermissionPolicy` aggregate to use AccountSelection
- Update authorization logic to resolve group membership

### Authorization Service
- When checking permissions, resolve account group to current members
- Cache group membership for performance
- Invalidate cache when group membership changes

## Dependencies

- US-AG-001: Create Account Group (must have groups to use)
- US-AG-002: Add Accounts to Group (groups need accounts)
- US-AG-004: View Account Groups (to select from available groups)

## Test Cases

1. **Assign permission using account group**
   - Open create permission form
   - Select "Account Group" option
   - Choose a group with 5 accounts
   - Assign VIEW permission to a user
   - Save permission
   - Verify user can access all 5 accounts

2. **Switch between individual and group selection**
   - Start with "Individual Accounts" selected
   - Pick 3 individual accounts
   - Switch to "Account Group"
   - Verify individual selections are cleared
   - Select a group
   - Switch back to "Individual Accounts"
   - Verify group selection is cleared

3. **Display group-based permission**
   - Create permission using account group "VIP Accounts"
   - View permissions list
   - Verify permission shows "VIP Accounts (5 accounts)" instead of individual account names

4. **Expand to view group accounts**
   - View permission using account group
   - Click expand/details button
   - Verify list of current accounts in group displays

5. **Validate group selection**
   - Attempt to save permission with "Account Group" type but no group selected
   - Verify validation error displays

6. **Permission inheritance from group**
   - Create group with Accounts A, B, C
   - Assign permission to User 1 using this group
   - User 1 authenticates and requests access to Account B
   - Verify authorization succeeds
   - Verify authorization check resolves group membership

## UI/UX

### Permission Assignment Form Enhancement

#### Account Selection Section
- Radio button group:
  - ( ) Individual Accounts
  - ( ) Account Group

#### When "Individual Accounts" selected:
- Multi-select dropdown or transfer list
- Search functionality
- Selected count display

#### When "Account Group" selected:
- Single-select dropdown
- Each option shows: "Group Name (X accounts)"
- Preview panel showing:
  - Group description
  - Current account count
  - "View accounts" link to expand list

### Permission Display
- For individual: "Accounts: Account A, Account B, Account C"
- For group: "Account Group: VIP Accounts (5 accounts)" with expand icon
- Expanded view shows:
  - Group name and description
  - Table of current accounts in group
  - Note: "Access reflects current group membership"

### Visual Indicators
- Badge/icon to distinguish group-based vs individual permissions
- Group icon next to group-based permissions
- Tooltip: "This permission uses an account group. Access automatically updates when group membership changes."

### Validation
- Clear error messages for incomplete selections
- Disable Save button until valid selection made
- Warning if switching selection types with unsaved changes
