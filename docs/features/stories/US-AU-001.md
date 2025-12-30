# US-AU-001: Login ID Separate from Email

## Story

**As a** system administrator
**I want** users to have a separate login ID distinct from their email address
**So that** users can maintain a consistent login credential while email addresses may change, and we can use email solely for communications

## Acceptance Criteria

- [ ] Auth0 username field is used as the primary login identifier
- [ ] Email field is stored separately and used only for communications
- [ ] Users can log in using their username (login ID)
- [ ] Email changes do not affect the user's ability to log in
- [ ] Login ID is immutable once created
- [ ] Login ID follows naming conventions (alphanumeric, may include dots, hyphens, underscores)
- [ ] System validates login ID uniqueness during registration
- [ ] Both login ID and email are displayed in user profile views

## Technical Notes

**Auth0 Configuration:**
- Use Auth0 Database Connection with username requirement enabled
- Configure `requires_username: true` in database connection settings
- Username field will be the primary identifier for authentication
- Email field stored separately in user metadata

**API Changes:**
- User registration endpoint accepts both `username` and `email` parameters
- User profile responses include both `username` and `email` fields
- Authentication endpoints accept username as the primary identifier

**Database Changes:**
- Ensure user tables have separate columns for `username` and `email`
- Add unique constraint on `username` column
- Add unique constraint on `email` column

**Validation Rules:**
- Username: 3-30 characters, alphanumeric plus `.`, `-`, `_`
- Username must not start or end with special characters
- Username is case-insensitive for uniqueness checks but preserves case for display

## Dependencies

- None (foundational story)

## Test Cases

1. **Successful Registration with Username and Email**
   - Given valid username and email
   - When user registers
   - Then account is created with both fields stored separately

2. **Login with Username**
   - Given registered user with username "john.doe" and email "john@example.com"
   - When user logs in with "john.doe"
   - Then authentication succeeds

3. **Cannot Login with Email**
   - Given registered user with username "john.doe" and email "john@example.com"
   - When user attempts to log in with "john@example.com"
   - Then authentication fails with appropriate error message

4. **Username Uniqueness Validation**
   - Given existing user with username "john.doe"
   - When new user attempts to register with same username
   - Then registration fails with "Username already exists" error

5. **Email Change Does Not Affect Login**
   - Given user with username "john.doe" and email "old@example.com"
   - When user changes email to "new@example.com"
   - Then user can still log in with "john.doe"

6. **Invalid Username Format**
   - Given username with invalid characters (e.g., spaces, special chars)
   - When user attempts to register
   - Then registration fails with format validation error

## UI/UX (if applicable)

**Registration Form:**
- Separate fields for "Login ID" and "Email Address"
- Inline validation for username format
- Real-time uniqueness check for username
- Clear labels explaining that Login ID is used for signing in
- Help text: "Your Login ID will be used to sign in and cannot be changed"

**Login Form:**
- Field labeled "Login ID" (not "Email" or "Username/Email")
- Help text or link: "Enter the Login ID you created during registration"

**User Profile Display:**
```
Login ID: john.doe
Email: john@example.com
```

**Error Messages:**
- "This Login ID is already taken. Please choose another."
- "Login ID must be 3-30 characters and contain only letters, numbers, dots, hyphens, or underscores"
- "Please enter your Login ID, not your email address"
