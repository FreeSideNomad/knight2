# US-UG-001: Create User Group

## Story

**As a** Profile Administrator
**I want** to create user groups with a unique name and description
**So that** I can organize users and manage permissions at the group level

## Acceptance Criteria

- [ ] Group creation form includes name field (required, max 100 characters)
- [ ] Group creation form includes description field (optional, max 500 characters)
- [ ] Group name must be unique within the profile
- [ ] Group name validation shows immediate feedback for duplicates
- [ ] Successfully created group appears in the user groups list
- [ ] Success message displayed after group creation
- [ ] User is redirected to group detail page after creation
- [ ] Audit log records group creation with creator and timestamp

## Technical Notes

**Database Changes:**
- Create `user_groups` table with columns:
  - `id` (UUID, primary key)
  - `profile_id` (UUID, foreign key to profiles)
  - `name` (VARCHAR(100), not null)
  - `description` (VARCHAR(500), nullable)
  - `created_at` (TIMESTAMP)
  - `created_by` (VARCHAR(255))
  - `updated_at` (TIMESTAMP)
  - `updated_by` (VARCHAR(255))
- Add unique constraint on (profile_id, name)

**API Changes:**
- POST `/api/profiles/{profileId}/user-groups`
  - Request body: `{ "name": "string", "description": "string" }`
  - Response: `UserGroupDto` with id, name, description, timestamps
  - Returns 201 Created on success
  - Returns 400 Bad Request if name already exists
  - Returns 403 Forbidden if user lacks MANAGE_USERS permission

**Domain Model:**
- Create `UserGroup` aggregate in domain layer
- Create `UserGroupRepository` interface
- Add validation for name uniqueness in domain service

## Dependencies

- Profile service must be operational
- User permission system must support MANAGE_USERS permission

## Test Cases

1. **Valid Group Creation**
   - Given a user with MANAGE_USERS permission
   - When they submit a form with unique name "Sales Team" and description "All sales staff"
   - Then group is created successfully
   - And user is redirected to group detail page

2. **Duplicate Name Validation**
   - Given a group named "Engineering" already exists
   - When user tries to create another group named "Engineering"
   - Then validation error is displayed
   - And group is not created

3. **Name Required Validation**
   - Given user is on create group form
   - When they submit without entering a name
   - Then validation error "Name is required" is displayed
   - And form is not submitted

4. **Max Length Validation**
   - Given user enters 101 characters in name field
   - When they attempt to submit
   - Then validation error is displayed
   - And form is not submitted

5. **Unauthorized Access**
   - Given a user without MANAGE_USERS permission
   - When they attempt to access group creation form
   - Then they receive 403 Forbidden error

## UI/UX (if applicable)

**Create Group Form:**
- Modal dialog or dedicated page with form
- Name field with character counter (100 max)
- Description textarea with character counter (500 max)
- "Create Group" primary button
- "Cancel" secondary button
- Real-time validation feedback
- Loading state during submission
