# US-AG-004: View Account Groups

## Story

**As a** service profile administrator
**I want** to view a list of all account groups for a service profile
**So that** I can see what groups exist and navigate to manage them

## Acceptance Criteria

- [ ] List displays all account groups for the current service profile
- [ ] Each group shows: name, description, account count
- [ ] List supports sorting by name, account count, or creation date
- [ ] List supports searching/filtering by group name or description
- [ ] List supports pagination for profiles with many groups
- [ ] Click on a group navigates to group details page
- [ ] Empty state displays when no groups exist with "Create Group" CTA
- [ ] Account count is displayed accurately and updates in real-time

## Technical Notes

### API Endpoints
- GET `/api/profiles/{profileId}/account-groups`
  - Query params: `search`, `sort`, `page`, `size`
  - Response: Page<AccountGroupSummaryDto>

### DTOs
```java
AccountGroupSummaryDto {
  UUID id;
  String name;
  String description;
  int accountCount;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;
}
```

### Query Optimization
- Use JOIN with COUNT to get account counts efficiently
- Add index on account_groups(profile_id, name)
- Add index on account_groups(created_at)

## Dependencies

- US-AG-001: Create Account Group (to have groups to display)

## Test Cases

1. **View empty account groups list**
   - Navigate to account groups page for profile with no groups
   - Verify empty state displays
   - Verify "Create Account Group" button is visible

2. **View list with multiple account groups**
   - Create 3 account groups with different account counts
   - Navigate to account groups page
   - Verify all 3 groups display with correct information

3. **Search for account group by name**
   - Create groups: "VIP Accounts", "Standard Accounts", "Test Accounts"
   - Search for "VIP"
   - Verify only "VIP Accounts" displays

4. **Sort account groups by account count**
   - Create groups with 5, 2, and 10 accounts respectively
   - Click sort by account count ascending
   - Verify groups display in order: 2, 5, 10

5. **Navigate to group details**
   - Click on a group in the list
   - Verify navigation to group details page
   - Verify correct group information displays

6. **Pagination with many groups**
   - Create 25 account groups
   - Set page size to 10
   - Verify pagination controls display
   - Navigate through pages

## UI/UX

### Account Groups List Page
- Header:
  - Title: "Account Groups"
  - "Create Account Group" button (primary action)
- Search bar:
  - Placeholder: "Search groups by name or description"
  - Search icon
- Table/Card view with columns:
  - Name (sortable, bold)
  - Description (truncated with tooltip if long)
  - Accounts (sortable, badge showing count)
  - Actions (view/edit/delete icons)
- Sorting controls in column headers
- Pagination footer showing: "Showing X-Y of Z groups"

### Empty State
- Icon (group/folder icon)
- Message: "No account groups yet"
- Subtext: "Create account groups to organize accounts for easier permission management"
- "Create Account Group" button (primary)

### Responsive Design
- Desktop: Table view with all columns
- Tablet: Card view with condensed information
- Mobile: List view with name and account count, expandable for description
