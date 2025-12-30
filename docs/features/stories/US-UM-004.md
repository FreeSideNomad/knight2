# US-UM-004: User Login ID Separate from Email

## Story

**As a** system administrator
**I want** user login IDs to be separate from email addresses
**So that** users can have stable login identifiers even if their email changes

## Acceptance Criteria

- [ ] `login_id` field added to user table as separate column
- [ ] `login_id` is unique and required
- [ ] `login_id` is immutable after creation
- [ ] Email can be changed without affecting login_id
- [ ] Authentication accepts login_id for login
- [ ] Both login_id and email are validated for uniqueness
- [ ] Migration script generates login_id from existing email for current users

## Technical Notes

**Database Changes:**
```sql
-- Add login_id column
ALTER TABLE users ADD COLUMN login_id VARCHAR(255);

-- Migrate existing data (extract username from email)
UPDATE users SET login_id = SPLIT_PART(email, '@', 1) WHERE login_id IS NULL;

-- Add unique constraint
ALTER TABLE users ADD CONSTRAINT uk_users_login_id UNIQUE (login_id);
ALTER TABLE users ALTER COLUMN login_id SET NOT NULL;

-- Create index
CREATE INDEX idx_users_login_id ON users(login_id);
```

**Validation Rules:**
- login_id: 3-50 characters, alphanumeric plus dash, underscore, dot
- login_id: Must start with letter or number
- login_id: Case-insensitive uniqueness check
- login_id: Cannot be changed after creation
- email: Standard email validation
- email: Unique across all users

**Implementation:**
- Add `loginId` field to User entity
- Update UserRepository to check login_id uniqueness
- Update authentication service to accept login_id
- Add immutability validation (prevent updates to login_id)
- Update Auth0 integration to use login_id as username

**API Changes:**
```json
POST /api/users
{
  "loginId": "jdoe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe"
}

PATCH /api/users/{id}
{
  "email": "john.doe.new@example.com"  // loginId cannot be changed
}
```

## Dependencies

- None

## Test Cases

1. **Create User**: Verify login_id is required when creating user
2. **Uniqueness - Login ID**: Verify duplicate login_id is rejected
3. **Uniqueness - Email**: Verify duplicate email is rejected
4. **Login ID Format**: Verify invalid characters are rejected
5. **Login ID Immutability**: Verify update attempts to login_id are rejected
6. **Email Update**: Verify email can be changed without affecting login_id
7. **Authentication**: Verify login works with login_id
8. **Case Insensitivity**: Verify login_id uniqueness is case-insensitive
9. **Migration**: Verify existing users get login_id derived from email
10. **Min/Max Length**: Verify login_id length validation (3-50 chars)

## UI/UX (if applicable)

**Create User Form:**
- Add "Login ID" field (required)
- Add real-time validation for:
  - Format (alphanumeric, dash, underscore, dot)
  - Length (3-50 characters)
  - Uniqueness check on blur
- Email field remains separate
- Show helper text: "Login ID cannot be changed after creation"

**User Detail View:**
- Display Login ID prominently (read-only)
- Display Email with edit capability
- Show icon or badge indicating login_id is immutable

**Login Page:**
- Accept login_id as username
- Update placeholder text: "Enter Login ID"
- Maintain email recovery option

**User List View:**
- Add "Login ID" column
- Support search by login_id or email
- Display both login_id and email in results
