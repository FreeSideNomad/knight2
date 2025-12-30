# US-AG-008: View Group Usage

## Story

**As a** service profile administrator
**I want** to see which permissions and users are using a specific account group
**So that** I can understand the impact of changes to the group before making them

## Acceptance Criteria

- [ ] "Usage" tab or section displays on account group details page
- [ ] Shows count of permissions using this group
- [ ] Shows list of users who have permissions via this group
- [ ] Each permission entry shows: user name, permission type, granted date
- [ ] Can navigate to user's permission details from usage list
- [ ] Usage information updates in real-time when permissions change
- [ ] Empty state displays if group is not used in any permissions
- [ ] Can filter/search usage by user name or permission type

## Technical Notes

### API Endpoints
- GET `/api/profiles/{profileId}/account-groups/{groupId}/usage`
  - Response:
  ```json
  {
    "accountGroupId": "uuid",
    "accountGroupName": "string",
    "permissionCount": "number",
    "userCount": "number",
    "permissions": [
      {
        "permissionId": "uuid",
        "userId": "uuid",
        "userName": "string",
        "permissionTypes": ["VIEW", "EDIT"],
        "grantedAt": "timestamp",
        "grantedBy": "string"
      }
    ]
  }
  ```

### Query Optimization
- Create database view or materialized view for efficient querying
- Index on account_group_id in permissions table
- Consider caching usage counts for large datasets

### Real-time Updates
- Use WebSocket or Server-Sent Events for real-time updates
- Or implement polling with reasonable interval
- Update usage count badges when permissions change

## Dependencies

- US-AG-001: Create Account Group (must have groups)
- US-AG-007: Use Account Group in Permissions (to have usage to display)

## Test Cases

1. **View usage for group with permissions**
   - Create account group "Sales Accounts"
   - Assign permissions to 3 users using this group
   - Navigate to group details and open Usage tab
   - Verify all 3 permissions display with correct user information

2. **View usage for unused group**
   - Create account group with no permissions
   - Navigate to Usage tab
   - Verify empty state displays
   - Verify message suggests creating permissions

3. **Navigate to user permission from usage**
   - View usage list showing User A
   - Click on User A's entry
   - Verify navigation to User A's permission details page

4. **Search usage by user name**
   - Group has permissions for users: Alice, Bob, Charlie
   - Enter "Ali" in search box
   - Verify only Alice's permission displays

5. **Filter usage by permission type**
   - Group has permissions: User A (VIEW), User B (VIEW, EDIT), User C (EDIT)
   - Filter by EDIT permission
   - Verify only User B and User C display

6. **Real-time usage update**
   - View usage tab showing 3 permissions
   - In another window/session, add new permission using this group
   - Verify usage count updates to 4 without page refresh

7. **Usage count accuracy**
   - Create group and assign to 5 users with different permission levels
   - Verify usage count shows 5
   - Remove 2 permissions
   - Verify usage count updates to 3

## UI/UX

### Group Details Page - Usage Tab
- Tab navigation: Details | Accounts | Usage
- Usage tab contains:

#### Summary Section
- Cards showing:
  - "Used in X Permissions"
  - "Granted to X Users"
  - "Last permission added: [date]"

#### Permissions Using This Group
- Table/List view with columns:
  - User (name, avatar if available)
  - Permissions (badges: VIEW, EDIT, DELETE)
  - Granted On (date)
  - Granted By (admin who created permission)
  - Actions (view details, revoke)
- Search bar: "Search by user name"
- Filter dropdown: "All Permissions", "View Only", "Edit", "Delete"
- Sort options: By user name, by date, by permission type

#### Empty State
- Icon (chain link or group icon)
- Message: "This account group is not used in any permissions yet"
- Subtext: "Create permissions using this group to grant users access to all accounts in the group"
- CTA button: "Create Permission"

### Visual Indicators
- Badge showing usage count on group details header
- Color coding:
  - Green badge: Group is actively used
  - Gray badge: Group is not used
- Warning indicator if group is heavily used (impacts delete/modify operations)

### Responsive Design
- Desktop: Full table with all columns
- Tablet: Condensed view with essential columns
- Mobile: Card-based list with expandable details

### Additional Features
- Export usage report (CSV/PDF)
- "Copy to clipboard" for sharing usage summary
- Quick actions: "Revoke all permissions" (with strong confirmation)
