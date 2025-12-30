# US-AU-004: Password Setup

## Story

**As a** new user
**I want** to create a strong password during registration
**So that** I can securely access my account with credentials I control

## Acceptance Criteria

- [ ] Password form is shown immediately after email verification
- [ ] Password must meet minimum complexity requirements
- [ ] Real-time password strength indicator displayed
- [ ] Password confirmation field to prevent typos
- [ ] Passwords must match before submission
- [ ] Password is securely hashed before storage (bcrypt/Auth0 default)
- [ ] User cannot proceed without setting a valid password
- [ ] Common/breached passwords are rejected
- [ ] Password rules are clearly displayed on the form
- [ ] Success message confirms password creation

## Technical Notes

**Password Requirements:**
- Minimum 8 characters
- Maximum 128 characters
- At least one uppercase letter (A-Z)
- At least one lowercase letter (a-z)
- At least one number (0-9)
- At least one special character (!@#$%^&*()_+-=[]{}|;:,.<>?)
- No common passwords (check against HIBP API or common password list)
- Cannot contain user's login ID or email

**Auth0 Configuration:**
- Configure password policy in Database Connection settings
- Set password strength: "good" (or custom)
- Enable password history (prevent reuse of last 5 passwords)
- Configure password dictionary to reject common passwords

**API Endpoints:**
```
POST /api/auth/registration/set-password
- Request: {
    userId: string,
    password: string,
    passwordConfirm: string,
    registrationToken: string
  }
- Response: {
    success: boolean,
    passwordStrength: string,
    errors?: string[]
  }

POST /api/auth/password/validate
- Request: { password: string, userId: string }
- Response: {
    valid: boolean,
    strength: "weak" | "fair" | "good" | "strong",
    feedback: string[]
  }
```

**Password Strength Calculation:**
- Use zxcvbn library for strength estimation
- Factors: length, character diversity, patterns, common words
- Levels: Weak (0-1), Fair (2), Good (3), Strong (4)
- Require minimum "Good" strength

**Database Changes:**
```sql
ALTER TABLE users ADD COLUMN password_set_at TIMESTAMP;
ALTER TABLE users ADD COLUMN password_strength VARCHAR(20);
ALTER TABLE users ADD COLUMN registration_completed_at TIMESTAMP;

CREATE TABLE password_history (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    password_hash VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Security Measures:**
- Never log or store plaintext passwords
- Use HTTPS for all password transmission
- Implement rate limiting (5 attempts per 15 minutes)
- Clear password fields on validation errors
- Use autocomplete="new-password" attribute
- Prevent password managers from auto-filling confirmation field

## Dependencies

- US-AU-001 (User account with login ID exists)
- US-AU-002 (Registration email sent and link clicked)
- US-AU-003 (Email verified via OTP)

## Test Cases

1. **Password Meets All Requirements**
   - Given user enters "SecureP@ss123"
   - When password validation runs
   - Then password is accepted as "Good" strength

2. **Password Too Short**
   - Given user enters "Pass1!"
   - When password validation runs
   - Then error shows "Password must be at least 8 characters"

3. **Password Missing Uppercase**
   - Given user enters "password123!"
   - When password validation runs
   - Then error shows "Password must contain at least one uppercase letter"

4. **Password Missing Number**
   - Given user enters "Password!"
   - When password validation runs
   - Then error shows "Password must contain at least one number"

5. **Password Missing Special Character**
   - Given user enters "Password123"
   - When password validation runs
   - Then error shows "Password must contain at least one special character"

6. **Passwords Do Not Match**
   - Given password "SecureP@ss123" and confirmation "SecureP@ss124"
   - When user submits form
   - Then error shows "Passwords do not match"

7. **Common Password Rejected**
   - Given user enters "Password123!"
   - When password validation runs
   - Then error shows "This password is too common. Please choose a different one"

8. **Password Contains Login ID**
   - Given login ID "john.doe" and password "JohnDoe123!"
   - When password validation runs
   - Then error shows "Password cannot contain your login ID"

9. **Real-time Strength Indicator**
   - Given user types password character by character
   - When each character is entered
   - Then strength meter updates in real-time (Weak → Fair → Good → Strong)

10. **Successful Password Creation**
    - Given valid password and matching confirmation
    - When user submits form
    - Then password is set and user proceeds to MFA enrollment

## UI/UX (if applicable)

**Password Setup Form:**
```
Set Your Password

Create a strong password for your account.

Password *
[••••••••••••••••]
[Show/Hide]

Password strength: ████████░░ Good

Password must contain:
✓ At least 8 characters
✓ One uppercase letter
✓ One lowercase letter
✓ One number
✗ One special character (!@#$%^&*...)

Confirm Password *
[••••••••••••••••]

[Continue to MFA Setup]
```

**Password Strength Meter:**
```
Weak:   ███░░░░░░░ (Red)
Fair:   █████░░░░░ (Orange)
Good:   ████████░░ (Yellow)
Strong: ██████████ (Green)
```

**Error Messages:**
```
❌ Password must be at least 8 characters long
❌ Password must contain at least one uppercase letter (A-Z)
❌ Password must contain at least one lowercase letter (a-z)
❌ Password must contain at least one number (0-9)
❌ Password must contain at least one special character
❌ Password is too common. Please choose a different password
❌ Password cannot contain your login ID or email address
❌ Passwords do not match
❌ Password must be at least "Good" strength to continue
```

**Success Message:**
```
✓ Password Created Successfully

Your password has been set. Next, let's secure your account with multi-factor authentication.

[Continue to MFA Setup]
```

**Accessibility:**
- ARIA labels for password requirements
- Screen reader announces strength changes
- Clear focus indicators
- Keyboard navigation support
- Error messages associated with fields
