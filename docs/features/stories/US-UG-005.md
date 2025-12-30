# US-UG-005: View User Groups

## Story

**As a** Profile Administrator
**I want** to view a list of all user groups with their name, description, member count, and permission count
**So that** I can understand the group structure and quickly find groups to manage

## Acceptance Criteria

- [ ] User groups list displays all groups in the profile
- [ ] Each group shows name, description, member count, and permission count
- [ ] List supports sorting by name, member count, or permission count
- [ ] List supports search/filter by group name
- [ ] List supports pagination for profiles with many groups
- [ ] Clicking a group navigates to group detail page
- [ ] Empty state shown when no groups exist
- [ ] "Create Group" button prominently displayed
- [ ] List refreshes after creating, editing, or deleting groups

## Technical Notes

**API Changes:**
- GET `/api/profiles/{profileId}/user-groups`
  - Query params: `search`, `sort`, `page`, `size`
  - Response: Paginated list of `UserGroupSummaryDto`
    ```json
    {
      "content": [
        {
          "id": "uuid",
          "name": "Engineering",
          "description": "All engineering staff",
          "memberCount": 15,
          "permissionCount": 8,
          "createdAt": "2025-01-15T10:30:00Z",
          "updatedAt": "2025-01-20T14:45:00Z"
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 45,
      "totalPages": 3
    }
    ```
  - Returns 403 Forbidden if user lacks VIEW_USERS permission
  - Supports sorting by: name, memberCount, permissionCount, createdAt

**Performance Considerations:**
- Use database queries with COUNT subqueries for member and permission counts
- Index group names for search performance
- Cache group summary data with 5-minute TTL
- Invalidate cache on group modifications

**Domain Model:**
- Create `UserGroupSummary` value object for list view
- Repository method: `findAllByProfileId(ProfileId, PageRequest)`
- Repository method: `searchByName(ProfileId, String, PageRequest)`

## Dependencies

- US-UG-001: Create User Group must be completed
- User must have VIEW_USERS or MANAGE_USERS permission

## Test Cases

1. **View All Groups**
   - Given profile has 5 user groups
   - When admin navigates to groups page
   - Then all 5 groups are displayed
   - And each shows name, description, member count, permission count

2. **Search Groups by Name**
   - Given profile has groups "Engineering", "Sales", "Marketing"
   - When admin types "eng" in search field
   - Then only "Engineering" group is displayed
   - And other groups are filtered out

3. **Sort by Member Count**
   - Given profile has multiple groups with varying member counts
   - When admin clicks "Member Count" column header
   - Then groups are sorted by member count descending
   - And clicking again reverses the sort order

4. **Sort by Name**
   - Given profile has groups "Zebra", "Alpha", "Beta"
   - When admin sorts by name ascending
   - Then groups appear in order: Alpha, Beta, Zebra

5. **Pagination**
   - Given profile has 50 groups with page size 20
   - When admin views groups list
   - Then first 20 groups are displayed
   - And pagination controls show 3 pages
   - When admin clicks page 2
   - Then groups 21-40 are displayed

6. **Empty State**
   - Given profile has no user groups
   - When admin navigates to groups page
   - Then empty state message is displayed
   - And "Create Your First Group" button is shown

7. **Navigate to Group Detail**
   - Given groups list is displayed
   - When admin clicks on "Engineering" group
   - Then navigates to group detail page
   - And group detail shows full information

8. **Member Count Accuracy**
   - Given group "Sales" has 10 members
   - When admin adds 2 more members
   - And returns to groups list
   - Then "Sales" shows member count of 12

9. **Permission Count Accuracy**
   - Given group "Marketing" has 5 permissions
   - When admin removes 2 permissions
   - And returns to groups list
   - Then "Marketing" shows permission count of 3

10. **Unauthorized Access**
    - Given user without VIEW_USERS permission
    - When they attempt to view groups list
    - Then they receive 403 Forbidden error

## UI/UX (if applicable)

**User Groups List Page:**
- Page title: "User Groups"
- "Create Group" primary button (top right)
- Search field: "Search groups..." (top left)
- Table with columns:
  - Name (sortable, clickable)
  - Description (truncated if long)
  - Members (sortable, badge with count)
  - Permissions (sortable, badge with count)
  - Actions (view/edit/delete icons)
- Pagination controls at bottom
- Loading skeleton while fetching data

**Empty State:**
- Icon representing groups
- Message: "No user groups yet"
- Subtext: "Create groups to organize users and manage permissions efficiently"
- "Create Your First Group" button

**Group Row Hover:**
- Highlight row on hover
- Show pointer cursor
- Reveal action buttons
