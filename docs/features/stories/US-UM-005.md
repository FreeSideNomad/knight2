# US-UM-005: Create New User

## Story

**As a** bank administrator
**I want** to create new user accounts with login ID, email, name, and roles
**So that** I can onboard new employees and grant them appropriate system access

## Acceptance Criteria

- [ ] Create user form accessible to authorized administrators
- [ ] Form includes fields: login_id, email, first_name, last_name, roles
- [ ] Real-time validation for all required fields
- [ ] Uniqueness check for login_id and email
- [ ] Role selection from predefined list
- [ ] User is created in Auth0 and local database
- [ ] Invitation email is sent to user with registration link
- [ ] Success confirmation with user details displayed
- [ ] User appears in user list immediately after creation

## Technical Notes

**API Endpoint:**
```
POST /api/users
Content-Type: application/json

{
  "loginId": "jdoe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "roles": ["EMPLOYEE", "CLIENT_MANAGER"]
}

Response: 201 Created
{
  "id": "usr_123456",
  "loginId": "jdoe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "roles": ["EMPLOYEE", "CLIENT_MANAGER"],
  "status": "PENDING_REGISTRATION",
  "createdAt": "2025-12-30T15:30:00Z"
}
```

**Implementation:**
1. Validate input fields
2. Check login_id and email uniqueness
3. Create user record in database with status PENDING_REGISTRATION
4. Create user in Auth0 with roles
5. Generate registration token (expires in 7 days)
6. Send invitation email with registration link
7. Return user details

**Validation Rules:**
- login_id: Required, 3-50 chars, alphanumeric + dash/underscore/dot
- email: Required, valid email format
- firstName: Required, 1-100 chars
- lastName: Required, 1-100 chars
- roles: Required, at least one role, valid role names

**Available Roles:**
- SUPER_ADMIN
- BANK_ADMIN
- EMPLOYEE
- CLIENT_MANAGER
- INDIRECT_CLIENT_MANAGER
- SUPPORT

## Dependencies

- US-UM-004: User Login ID Separate from Email

## Test Cases

1. **Valid User Creation**: Verify user is created with all required fields
2. **Duplicate Login ID**: Verify error when login_id already exists
3. **Duplicate Email**: Verify error when email already exists
4. **Invalid Login ID Format**: Verify validation error for invalid characters
5. **Invalid Email Format**: Verify validation error for invalid email
6. **Missing Required Fields**: Verify error when required fields are missing
7. **Invalid Role**: Verify error when invalid role is provided
8. **No Roles**: Verify error when roles array is empty
9. **Auth0 Integration**: Verify user is created in Auth0 with correct roles
10. **Invitation Email**: Verify invitation email is sent to user's email
11. **Database Record**: Verify user record is created with PENDING_REGISTRATION status
12. **User List Update**: Verify new user appears in user list

## UI/UX (if applicable)

**Create User Form:**

```
┌─────────────────────────────────────────┐
│ Create New User                    [X]  │
├─────────────────────────────────────────┤
│                                         │
│ Login ID *                              │
│ [____________________________]          │
│ 3-50 characters, alphanumeric           │
│                                         │
│ Email *                                 │
│ [____________________________]          │
│                                         │
│ First Name *                            │
│ [____________________________]          │
│                                         │
│ Last Name *                             │
│ [____________________________]          │
│                                         │
│ Roles *                                 │
│ ☐ Super Admin                           │
│ ☐ Bank Admin                            │
│ ☑ Employee                              │
│ ☑ Client Manager                        │
│ ☐ Indirect Client Manager               │
│ ☐ Support                               │
│                                         │
│ An invitation email will be sent to    │
│ the user with registration instructions.│
│                                         │
│         [Cancel]  [Create User]         │
└─────────────────────────────────────────┘
```

**Success Confirmation:**
```
✓ User created successfully!

User Details:
- Login ID: jdoe
- Email: john.doe@example.com
- Name: John Doe
- Roles: Employee, Client Manager
- Status: Pending Registration

An invitation email has been sent to john.doe@example.com.

[View User] [Create Another User]
```

**Validation Messages:**
- Real-time validation on field blur
- Inline error messages below each field
- Summary of errors at top of form if submission fails
