# US-AG-001: Create Account Group

## Story

**As a** service profile administrator
**I want** to create account groups with a unique name and description
**So that** I can organize accounts into logical collections for easier permission management

## Acceptance Criteria

- [ ] Form allows input of account group name (required, max 100 characters)
- [ ] Form allows input of description (optional, max 500 characters)
- [ ] Group name must be unique within the service profile
- [ ] Validation error displays if name already exists
- [ ] Validation error displays if name is empty or exceeds character limit
- [ ] Success message displays after successful creation
- [ ] User is redirected to the account group details page after creation
- [ ] Created group appears in the account groups list

## Technical Notes

### Database Schema
- Create new table: `account_groups`
  - `id` (UUID, primary key)
  - `profile_id` (UUID, foreign key to profiles)
  - `name` (VARCHAR(100), not null)
  - `description` (VARCHAR(500), nullable)
  - `created_at` (TIMESTAMP)
  - `updated_at` (TIMESTAMP)
  - Unique constraint on (profile_id, name)

### API Endpoints
- POST `/api/profiles/{profileId}/account-groups`
  - Request body: `{ "name": string, "description": string }`
  - Response: AccountGroupDto with generated ID

### Domain Model
- Create `AccountGroup` aggregate root
- Create `AccountGroupId` value object
- Implement `CreateAccountGroupCommand`

## Dependencies

- None - this is the foundational story for account groups

## Test Cases

1. **Create account group with valid name**
   - Input valid name and description
   - Verify group is created successfully
   - Verify group appears in list

2. **Attempt to create duplicate group name**
   - Create group with name "Group A"
   - Attempt to create another group with name "Group A"
   - Verify validation error is displayed

3. **Create group with empty name**
   - Submit form with empty name
   - Verify validation error is displayed

4. **Create group with name exceeding character limit**
   - Input name with 101 characters
   - Verify validation error is displayed

5. **Create group with only name (no description)**
   - Input name only
   - Verify group is created successfully

## UI/UX

### Create Account Group Form
- Modal dialog or dedicated page
- Fields:
  - Name (text input with character counter)
  - Description (textarea with character counter)
- Buttons:
  - Save (primary action)
  - Cancel (secondary action)
- Display validation errors inline below respective fields
