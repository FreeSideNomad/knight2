# US-ICP-002: Add User

## Story

**As an** Indirect Client Administrator
**I want** to add new users to my organization
**So that** I can grant team members access to the portal

## Acceptance Criteria

- [ ] Form includes fields: login ID, email, first name, last name
- [ ] Email validation ensures valid email format
- [ ] Login ID is unique within the organization
- [ ] Login ID cannot contain special characters or spaces
- [ ] User can select one or more roles from available roles list
- [ ] "Send Invitation" checkbox is enabled by default
- [ ] Form validates all required fields before submission
- [ ] Success message displays after user creation
- [ ] Invitation email is sent if "Send Invitation" is checked
- [ ] User is redirected to user list after successful creation
- [ ] New user appears in the user grid immediately

## Technical Notes

**API Endpoints:**
- `POST /api/indirect/users` - Create new user
  - Request body: `CreateUserRequest { loginId, email, firstName, lastName, roles[], sendInvitation }`
  - Response: `UserDTO`
- `GET /api/indirect/roles` - Get available roles for selection

**Database:**
- Insert into `indirect_client_users` table
- Link to `indirect_client_id` from authenticated user's context
- Insert role assignments into `user_roles` table
- Set initial status to "PENDING" until first login
- Generate secure invitation token and store in `user_invitations` table

**Email Integration:**
- Send invitation email via Auth0 or email service
- Include invitation link with token: `/invite?token={token}`
- Token expires in 7 days
- Email template includes organization name, inviter name, setup instructions

**Security:**
- Requires `INDIRECT_CLIENT_ADMIN` role
- Validate user count against organization license limit
- Cannot assign roles higher than current user's role level
- Audit log entry for user creation

## Dependencies

- US-ICP-001: View Users (for displaying new user in grid)
- Email service integration
- Role management system

## Test Cases

1. **Create User Successfully**: Fill form with valid data and verify user is created
2. **Email Validation**: Enter invalid email and verify error message
3. **Login ID Uniqueness**: Enter existing login ID and verify error
4. **Required Fields**: Submit form with missing fields and verify validation errors
5. **Send Invitation**: Create user with "Send Invitation" checked and verify email sent
6. **Skip Invitation**: Uncheck "Send Invitation" and verify no email sent
7. **Multiple Roles**: Select multiple roles and verify all are assigned
8. **Special Characters**: Enter login ID with special characters and verify error
9. **License Limit**: Attempt to add user when limit reached and verify error
10. **Insufficient Permissions**: Non-admin user cannot access add user form

## UI/UX

**Form Layout:**
```
Add New User
------------

Login ID *
[____________________]
Must be unique, no special characters

Email *
[____________________]
User will receive notifications at this email

First Name *
[____________________]

Last Name *
[____________________]

Roles *
[ ] Admin
[ ] User Manager
[ ] Viewer
[ ] Accountant

[x] Send invitation email

[Cancel]  [Create User]
```

**Validation Messages:**
- Login ID: "Login ID already exists" or "Special characters not allowed"
- Email: "Please enter a valid email address"
- Required fields: "This field is required"

**Success Message:**
"User successfully created and invitation sent to {email}"
