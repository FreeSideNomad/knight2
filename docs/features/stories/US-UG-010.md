# US-UG-010: Effective Permissions View

## Story

**As a** Profile Administrator
**I want** to view a user's effective permissions with their sources (User, Group name, Role)
**So that** I can troubleshoot access issues and audit user access comprehensively

## Acceptance Criteria

- [ ] Effective permissions view shows all user's permissions
- [ ] Each permission displays its source (User, Group: [Name], Role: [Name])
- [ ] Permissions can be filtered by source type
- [ ] Permissions can be filtered by permission type (Client, Account, Role)
- [ ] Permissions can be searched by resource name
- [ ] View distinguishes between single-source and multi-source permissions
- [ ] Multi-source permissions expand to show all sources
- [ ] View is accessible from user detail page
- [ ] Real-time updates when permissions change
- [ ] Export option to download permission report
- [ ] Permission count summary shows breakdown by source

## Acceptance Criteria (continued)

- [ ] Clicking on a source navigates to that source's detail page
- [ ] Visual indicators differentiate source types (colors, icons)
- [ ] Loading state while calculating effective permissions
- [ ] Empty state when user has no permissions

## Technical Notes

**API Changes:**
- GET `/api/profiles/{profileId}/users/{userId}/effective-permissions`
  - Query params: `sourceType`, `permissionType`, `search`
  - Response: List of `EffectivePermissionDto`
    ```json
    [
      {
        "id": "uuid",
        "permissionType": "CLIENT_ACCESS",
        "clientId": "uuid",
        "clientName": "TechCo",
        "accountId": null,
        "accountName": null,
        "roleName": null,
        "sources": [
          {
            "type": "USER",
            "name": "Direct Assignment",
            "sourceId": null,
            "grantedBy": "admin@example.com",
            "grantedAt": "2025-01-10T09:00:00Z"
          },
          {
            "type": "GROUP",
            "name": "Engineering",
            "sourceId": "group-uuid",
            "grantedBy": "manager@example.com",
            "grantedAt": "2025-01-15T10:30:00Z"
          }
        ],
        "effectiveSince": "2025-01-10T09:00:00Z"
      }
    ]
    ```
  - Returns 403 Forbidden if user lacks VIEW_USERS permission

- GET `/api/profiles/{profileId}/users/{userId}/permissions-summary`
  - Response: Summary statistics
    ```json
    {
      "totalPermissions": 15,
      "directPermissions": 5,
      "groupPermissions": 8,
      "rolePermissions": 2,
      "groups": 3,
      "breakdown": {
        "clientAccess": 10,
        "accountAccess": 3,
        "roleBased": 2
      }
    }
    ```

- POST `/api/profiles/{profileId}/users/{userId}/permissions-export`
  - Request body: `{ "format": "CSV" | "PDF" | "JSON" }`
  - Response: File download
  - Generates comprehensive permission report

**Domain Model:**
- Create `EffectivePermission` value object
  - Contains permission details
  - Contains list of sources
  - Methods: `isMultiSource()`, `getPrimarySouce()`, `getAllSources()`
- Create `PermissionSource` value object
  - Type: USER, GROUP, ROLE
  - Name, ID, granted by, granted at
- Service: `EffectivePermissionService`
  - Method: `getEffectivePermissions(UserId): List<EffectivePermission>`
  - Method: `getPermissionSummary(UserId): PermissionSummary`

**Frontend Components:**
- `EffectivePermissionsTable` component
- `PermissionSourceBadge` component
- `PermissionSummaryCard` component
- `PermissionExportDialog` component

## Dependencies

- US-UG-006: View User's Groups must be completed
- US-UG-009: Permission Evaluation with Groups must be completed
- User detail page must exist

## Test Cases

1. **View All Effective Permissions**
   - Given user has 5 direct, 3 group, and 2 role permissions
   - When admin views effective permissions tab
   - Then all 10 permissions are displayed
   - And each shows its source(s)

2. **Filter by Source Type**
   - Given user has permissions from multiple sources
   - When admin selects "Group" filter
   - Then only permissions from groups are shown
   - And other permissions are hidden

3. **Filter by Permission Type**
   - Given user has CLIENT_ACCESS and ACCOUNT_ACCESS permissions
   - When admin selects "Client Access" filter
   - Then only client access permissions are shown
   - And account access permissions are hidden

4. **Search by Resource Name**
   - Given user has access to clients "TechCo", "Acme Corp", "StartupXYZ"
   - When admin types "tech" in search field
   - Then only "TechCo" permission is displayed
   - And other permissions are filtered out

5. **Multi-Source Permission Display**
   - Given user has CLIENT_ACCESS to "TechCo" from both direct and group
   - When viewing effective permissions
   - Then "TechCo" appears once
   - And shows indicator for multiple sources
   - When expanded, shows both sources with details

6. **Single-Source Permission Display**
   - Given user has CLIENT_ACCESS to "Acme Corp" only from "Sales" group
   - When viewing effective permissions
   - Then "Acme Corp" shows single source badge
   - And displays "Group: Sales"

7. **Permission Summary Card**
   - Given user has 15 total permissions from various sources
   - When viewing effective permissions page
   - Then summary card shows:
     - Total: 15
     - Direct: 5
     - Group: 8
     - Role: 2
     - Groups: 3

8. **Navigate to Source**
   - Given permission shows source "Group: Engineering"
   - When admin clicks "Engineering" link
   - Then navigates to Engineering group detail page

9. **Visual Source Indicators**
   - Given permissions from different sources are displayed
   - Then each source has distinct visual indicator:
     - Direct: Blue badge with user icon
     - Group: Green badge with group icon
     - Role: Purple badge with role icon

10. **Real-time Updates**
    - Given admin is viewing user's effective permissions
    - When user is added to new group with permissions
    - Then effective permissions table updates automatically
    - And new permissions appear with "Group: [Name]" source

11. **Export to CSV**
    - Given admin views effective permissions
    - When admin clicks "Export" and selects "CSV"
    - Then CSV file downloads
    - And contains all permissions with source details

12. **Export to PDF**
    - Given admin views effective permissions
    - When admin clicks "Export" and selects "PDF"
    - Then PDF report downloads
    - And contains formatted permission report with summary

13. **Empty State**
    - Given user has no permissions from any source
    - When admin views effective permissions tab
    - Then empty state is displayed
    - And message says "No permissions assigned"

14. **Loading State**
    - Given admin clicks effective permissions tab
    - While permissions are being calculated
    - Then loading skeleton is displayed
    - And "Calculating permissions..." message shows

15. **Permission Granted Details**
    - Given permission with source information
    - When admin hovers over source badge
    - Then tooltip shows:
      - Granted by: [username]
      - Granted at: [timestamp]
      - Source ID (for debugging)

16. **Combine Filters**
    - Given user has many permissions
    - When admin selects "Group" source filter AND "Client Access" type filter
    - Then only client access permissions from groups are shown
    - And all other permissions are hidden

17. **Unauthorized Access**
    - Given user without VIEW_USERS permission
    - When they attempt to view effective permissions
    - Then they receive 403 Forbidden error

## UI/UX (if applicable)

**Effective Permissions Tab (on User Detail Page):**

**Header Section:**
- Tab title: "Effective Permissions"
- Summary card showing:
  - Total permissions count (large)
  - Breakdown by source (smaller chips)
  - Breakdown by type (smaller chips)
- "Export" button with format dropdown
- "Refresh" button

**Filters Section:**
- Source type filter (All, Direct, Group, Role)
- Permission type filter (All, Client Access, Account Access, Role-Based)
- Search field: "Search resources..."
- Active filter chips with X to remove
- "Clear all filters" link

**Permissions Table:**
- Columns:
  - Type (icon + text)
  - Resource (client/account/role name)
  - Source(s) (badge(s), expandable if multiple)
  - Granted At (earliest)
  - Actions (navigate to source)
- Sortable columns
- Expandable rows for multi-source permissions
- Pagination if many permissions

**Source Badges:**
- Direct: Blue background, user icon, "Direct"
- Group: Green background, group icon, "Group: [Name]"
- Role: Purple background, shield icon, "Role: [Name]"
- Multi-source: Badge with "+2" indicator, click to expand

**Expanded Multi-Source Row:**
- Shows table of all sources:
  - Source type
  - Source name (linked)
  - Granted by
  - Granted at
- "View all sources" link if more than 3

**Export Dialog:**
- Title: "Export Permissions Report"
- Format options: CSV, PDF, JSON
- Options:
  - Include summary
  - Include source details
  - Include timestamps
- "Export" button
- "Cancel" button

**Empty State:**
- Icon: Lock or shield
- Message: "No permissions assigned"
- Subtext: "This user doesn't have any permissions from any source"
- Action: "Assign Permissions" button (if user has permission)

**Loading State:**
- Skeleton loader for table rows
- Message: "Calculating effective permissions..."
- Progress indicator

**Mobile Responsive:**
- Card view instead of table on mobile
- Filters collapse into dropdown menu
- Export moved to actions menu
- Touch-friendly interaction areas
