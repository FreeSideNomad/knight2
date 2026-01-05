# Authentication System

## Overview

This document describes the authentication system for the Knight platform, covering first-time registration (FTR), repeated authentication, and related security flows for indirect client users authenticated via Auth0.

## Architecture Principles

1. **Custom UI for all flows** - We use our own UI for registration, login, and MFA enrollment (not Auth0's Universal Login)
2. **Email verification outside Auth0** - OTP-based email verification managed by our platform
3. **Auth0 as identity provider** - Password and MFA (Guardian/Passkeys) managed by Auth0
4. **Users table as source of truth for email verification** - Our database tracks email verification status
5. **Auth0 as source of truth for password and MFA status** - Query Auth0 for `password_set` and `mfa_enrolled`
6. **Shared email addresses supported** - Multiple users can share the same email address (Auth0 Non-Unique Emails feature)
7. **Same FTR flow for both portals** - Client Portal and Indirect Client Portal use identical authentication flows

## Key Design Decisions

### Login ID vs Auth0 Username

Users enter a **Login ID** in our system which may look like an email address (e.g., `admin@big.com`). Since Auth0 blocks email-format usernames, we transform the Login ID for Auth0:

```
Login ID (our system):     admin@big.com
Auth0 username:            admin_big.com  (@ replaced with _)
Auth0 email:               shared@company.com (actual email for OTP)
```

**Transformation rules:**
- Replace `@` with `_`
- Other special characters that Auth0 doesn't accept are also replaced with `_`
- The original Login ID is stored in our `users` table
- The transformed username is used as Auth0's `username` field

```java
public static String toAuth0Username(String loginId) {
    // Auth0 username allows: alphanumeric, _ . + - # ' ~
    // But NOT email format (cannot contain @ in email-like pattern)
    return loginId
        .replace("@", "_")
        .toLowerCase();
}

public static String fromAuth0Username(String auth0Username) {
    // Note: This is lossy - we store original loginId in our DB
    // This is only for display purposes if needed
    return auth0Username;
}
```

### Email Storage Strategy

**Email is NOT stored in Auth0** - only in our `users` table. This provides:

- No dependency on Auth0 Early Access features
- Multiple users can share the same email address
- We fully control email communication
- Cleaner separation of concerns

| Field | Auth0 | Our `users` Table |
|-------|-------|-------------------|
| username (transformed) | ✓ | - |
| password | ✓ | - |
| loginId (original) | ✓ (app_metadata) | ✓ |
| email | ✗ | ✓ |
| email_verified | ✗ | ✓ |

**Auth0 Configuration:**
- Enable "Flexible Identifiers" on database connection
- Set `username` as the only identifier
- Remove email requirement

**Example:**
```
User 1: loginId=john.doe@acme.com → Auth0 username: john.doe_acme.com
User 2: loginId=jane.doe@acme.com → Auth0 username: jane.doe_acme.com
Both share email in our DB: shared-admin@acme.com (for OTP)
Auth0 knows nothing about email.
```

### Passkey Strategy

1. **Passkey with user verification replaces BOTH password AND MFA**
2. **Enrollment timing**:
   - During FTR: After password setup, BEFORE Guardian enrollment (optional)
   - Post-login: Prompt on dashboard
   - Settings: User can add anytime
3. **If passkey unavailable** (different device, browser issue):
   - Step 1: Email OTP verification
   - Step 2: Password authentication
   - Step 3: Optionally re-enroll passkey on new device

## User States

| State | Description | Next Step |
|-------|-------------|-----------|
| `PENDING_CREATION` | User created locally, not yet provisioned to Auth0 | Provision to Auth0 |
| `PENDING_VERIFICATION` | Provisioned to Auth0, email not verified | Email OTP verification |
| `PENDING_PASSWORD` | Email verified, password not set | Set password |
| `PENDING_PASSKEY` | Password set, passkey not offered | Offer passkey enrollment (optional) |
| `PENDING_MFA` | Passkey skipped/enrolled, Guardian MFA not enrolled | Enroll Guardian MFA |
| `ACTIVE` | Fully onboarded | Normal authentication |
| `LOCKED` | Account locked | Unlock by admin |
| `DEACTIVATED` | Account deactivated | Reactivation by admin |

**Note:** If user enrolls a passkey with user verification, Guardian MFA enrollment can be skipped as passkey satisfies the MFA requirement.

## First-Time Registration (FTR) Flow

### Step 1: User Identification

```
User enters Login ID (username)
        ↓
Query users table by login_id
        ↓
If not found → Display "User not found" error
If found → Check user status
```

### Step 2: Status Check & Routing

```
Check user.status and Auth0 state
        ↓
┌──────────────────────────────────────────────────────────────────┐
│ Status Check Decision Tree                                        │
├──────────────────────────────────────────────────────────────────┤
│ email_verified = false      → Email OTP Screen                   │
│ password_set = false        → Set Password Screen                │
│ passkey_offered = false     → Passkey Enrollment Screen (optional)│
│ passkey enrolled w/ UV      → Skip Guardian, go to Dashboard     │
│ mfa_enrolled = false        → Guardian MFA Enrollment Screen     │
│ All complete                → Dashboard                          │
└──────────────────────────────────────────────────────────────────┘
```

**UV = User Verification** (biometric or PIN on the passkey)

### Step 3: Email OTP Verification

1. System generates 6-digit OTP
2. OTP stored in database with expiration (default: 120 seconds)
3. Email sent via external email service (not Auth0)
4. User enters OTP in UI
5. On success: Update `users.email_verified = true`
6. On failure: Allow retry or resend

**Database Schema for OTP:**
```sql
CREATE TABLE email_verification_otp (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id),
    otp_code VARCHAR(6) NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    attempts INT DEFAULT 0,
    max_attempts INT DEFAULT 3,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(user_id)
);
```

**OTP Flow:**
```
Generate OTP → Store in DB → Send Email
        ↓
User enters OTP
        ↓
Validate: code matches AND not expired AND attempts < max
        ↓
Success: Mark verified, proceed to password
Failure: Increment attempts, show error
Expired: Allow resend
```

### Step 4: Password Setup

1. Query Auth0 to confirm password not set
2. Display password entry UI (with confirmation)
3. Call Auth0 Management API to set password
4. Validate password strength (minimum 12 characters)

**Auth0 API Call:**
```
PATCH /api/v2/users/{user_id}
{
  "password": "new_password",
  "connection": "Username-Password-Authentication"
}
```

### Step 5: Passkey Enrollment (Optional)

After password setup, offer passkey enrollment:

```
┌─────────────────────────────────────────────────────────────┐
│  Secure Your Account with a Passkey                         │
│                                                              │
│  Passkeys let you sign in with your fingerprint, face,      │
│  or device PIN. No passwords to remember!                   │
│                                                              │
│  [Set Up Passkey]              [Skip for Now]               │
└─────────────────────────────────────────────────────────────┘
```

**Flow:**
1. Check if device/browser supports WebAuthn
2. If supported, show passkey enrollment prompt
3. If user chooses to enroll:
   - Call `/api/login/passkey/register-options` to get WebAuthn challenge
   - Browser prompts for biometric/PIN
   - Call `/api/login/passkey/register` with credential
   - Mark `passkey_enrolled = true`, `passkey_has_uv = true/false`
4. If user skips, mark `passkey_offered = true`

**If passkey with user verification is enrolled:**
- Skip Guardian MFA enrollment (passkey satisfies MFA requirement)
- Proceed directly to dashboard

### Step 6: Guardian MFA Enrollment

Only shown if user skipped passkey or enrolled passkey without user verification.

1. Query Auth0 for current MFA enrollments
2. Display MFA options:
   - **Guardian Push** - Auth0 Guardian app (recommended)
   - **TOTP** - Google Authenticator, Authy, etc.
3. Guide user through enrollment
4. Verify enrollment successful

## Repeated Authentication Flow

### Standard Login

```
User enters Login ID
        ↓
Query users table → Get user details
        ↓
Check if any FTR steps incomplete → Route to FTR
        ↓
Check if passkey enrolled
        ↓
┌─────────────────────────────────────────────────────────┐
│ If passkey available:                                    │
│   → Prompt WebAuthn authentication                      │
│   → If passkey has UV → Skip MFA, issue tokens          │
│   → If passkey no UV → Prompt Guardian MFA              │
│                                                          │
│ If passkey unavailable or user chooses password:        │
│   → Prompt password authentication                      │
│   → Prompt Guardian MFA                                 │
│   → Issue tokens                                        │
└─────────────────────────────────────────────────────────┘
        ↓
Success → Redirect to dashboard
```

### Passkey Unavailable Fallback

When user has a passkey enrolled but it's not available (different device, browser issue):

```
User enters Login ID
        ↓
Passkey prompt fails or user clicks "Use password instead"
        ↓
Step 1: Email OTP Verification
   → Send OTP to registered email
   → User enters 6-digit code
   → Verify OTP
        ↓
Step 2: Password Authentication
   → User enters password
   → Authenticate with Auth0
        ↓
Step 3: Offer Passkey Re-enrollment
   → "Would you like to set up a passkey on this device?"
   → [Set Up Passkey] [Skip]
        ↓
Success → Redirect to dashboard
```

### Email Changed - Re-verification Required

When a user's email address changes:

1. Set `users.email_verified = false`
2. On next login attempt, route to Email OTP verification
3. Send OTP to **new** email address
4. Complete verification before allowing access

### Password Reset Flow

```
User requests password reset
        ↓
Email OTP verification (security check)
        ↓
Display password entry UI
        ↓
Call Auth0 to update password
        ↓
Success → Return to login
```

### MFA Re-binding (Guardian Reset)

When user needs to re-bind their Guardian authenticator:

1. Admin or user initiates reset
2. Delete existing MFA enrollments in Auth0:
   ```
   DELETE /api/v2/users/{user_id}/authenticators/{authenticator_id}
   ```
3. On next login, user is prompted to enroll new MFA device
4. Email OTP verification may be required for security

## Passkeys (WebAuthn/FIDO2)

### Overview

Passkeys provide passwordless, phishing-resistant authentication using WebAuthn. They can serve as:

1. **First-factor authentication** - Replace password entirely
2. **MFA factor** - Replace or supplement Guardian

### Auth0 Passkey Capabilities

| Capability | Supported | Notes |
|------------|-----------|-------|
| Passkey as primary auth | Yes | Can replace password completely |
| Passkey as MFA | Yes | WebAuthn with user verification |
| Replace Guardian | Yes | Via custom MFA policy logic |
| Cross-device sync | Yes | Synced passkeys (iCloud, Google, etc.) |
| Device-bound | Yes | Hardware security keys |

### Passkey Configuration Requirements

1. **Identifier First login flow** - User enters identifier, then authenticates
2. **New Universal Login Experience** - Required for WebAuthn support
3. Enable passkeys in Auth0 Dashboard:
   - Authentication → Database → Connection → Authentication Methods → Passkey

### Passkey Registration Flow

```
User completes password setup
        ↓
Prompt: "Add a passkey for faster login?"
        ↓
If yes → WebAuthn registration ceremony
        ↓
Store passkey credential in Auth0
        ↓
User can now login with passkey
```

### Passkey Login Flow

```
User enters Login ID
        ↓
Check if passkey enrolled
        ↓
If passkey available → Prompt passkey authentication
If no passkey → Fall back to password
        ↓
WebAuthn authentication ceremony
        ↓
If MFA required AND passkey with user verification → Skip additional MFA
If MFA required AND no user verification → Prompt secondary factor
```

### Passkey vs Password vs Guardian Decision Matrix

| Scenario | Recommended Approach |
|----------|---------------------|
| New device, first login | Password + MFA, then offer passkey enrollment |
| Returning user, passkey available | Passkey only (skip MFA if user verification) |
| Passkey unavailable | Password + Guardian/TOTP |
| High-security operation | Step-up authentication regardless |

## Clarifying Questions - Passkeys

**Q1: Can passkeys completely replace passwords?**
> Yes. Auth0 supports passkey-only authentication. Users can sign up and log in using only passkeys. However, password remains available as a fallback for devices/browsers that don't support WebAuthn.

**Q2: Can passkeys replace Guardian authenticator?**
> Yes. WebAuthn with user verification (biometric or PIN) satisfies MFA requirements. You can implement custom logic to bypass the Guardian challenge when a passkey with user verification is used. The passkey provides:
> - Something you have (the device/key)
> - Something you are (biometric) OR something you know (PIN)

**Q3: Recommended approach?**
> - Require password setup during FTR for fallback
> - Strongly encourage passkey enrollment after password
> - Allow passkey-only login when enrolled
> - If passkey uses user verification, skip additional MFA
> - If passkey without user verification, require Guardian/TOTP as second factor

## Email Service Architecture

### Design Principles

The email service is abstracted behind an interface to allow easy replacement of providers between environments:

- **Development/Test:** AhaSend (1,000 emails/month free tier)
- **Production:** Enterprise provider (TBD - e.g., SendGrid, AWS SES, or internal SMTP)

### Email Service Interface

```java
/**
 * Email service abstraction for sending transactional emails.
 * Implementations are swappable via configuration.
 */
public interface EmailService {

    /**
     * Send an email.
     * @param request The email request containing recipient, subject, and content
     * @return Result indicating success or failure with details
     */
    EmailResult send(EmailRequest request);

    /**
     * Send OTP verification email using a template.
     * @param to Recipient email address
     * @param otpCode The 6-digit OTP code
     * @param expiresInSeconds OTP expiration time
     * @return Result indicating success or failure
     */
    EmailResult sendOtpVerification(String to, String otpCode, int expiresInSeconds);
}

public record EmailRequest(
    String to,
    String subject,
    String htmlBody,
    String textBody,
    Map<String, String> metadata
) {}

public record EmailResult(
    boolean success,
    String messageId,
    String errorCode,
    String errorMessage
) {
    public static EmailResult success(String messageId) {
        return new EmailResult(true, messageId, null, null);
    }

    public static EmailResult failure(String errorCode, String errorMessage) {
        return new EmailResult(false, null, errorCode, errorMessage);
    }
}
```

### Configuration

```yaml
# application.yml
email:
  provider: ${EMAIL_PROVIDER:ahasend}  # ahasend | sendgrid | smtp | mock
  from-address: ${EMAIL_FROM:noreply@knight.example.com}
  from-name: ${EMAIL_FROM_NAME:Knight Platform}

  # AhaSend configuration (dev/test)
  ahasend:
    api-key: ${AHASEND_API_KEY:}
    api-url: https://api.ahasend.com/v1

  # SendGrid configuration (production option)
  sendgrid:
    api-key: ${SENDGRID_API_KEY:}

  # SMTP configuration (production option)
  smtp:
    host: ${SMTP_HOST:}
    port: ${SMTP_PORT:587}
    username: ${SMTP_USERNAME:}
    password: ${SMTP_PASSWORD:}
    starttls: true

  # Templates
  templates:
    otp-verification:
      subject: "Your verification code: {{otp_code}}"
```

### AhaSend Implementation

```java
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "ahasend")
@RequiredArgsConstructor
@Slf4j
public class AhaSendEmailService implements EmailService {

    private final AhaSendProperties properties;
    private final RestClient restClient;

    @Override
    public EmailResult send(EmailRequest request) {
        try {
            var response = restClient.post()
                .uri(properties.getApiUrl() + "/email/send")
                .header("X-Api-Key", properties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                    "from", Map.of(
                        "email", properties.getFromAddress(),
                        "name", properties.getFromName()
                    ),
                    "to", List.of(Map.of("email", request.to())),
                    "subject", request.subject(),
                    "html", request.htmlBody(),
                    "text", request.textBody()
                ))
                .retrieve()
                .body(AhaSendResponse.class);

            return EmailResult.success(response.messageId());
        } catch (Exception e) {
            log.error("Failed to send email via AhaSend: {}", e.getMessage());
            return EmailResult.failure("SEND_FAILED", e.getMessage());
        }
    }

    @Override
    public EmailResult sendOtpVerification(String to, String otpCode, int expiresInSeconds) {
        String html = buildOtpEmailHtml(otpCode, expiresInSeconds);
        String text = buildOtpEmailText(otpCode, expiresInSeconds);

        return send(new EmailRequest(
            to,
            "Your verification code: " + otpCode,
            html,
            text,
            Map.of("type", "otp_verification")
        ));
    }

    private String buildOtpEmailHtml(String otpCode, int expiresInSeconds) {
        int minutes = expiresInSeconds / 60;
        return """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                <h2>Email Verification</h2>
                <p>Your verification code is:</p>
                <div style="font-size: 32px; font-weight: bold; letter-spacing: 8px;
                            background: #f5f5f5; padding: 20px; text-align: center;
                            border-radius: 8px; margin: 20px 0;">
                    %s
                </div>
                <p>This code expires in %d minute%s.</p>
                <p>If you didn't request this code, please ignore this email.</p>
            </div>
            """.formatted(otpCode, minutes, minutes == 1 ? "" : "s");
    }

    private String buildOtpEmailText(String otpCode, int expiresInSeconds) {
        int minutes = expiresInSeconds / 60;
        return """
            Email Verification

            Your verification code is: %s

            This code expires in %d minute%s.

            If you didn't request this code, please ignore this email.
            """.formatted(otpCode, minutes, minutes == 1 ? "" : "s");
    }
}
```

### Mock Implementation (Testing)

```java
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "mock", matchIfMissing = false)
@Slf4j
public class MockEmailService implements EmailService {

    private final List<EmailRequest> sentEmails = new CopyOnWriteArrayList<>();

    @Override
    public EmailResult send(EmailRequest request) {
        log.info("MOCK EMAIL: To={}, Subject={}", request.to(), request.subject());
        sentEmails.add(request);
        return EmailResult.success("mock-" + UUID.randomUUID());
    }

    @Override
    public EmailResult sendOtpVerification(String to, String otpCode, int expiresInSeconds) {
        log.info("MOCK OTP EMAIL: To={}, Code={}", to, otpCode);
        return send(new EmailRequest(to, "OTP: " + otpCode, otpCode, otpCode, Map.of()));
    }

    // For testing: retrieve sent emails
    public List<EmailRequest> getSentEmails() {
        return List.copyOf(sentEmails);
    }

    public void clearSentEmails() {
        sentEmails.clear();
    }
}
```

### Provider Comparison

| Provider | Free Tier | Use Case | Notes |
|----------|-----------|----------|-------|
| **AhaSend** | 1,000/month | Dev/Test | Fast delivery, simple API |
| **SendGrid** | 100/day | Production | Enterprise features, analytics |
| **AWS SES** | 62,000/month* | Production | *If sending from EC2 |
| **Internal SMTP** | Unlimited | Production | Requires infrastructure |

### Environment Configuration

```bash
# Development (.env.dev)
EMAIL_PROVIDER=ahasend
AHASEND_API_KEY=your-dev-api-key
EMAIL_FROM=noreply@dev.knight.example.com

# Testing (.env.test)
EMAIL_PROVIDER=mock

# Production (.env.prod)
EMAIL_PROVIDER=sendgrid  # or smtp
SENDGRID_API_KEY=your-prod-api-key
EMAIL_FROM=noreply@knight.example.com
```

## OTP Service Architecture

### OTP Service Interface

```java
/**
 * Service for managing email OTP verification.
 */
public interface OtpService {

    /**
     * Generate and send an OTP to the user's email.
     * @param userId The user's ID
     * @return Result with OTP details (expiration, masked email)
     */
    OtpSendResult sendOtp(UserId userId);

    /**
     * Verify an OTP code.
     * @param userId The user's ID
     * @param otpCode The 6-digit code entered by user
     * @return Verification result
     */
    OtpVerifyResult verifyOtp(UserId userId, String otpCode);

    /**
     * Check if user can request a new OTP (rate limiting).
     * @param userId The user's ID
     * @return true if new OTP can be sent
     */
    boolean canResendOtp(UserId userId);
}

public record OtpSendResult(
    boolean success,
    String maskedEmail,
    int expiresInSeconds,
    int resendAvailableInSeconds,
    String errorCode,
    String errorMessage
) {}

public record OtpVerifyResult(
    boolean success,
    String errorCode,      // invalid_code, expired, max_attempts
    int attemptsRemaining,
    boolean canResend
) {}
```

### OTP Configuration

```yaml
otp:
  code-length: 6
  expiration-seconds: 120        # 2 minutes
  max-attempts: 3
  resend-cooldown-seconds: 60    # 1 minute between resends
  max-resends-per-hour: 5
```

### OTP Implementation

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    private final OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final OtpProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public OtpSendResult sendOtp(UserId userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        // Check rate limiting
        if (!canResendOtp(userId)) {
            return new OtpSendResult(false, null, 0,
                getResendCooldownRemaining(userId),
                "RATE_LIMITED", "Please wait before requesting a new code");
        }

        // Invalidate any existing OTPs
        otpRepository.invalidateAllForUser(userId);

        // Generate new OTP
        String otpCode = generateOtpCode();
        Instant expiresAt = Instant.now().plusSeconds(properties.getExpirationSeconds());

        EmailVerificationOtp otp = EmailVerificationOtp.create(
            userId, otpCode, user.email(), expiresAt, properties.getMaxAttempts()
        );
        otpRepository.save(otp);

        // Send email
        EmailResult emailResult = emailService.sendOtpVerification(
            user.email(), otpCode, properties.getExpirationSeconds()
        );

        if (!emailResult.success()) {
            log.error("Failed to send OTP email to {}: {}",
                maskEmail(user.email()), emailResult.errorMessage());
            return new OtpSendResult(false, null, 0, 0,
                "EMAIL_FAILED", "Failed to send verification email");
        }

        return new OtpSendResult(
            true,
            maskEmail(user.email()),
            properties.getExpirationSeconds(),
            properties.getResendCooldownSeconds(),
            null, null
        );
    }

    @Override
    @Transactional
    public OtpVerifyResult verifyOtp(UserId userId, String otpCode) {
        Optional<EmailVerificationOtp> otpOpt =
            otpRepository.findLatestValidForUser(userId);

        if (otpOpt.isEmpty()) {
            return new OtpVerifyResult(false, "NO_PENDING_OTP", 0, true);
        }

        EmailVerificationOtp otp = otpOpt.get();

        // Check expiration
        if (otp.isExpired()) {
            return new OtpVerifyResult(false, "EXPIRED", 0, true);
        }

        // Check max attempts
        if (otp.isMaxAttemptsReached()) {
            return new OtpVerifyResult(false, "MAX_ATTEMPTS", 0, true);
        }

        // Verify code
        if (!otp.matches(otpCode)) {
            otp.incrementAttempts();
            otpRepository.save(otp);
            int remaining = otp.getRemainingAttempts();
            return new OtpVerifyResult(false, "INVALID_CODE", remaining, remaining == 0);
        }

        // Success - mark as verified
        otp.markVerified();
        otpRepository.save(otp);

        // Update user's email verification status
        User user = userRepository.findById(userId).orElseThrow();
        user.markEmailVerified();
        userRepository.save(user);

        return new OtpVerifyResult(true, null, 0, false);
    }

    @Override
    public boolean canResendOtp(UserId userId) {
        return otpRepository.countRecentForUser(userId, Duration.ofHours(1))
            < properties.getMaxResendsPerHour()
            && getResendCooldownRemaining(userId) == 0;
    }

    private String generateOtpCode() {
        int max = (int) Math.pow(10, properties.getCodeLength());
        int code = secureRandom.nextInt(max);
        return String.format("%0" + properties.getCodeLength() + "d", code);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return email;
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String masked = local.charAt(0) + "***";
        return masked + domain.charAt(0) + "***" +
               domain.substring(domain.lastIndexOf('.'));
    }

    private int getResendCooldownRemaining(UserId userId) {
        return otpRepository.findLatestForUser(userId)
            .map(otp -> {
                long elapsed = Duration.between(otp.createdAt(), Instant.now()).toSeconds();
                return Math.max(0, properties.getResendCooldownSeconds() - (int) elapsed);
            })
            .orElse(0);
    }
}
```

### OTP Entity

```java
public class EmailVerificationOtp {
    private final UUID id;
    private final UserId userId;
    private final String otpCode;          // Hashed for security
    private final String email;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant verifiedAt;
    private int attempts;
    private final int maxAttempts;

    public static EmailVerificationOtp create(
            UserId userId, String otpCode, String email,
            Instant expiresAt, int maxAttempts) {
        return new EmailVerificationOtp(
            UUID.randomUUID(), userId, hashCode(otpCode), email,
            Instant.now(), expiresAt, null, 0, maxAttempts
        );
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isMaxAttemptsReached() {
        return attempts >= maxAttempts;
    }

    public boolean matches(String code) {
        return hashCode(code).equals(this.otpCode);
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public int getRemainingAttempts() {
        return Math.max(0, maxAttempts - attempts);
    }

    public void markVerified() {
        this.verifiedAt = Instant.now();
    }

    private static String hashCode(String code) {
        // Use a fast hash - OTP codes are short-lived
        return Hashing.sha256().hashString(code, StandardCharsets.UTF_8).toString();
    }
}
```

## API Design

### FTR Status Check

```
POST /api/login/user/ftr-status
Request:
{
  "login_id": "john.doe"
}

Response:
{
  "user_id": "uuid",
  "email": "john.doe@company.com",
  "email_masked": "j***@c***.com",
  "status": "PENDING_VERIFICATION",
  "steps_required": ["email_verification", "password_setup", "mfa_enrollment"],
  "current_step": "email_verification"
}
```

### Email OTP - Send

```
POST /api/login/otp/send
Request:
{
  "user_id": "uuid"
}

Response:
{
  "success": true,
  "expires_in_seconds": 120,
  "email_masked": "j***@c***.com"
}
```

### Email OTP - Verify

```
POST /api/login/otp/verify
Request:
{
  "user_id": "uuid",
  "otp_code": "123456"
}

Response (success):
{
  "success": true,
  "next_step": "password_setup"
}

Response (failure):
{
  "success": false,
  "error": "invalid_otp" | "expired" | "max_attempts_exceeded",
  "attempts_remaining": 2
}
```

### Password Setup

```
POST /api/login/password/set
Request:
{
  "user_id": "uuid",
  "password": "new_secure_password"
}

Response:
{
  "success": true,
  "next_step": "mfa_enrollment"
}
```

### Passkey Enrollment

```
POST /api/login/passkey/register-options
Request:
{
  "user_id": "uuid"
}

Response:
{
  "challenge": "base64...",
  "rp": { "name": "Knight Platform", "id": "knight.example.com" },
  "user": { "id": "base64...", "name": "john.doe", "displayName": "John Doe" },
  "pubKeyCredParams": [...],
  "authenticatorSelection": {
    "userVerification": "required",
    "residentKey": "required"
  }
}
```

```
POST /api/login/passkey/register
Request:
{
  "user_id": "uuid",
  "credential": { /* WebAuthn credential response */ }
}

Response:
{
  "success": true,
  "passkey_id": "uuid"
}
```

## User Provisioning to Auth0

When creating a user in Auth0, set:

```json
{
  "username": "admin_big.com",
  "connection": "Username-Password-Authentication",
  "password": "<generated-temporary-password>",
  "name": "First Last",
  "given_name": "First",
  "family_name": "Last",
  "app_metadata": {
    "internal_user_id": "uuid",
    "login_id": "admin@big.com",
    "profile_id": "profile-urn",
    "provisioned_by": "knight_platform",
    "provisioned_at": "2025-01-04T12:00:00Z"
  }
}
```

**Key points:**
- `username` - Transformed from loginId (@ replaced with _)
- **No email field** - Email stored only in our `users` table
- `app_metadata.login_id` - Original loginId for reference
- Temporary password generated - User must set their own password via FTR
- No email verification from Auth0 (we handle via OTP)

**Auth0 Connection Configuration:**
- Enable "Flexible Identifiers" on the database connection
- Set `username` as the only identifier (no email)
- Remove email requirement from connection settings

## Security Considerations

1. **OTP Brute Force Protection**
   - Maximum 3 attempts per OTP
   - Rate limit OTP resend (e.g., 60 seconds between resends)
   - Account lockout after repeated failures

2. **Password Requirements**
   - Minimum 12 characters
   - Enforced by Auth0 password policy

3. **Session Security**
   - Short-lived access tokens (1 hour)
   - Refresh tokens for extended sessions
   - Token revocation on logout

4. **Passkey Security**
   - Require user verification for high-security operations
   - Device-bound passkeys for sensitive accounts
   - Allow multiple passkeys per user for backup

## Implementation Phases

### Phase 1: Email Service Abstraction
- [ ] Create `EmailService` interface in domain layer
- [ ] Create `EmailRequest` and `EmailResult` records
- [ ] Implement `AhaSendEmailService` adapter
- [ ] Implement `MockEmailService` for testing
- [ ] Add email configuration properties
- [ ] Write unit tests for email services

### Phase 2: OTP Verification System
- [ ] Create `email_verification_otp` database table
- [ ] Create `EmailVerificationOtp` domain entity
- [ ] Create `OtpRepository` interface and JPA adapter
- [ ] Create `OtpService` interface and implementation
- [ ] Add OTP configuration properties
- [ ] Implement rate limiting logic
- [ ] Write unit tests for OTP service

### Phase 3: FTR API Endpoints
- [ ] Create `/api/login/user/ftr-status` endpoint
- [ ] Create `/api/login/otp/send` endpoint
- [ ] Create `/api/login/otp/verify` endpoint
- [ ] Create `/api/login/password/set` endpoint
- [ ] Update user provisioning to set `email_verified: true` in Auth0
- [ ] Remove Auth0 email verification triggers
- [ ] Write integration tests for FTR flow

### Phase 4: FTR UI (Client Portal)
- [ ] Build Login ID entry screen
- [ ] Build OTP entry screen with countdown timer
- [ ] Build password setup screen with validation
- [ ] Build MFA enrollment screen
- [ ] Implement flow navigation between screens
- [ ] Handle error states and retry logic

### Phase 5: Email Change Re-verification
- [ ] Detect email changes in user update flow
- [ ] Set `email_verified: false` on email change
- [ ] Trigger OTP flow on next login
- [ ] Update UI to handle re-verification state

### Phase 6: Password Reset Flow
- [ ] Create `/api/login/password/reset-request` endpoint
- [ ] Require OTP verification before password reset
- [ ] Build password reset UI screens
- [ ] Integrate with Auth0 password update API

### Phase 7: Passkey Support
- [ ] Enable passkeys in Auth0 tenant configuration
- [ ] Enable "Identifier First" login flow in Auth0
- [ ] Create `/api/login/passkey/register-options` endpoint
- [ ] Create `/api/login/passkey/register` endpoint
- [ ] Create `/api/login/passkey/authenticate-options` endpoint
- [ ] Create `/api/login/passkey/authenticate` endpoint
- [ ] Build passkey enrollment UI
- [ ] Implement passkey authentication in login flow
- [ ] Add passkey management UI (list/revoke)

### Phase 8: MFA Improvements
- [ ] Implement custom logic to skip MFA when passkey with user verification is used
- [ ] Create Guardian re-binding API endpoints
- [ ] Create Guardian re-binding UI flow
- [ ] Implement step-up authentication for sensitive operations
- [ ] Add MFA method preference settings

## References

- [Auth0 Passkeys Documentation](https://auth0.com/blog/all-you-need-to-know-about-passkeys-at-auth0/)
- [WebAuthn as MFA](https://auth0.com/docs/secure/multi-factor-authentication/webauthn-as-mfa)
- [Auth0 Management API - Users](https://auth0.com/docs/api/management/v2/users)
- [MailerSend API](https://www.mailersend.com/features/email-api)
- [AhaSend API](https://ahasend.com/free-email-api)
