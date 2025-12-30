# US-UG-006: View User's Groups

## Story

**As a** Profile Administrator
**I want** to see a user's group memberships and inherited permissions on their detail page
**So that** I can understand the full scope of their access and how they obtained it

## Acceptance Criteria

- [ ] User detail page shows "Groups" section
- [ ] Lists all groups the user belongs to
- [ ] Each group shows name and link to group detail
- [ ] Shows permissions inherited from each group
- [ ] Distinguishes between direct permissions and group-inherited permissions
- [ ] Shows effective permissions summary (union of all sources)
- [ ] Permission source is clearly labeled (Direct, Group: [Group Name], Role)
- [ ] Clicking group name navigates to group detail page
- [ ] Section updates when user is added/removed from groups

## Technical Notes

**API Changes:**
- GET `/api/profiles/{profileId}/users/{userId}/groups`
  - Response: List of `UserGroupMembershipDto`
    ```json
    [
      {
        "id": "uuid",
        "name": "Engineering",
        "description": "All engineering staff",
        "joinedAt": "2025-01-15T10:30:00Z",
        "permissions": [
          {
            "type": "CLIENT_ACCESS",
            "clientId": "uuid",
            "clientName": "TechCo"
          }
        ]
      }
    ]
    ```
  - Returns 403 Forbidden if user lacks VIEW_USERS permission

- GET `/api/profiles/{profileId}/users/{userId}/effective-permissions`
  - Response: List of `EffectivePermissionDto` with source information
    ```json
    [
      {
        "type": "CLIENT_ACCESS",
        "clientId": "uuid",
        "clientName": "TechCo",
        "source": "GROUP",
        "sourceName": "Engineering",
        "sourceId": "group-uuid",
        "grantedAt": "2025-01-15T10:30:00Z"
      },
      {
        "type": "CLIENT_ACCESS",
        "clientId": "uuid2",
        "clientName": "Acme Corp",
        "source": "DIRECT",
        "sourceName": "Direct Assignment",
        "sourceId": null,
        "grantedAt": "2025-01-10T09:00:00Z"
      }
    ]
    ```

**Domain Model:**
- Create `EffectivePermissionCalculator` service
- Method: `calculateEffectivePermissions(UserId): List<EffectivePermission>`
- `EffectivePermission` value object includes source tracking
- Cache effective permissions per user with appropriate TTL

**Permission Sources:**
- DIRECT: Permission assigned directly to user
- GROUP: Permission inherited from group membership
- ROLE: Permission from role-based access (future enhancement)

## Dependencies

- US-UG-002: Add Users to Group must be completed
- US-UG-004: Assign Permissions to Group must be completed
- User detail page must exist

## Test Cases

1. **View User with Multiple Groups**
   - Given user "john@example.com" belongs to "Engineering" and "Leadership"
   - When admin views john's detail page
   - Then "Groups" section shows both groups
   - And each group is clickable link

2. **View Inherited Permissions**
   - Given group "Sales" has CLIENT_ACCESS to "Acme Corp"
   - And user "jane@example.com" is member of "Sales"
   - When admin views jane's detail page
   - Then inherited permissions section shows "Acme Corp" access
   - And source is labeled "Group: Sales"

3. **Distinguish Direct vs Group Permissions**
   - Given user "bob@example.com" has direct CLIENT_ACCESS to "TechCo"
   - And bob is in group "Engineering" with CLIENT_ACCESS to "StartupXYZ"
   - When admin views bob's permissions
   - Then TechCo access shows source "Direct Assignment"
   - And StartupXYZ access shows source "Group: Engineering"

4. **View User with No Groups**
   - Given user "alice@example.com" belongs to no groups
   - When admin views alice's detail page
   - Then "Groups" section shows "No group memberships"
   - And only direct permissions are shown

5. **Effective Permissions Summary**
   - Given user has 2 direct permissions and 3 group permissions
   - When admin views effective permissions tab
   - Then all 5 permissions are listed
   - And each shows its source clearly

6. **Navigate to Group from User Page**
   - Given user is member of "Marketing" group
   - When admin clicks "Marketing" link in user's groups section
   - Then navigates to Marketing group detail page

7. **Permission Count Badge**
   - Given user has 3 direct permissions and 5 group permissions
   - When admin views user detail
   - Then total permission count shows 8
   - And breakdown shows "3 direct, 5 inherited"

8. **Real-time Updates**
   - Given user detail page is open
   - When user is added to new group
   - Then groups section updates automatically
   - And new inherited permissions appear

9. **Duplicate Permission Handling**
   - Given user has direct CLIENT_ACCESS to "Acme Corp"
   - And user's group also grants CLIENT_ACCESS to "Acme Corp"
   - When viewing effective permissions
   - Then "Acme Corp" appears once
   - And shows both sources in detail tooltip/expansion

10. **Unauthorized Access**
    - Given user without VIEW_USERS permission
    - When they attempt to view user groups
    - Then they receive 403 Forbidden error

## UI/UX (if applicable)

**User Detail Page - Groups Section:**
- Section title: "Group Memberships" with count badge
- List or grid of group cards
- Each card shows:
  - Group name (linked)
  - Description (truncated)
  - "Member since" date
  - Permission count badge

**Effective Permissions Tab:**
- Table with columns:
  - Permission Type
  - Resource (Client/Account/Role)
  - Source (Direct, Group: [Name], Role)
  - Granted At
- Filter by source type
- Search/filter by resource name
- Visual indicators:
  - Blue badge for Direct
  - Green badge for Group
  - Purple badge for Role

**Permission Detail Expansion:**
- Click permission row to expand
- Shows full details including who granted it
- For group permissions, link to group detail
- Option to revoke (if direct) or remove from group (if inherited)
