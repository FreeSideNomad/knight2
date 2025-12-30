# US-ICP-003: Delete User

## Story

**As an** Indirect Client Administrator
**I want** to delete users from my organization
**So that** I can revoke access for users who no longer need it

## Acceptance Criteria

- [ ] Delete action is available from user grid and user detail page
- [ ] Confirmation dialog appears before deletion
- [ ] Confirmation dialog shows user's name and email
- [ ] Deletion is soft delete (data retained in database)
- [ ] Deleted user is immediately hidden from user list
- [ ] Deleted user cannot log in
- [ ] User's roles and permissions are revoked
- [ ] Audit log records deletion with timestamp and admin who performed it
- [ ] Cannot delete the last admin user in the organization
- [ ] Cannot delete yourself
- [ ] Success message confirms deletion

## Technical Notes

**API Endpoints:**
- `DELETE /api/indirect/users/{userId}` - Soft delete user
  - Response: `204 No Content`
- `GET /api/indirect/users/{userId}/deletion-validation` - Check if user can be deleted
  - Response: `{ canDelete: boolean, reason: string }`

**Database:**
- Update `indirect_client_users` table: set `deleted_at = CURRENT_TIMESTAMP`, `status = 'DELETED'`
- Retain user data for audit purposes
- Do not cascade delete related records (audit logs, activity history)
- Add filter to all user queries: `WHERE deleted_at IS NULL`

**Business Rules:**
- Prevent deletion if user is the only admin
- Prevent self-deletion
- Check for active sessions and invalidate them
- Revoke all refresh tokens

**Security:**
- Requires `INDIRECT_CLIENT_ADMIN` role
- Can only delete users within same indirect client organization
- Audit log entry includes: deleted user ID, admin ID, timestamp, IP address

**Auth0 Integration:**
- Call Auth0 API to block user account
- Set user metadata: `{ deleted: true, deleted_at: timestamp }`
- Do not permanently delete from Auth0 (allows audit trail)

## Dependencies

- US-ICP-001: View Users (remove from grid)
- US-ICP-010: User Detail Page (delete action)
- Authentication service (session invalidation)

## Test Cases

1. **Delete User Successfully**: Select delete, confirm, and verify user removed from list
2. **Confirmation Required**: Click delete and verify confirmation dialog appears
3. **Cancel Deletion**: Click cancel in dialog and verify user not deleted
4. **Cannot Delete Last Admin**: Attempt to delete last admin and verify error message
5. **Cannot Delete Self**: Admin tries to delete own account and verify error
6. **Login Blocked**: Delete user and verify they cannot log in
7. **Soft Delete Verification**: Check database and verify user record still exists with deleted_at timestamp
8. **Audit Log**: Delete user and verify audit log entry created
9. **Session Invalidation**: Delete logged-in user and verify their session is terminated
10. **Cross-Organization Security**: Verify cannot delete users from other indirect clients

## UI/UX

**Delete Confirmation Dialog:**
```
Delete User?
------------

Are you sure you want to delete this user?

Name: John Smith
Email: john.smith@example.com
Login ID: jsmith

This action cannot be undone. The user will:
- Be removed from the user list
- Lose all access to the portal
- Have their roles and permissions revoked

[Cancel]  [Delete User]
```

**Error Messages:**
- Last admin: "Cannot delete the only administrator. Please assign another user as admin first."
- Self-deletion: "You cannot delete your own account. Please contact another administrator."

**Success Message:**
"User {name} has been successfully deleted"

**Grid Action:**
- Delete button appears as trash icon in actions column
- Delete button is disabled with tooltip if user cannot be deleted
