# US-AU-002: New User Registration Email

## Story

**As a** system administrator
**I want** new users to receive a registration email with a secure link
**So that** users can verify their email address and complete their account setup within a reasonable timeframe

## Acceptance Criteria

- [ ] Registration email is sent immediately after admin creates a new user account
- [ ] Email contains a unique, cryptographically secure registration link
- [ ] Registration link expires after 7 days
- [ ] Email clearly states the expiration timeframe
- [ ] Link can only be used once (single-use token)
- [ ] Email includes the user's assigned login ID
- [ ] Email is sent from a verified sender address
- [ ] System tracks email send status (sent, failed, bounced)
- [ ] Admin can resend registration email if needed
- [ ] Expired links show appropriate error message with option to request new link

## Technical Notes

**Auth0 Configuration:**
- Use Auth0 Change Password ticket API for registration links
- Configure custom email template for registration
- Set `resultUrl` to redirect to registration completion page
- Set TTL to 604800 seconds (7 days)

**Email Template:**
- Subject: "Complete Your Knight Platform Registration"
- Include login ID prominently
- Clear call-to-action button
- Expiration date/time in user's timezone
- Contact information for support

**API Endpoints:**
```
POST /api/users/registration/send
- Sends or resends registration email
- Parameters: userId
- Returns: { sent: boolean, expiresAt: timestamp }

GET /api/users/registration/verify/:token
- Validates registration token
- Returns: { valid: boolean, userId: string, expiresAt: timestamp }
```

**Database Changes:**
- Add `registration_email_sent_at` timestamp to user table
- Add `registration_email_count` to track resend attempts
- Add `registration_token_expires_at` timestamp
- Add `registration_completed_at` timestamp

**Security Considerations:**
- Tokens must be cryptographically random (minimum 32 bytes)
- Use HTTPS for all registration links
- Implement rate limiting on resend requests (max 3 per day)
- Log all registration email events for audit trail

## Dependencies

- US-AU-001 (Login ID must exist to include in email)

## Test Cases

1. **Registration Email Sent After User Creation**
   - Given admin creates a new user account
   - When account creation completes
   - Then registration email is sent to user's email address

2. **Email Contains Valid Registration Link**
   - Given registration email is sent
   - When user receives email
   - Then email contains clickable link to registration page

3. **Link Expires After 7 Days**
   - Given registration link created 7 days ago
   - When user clicks the link
   - Then system shows "Link expired" message

4. **Link Can Only Be Used Once**
   - Given user completes registration using link
   - When user clicks same link again
   - Then system shows "Link already used" message

5. **Admin Can Resend Registration Email**
   - Given user has not completed registration
   - When admin clicks "Resend Registration Email"
   - Then new email is sent with fresh 7-day expiration

6. **Rate Limiting on Resend**
   - Given registration email sent 3 times today
   - When admin attempts 4th resend
   - Then system blocks request with "Daily limit exceeded" message

7. **Email Includes Login ID**
   - Given user with login ID "john.doe"
   - When registration email is generated
   - Then email body contains "Your Login ID: john.doe"

8. **Expired Link Shows Resend Option**
   - Given expired registration link
   - When user accesses link
   - Then page shows "Request New Link" button

## UI/UX (if applicable)

**Email Template:**
```
Subject: Complete Your Knight Platform Registration

Hi there,

Welcome to the Knight Platform! Your account has been created with the following Login ID:

Login ID: john.doe

To complete your registration, please click the button below:

[Complete Registration]

This link will expire in 7 days (on January 6, 2025 at 5:30 PM EST).

If you did not request this account, please ignore this email.

Questions? Contact support@knightplatform.com

---
Knight Platform Team
```

**Expired Link Page:**
```
Registration Link Expired

This registration link has expired. Registration links are valid for 7 days.

[Request New Registration Link]

Need help? Contact Support
```

**Admin User Management Interface:**
```
User: John Doe (john.doe)
Status: Registration Pending
Registration Email Sent: Dec 30, 2024 at 2:15 PM
Expires: Jan 6, 2025 at 2:15 PM

[Resend Registration Email]
```
