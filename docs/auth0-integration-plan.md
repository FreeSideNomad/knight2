# Auth0 Integration Plan

This document outlines the implementation steps to integrate the Knight platform with Auth0 for identity management, specifically focusing on user provisioning capabilities modelled after the reference shell script implementation.

## Overview

The Knight platform has a foundational Auth0 integration layer in the `auth0-identity` module that defines interfaces and placeholder implementations. This plan covers completing the integration to enable real Auth0 Management API calls for user provisioning.

### Current State

- **Interface defined**: `Auth0IdentityService` with `provisionUser()`, CRUD operations, and onboarding status
- **Adapter skeleton**: `Auth0IdentityAdapter` with TODO placeholders for actual API calls
- **Token service**: `Auth0TokenService` / `Auth0TokenAdapter` for management token retrieval
- **Configuration**: `Auth0Config` record and `Auth0ConfigProperties` for Spring configuration
- **Events**: Domain events for user creation, blocking, and linking

### Target Behavior (from reference script)

1. Check if user already exists by email
2. Generate a secure temporary password
3. Create user with `app_metadata` for tracking provisioning
4. Create a password change ticket for user onboarding
5. Return user ID and password reset URL

---

## Phase 1: Dependencies and Configuration

### 1.1 HTTP Client Dependency

The `auth0-identity` module already has `spring-web` dependency which includes Spring RestClient (Spring 6.1+). No additional dependencies required.

```xml
<!-- Already present in domain/auth0-identity/pom.xml -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-web</artifactId>
</dependency>
```

### 1.2 Update Application Configuration

Add Auth0 configuration to `application/src/main/resources/application.yml`:

```yaml
# Auth0 Configuration
auth0:
  domain: ${AUTH0_DOMAIN:dbc-test.auth0.com}
  client-id: ${AUTH0_M2M_CLIENT_ID:}
  client-secret: ${AUTH0_M2M_CLIENT_SECRET:}
  audience: ${AUTH0_API_AUDIENCE:https://auth-gateway.local/api}
  management-audience: https://${AUTH0_DOMAIN:dbc-test.auth0.com}/api/v2/
  connection: ${AUTH0_CONNECTION:Username-Password-Authentication}
  password-reset-result-url: ${AUTH0_PASSWORD_RESET_URL:http://localhost:8080/}
```

### 1.3 Environment Variables

Required environment variables for deployment (reference values from existing `.env`):

| Variable | Description | Current Value |
|----------|-------------|---------------|
| `AUTH0_DOMAIN` | Auth0 tenant domain | `dbc-test.auth0.com` |
| `AUTH0_M2M_CLIENT_ID` | Machine-to-machine application client ID | `yxeGeuWemoYWTqjManvon2u8RAdWVfls` |
| `AUTH0_M2M_CLIENT_SECRET` | Machine-to-machine application client secret | *(stored securely)* |
| `AUTH0_API_AUDIENCE` | API audience for JWT tokens | `https://auth-gateway.local/api` |
| `AUTH0_CONNECTION` | Database connection name | `Username-Password-Authentication` |
| `AUTH0_PASSWORD_RESET_URL` | Redirect URL after password reset | `http://localhost:8080/` |

**Note**: The M2M (Machine-to-Machine) credentials must have Management API permissions for user provisioning operations.

---

## Phase 2: Auth0 Management API HTTP Client

### 2.1 Update Auth0Config Record

First, update `Auth0Config.java` to include the password reset result URL:

```java
package com.knight.domain.auth0identity.config;

/**
 * Auth0 configuration properties.
 */
public record Auth0Config(
    String domain,
    String clientId,
    String clientSecret,
    String audience,
    String managementAudience,
    String connection,
    String passwordResetResultUrl  // New field
) {
    public String getIssuer() {
        return "https://" + domain + "/";
    }

    public String getManagementApiUrl() {
        return "https://" + domain + "/api/v2";
    }

    public String getTokenUrl() {
        return "https://" + domain + "/oauth/token";
    }

    public String getPasswordResetResultUrl() {
        return passwordResetResultUrl != null ? passwordResetResultUrl : "http://localhost/";
    }
}
```

Update `Auth0ConfigProperties.java` accordingly:

```java
@Configuration
@ConfigurationProperties(prefix = "auth0")
public class Auth0ConfigProperties {
    // ... existing fields ...
    private String passwordResetResultUrl = "http://localhost/";

    @Bean
    public Auth0Config auth0Config() {
        return new Auth0Config(
            domain, clientId, clientSecret, audience,
            managementAudience, connection, passwordResetResultUrl
        );
    }

    // ... getters and setters ...
}
```

### 2.2 Create HTTP Client Component

Create `Auth0HttpClient.java` using Spring RestClient:

```java
package com.knight.domain.auth0identity.adapter;

import com.knight.domain.auth0identity.api.Auth0TokenService;
import com.knight.domain.auth0identity.config.Auth0Config;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class Auth0HttpClient {

    private final RestClient restClient;
    private final Auth0TokenService tokenService;

    public Auth0HttpClient(Auth0Config config, Auth0TokenService tokenService) {
        this.tokenService = tokenService;
        this.restClient = RestClient.builder()
            .baseUrl(config.getManagementApiUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public <T> T get(String uri, Class<T> responseType) {
        return restClient.get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
            .retrieve()
            .body(responseType);
    }

    public <T> T post(String uri, Object body, Class<T> responseType) {
        return restClient.post()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
            .body(body)
            .retrieve()
            .body(responseType);
    }

    public <T> T patch(String uri, Object body, Class<T> responseType) {
        return restClient.patch()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
            .body(body)
            .retrieve()
            .body(responseType);
    }

    public void delete(String uri) {
        restClient.delete()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
            .retrieve()
            .toBodilessEntity();
    }
}
```

### 2.3 Define Auth0 API DTOs

Create request/response DTOs in `com.knight.domain.auth0identity.adapter.dto`:

```java
// Auth0CreateUserRequest.java
public record Auth0CreateUserRequest(
    String email,
    String connection,
    String password,
    String name,
    @JsonProperty("given_name") String givenName,
    @JsonProperty("family_name") String familyName,
    @JsonProperty("email_verified") boolean emailVerifiedStatus,
    @JsonProperty("verify_email") boolean triggerEmailVerificationOnCreate,
    @JsonProperty("app_metadata") AppMetadata appMetadata
) {
    public record AppMetadata(
        @JsonProperty("internal_user_id") String internalUserId,
        @JsonProperty("profile_id") String profileId,
        @JsonProperty("provisioned_by") String provisionedBy,
        @JsonProperty("provisioned_at") String provisionedAt,
        @JsonProperty("onboarding_status") String onboardingStatus,
        @JsonProperty("mfa_enrolled") boolean mfaEnrolled
    ) {}
}

// Auth0UserResponse.java
public record Auth0UserResponse(
    @JsonProperty("user_id") String userId,
    String email,
    String name,
    @JsonProperty("given_name") String givenName,
    @JsonProperty("family_name") String familyName,
    @JsonProperty("email_verified") boolean emailVerifiedStatus,
    boolean blocked,
    String picture,
    @JsonProperty("last_login") String lastLogin,
    @JsonProperty("app_metadata") Map<String, Object> appMetadata,
    @JsonProperty("user_metadata") Map<String, Object> userMetadata
) {}

// Auth0PasswordChangeTicketRequest.java
public record Auth0PasswordChangeTicketRequest(
    @JsonProperty("user_id") String userId,
    @JsonProperty("result_url") String resultUrl,
    @JsonProperty("ttl_sec") int ttlSec,
    @JsonProperty("mark_email_as_verified") boolean markEmailAsVerified
) {}

// Auth0PasswordChangeTicketResponse.java
public record Auth0PasswordChangeTicketResponse(
    String ticket
) {}
```

---

## Phase 3: Implement Token Service

### 3.1 Update Auth0TokenAdapter

Replace placeholder implementation with actual OAuth2 client credentials flow using Spring RestClient:

```java
package com.knight.domain.auth0identity.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.knight.domain.auth0identity.api.Auth0TokenService;
import com.knight.domain.auth0identity.config.Auth0Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class Auth0TokenAdapter implements Auth0TokenService {

    private static final Logger log = LoggerFactory.getLogger(Auth0TokenAdapter.class);

    private final Auth0Config config;
    private final RestClient restClient;

    private String cachedManagementToken;
    private Instant managementTokenExpiry;

    public Auth0TokenAdapter(Auth0Config config) {
        this.config = config;
        this.restClient = RestClient.create();
    }

    @Override
    public String getManagementApiToken() {
        // Return cached token if still valid (with 60 second buffer)
        if (cachedManagementToken != null && managementTokenExpiry != null
            && Instant.now().plusSeconds(60).isBefore(managementTokenExpiry)) {
            return cachedManagementToken;
        }

        log.info("Requesting new Auth0 Management API token for domain: {}", config.domain());

        var tokenRequest = Map.of(
            "grant_type", "client_credentials",
            "client_id", config.clientId(),
            "client_secret", config.clientSecret(),
            "audience", config.managementAudience()
        );

        TokenResponse response = restClient.post()
            .uri(config.getTokenUrl())
            .contentType(MediaType.APPLICATION_JSON)
            .body(tokenRequest)
            .retrieve()
            .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new Auth0IntegrationException("Failed to obtain management API token");
        }

        cachedManagementToken = response.accessToken();
        // Cache token until 60 seconds before expiry
        managementTokenExpiry = Instant.now().plusSeconds(response.expiresIn() - 60);

        log.info("Auth0 Management API token obtained, expires at: {}", managementTokenExpiry);

        return cachedManagementToken;
    }

    @Override
    public Optional<TokenInfo> validateToken(String accessToken) {
        // TODO: Implement JWT validation using JWKS
        // For now, token validation is handled by Spring Security OAuth2 Resource Server
        return Optional.empty();
    }

    @Override
    public void revokeUserTokens(String auth0UserId) {
        // Invalidate user sessions via Management API
        restClient.post()
            .uri(config.getManagementApiUrl() + "/users/" + auth0UserId + "/invalidate-remember-browser")
            .header("Authorization", "Bearer " + getManagementApiToken())
            .retrieve()
            .toBodilessEntity();
    }

    private record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("token_type") String tokenType,
        String scope
    ) {}
}
```

---

## Phase 4: Implement User Provisioning

### 4.1 Update Auth0IdentityAdapter

Implement the `provisionUser` method matching the reference script behavior:

```java
@Override
public ProvisionUserResult provisionUser(ProvisionUserRequest request) {
    // 1. Check if user already exists
    Optional<Auth0UserInfo> existing = getUserByEmail(request.email());
    if (existing.isPresent()) {
        throw new UserAlreadyExistsException(
            "User already exists in Auth0: " + request.email() +
            " (ID: " + existing.get().auth0UserId() + ")"
        );
    }

    // 2. Generate temporary password
    String tempPassword = generateSecurePassword();

    // 3. Build user creation request
    String fullName = buildFullName(request.firstName(), request.lastName());

    var createRequest = new Auth0CreateUserRequest(
        request.email(),
        config.connection(),
        tempPassword,
        fullName,
        request.firstName(),
        request.lastName(),
        false,  // emailVerifiedStatus - not verified yet
        true,   // triggerEmailVerificationOnCreate - sends verification email
        new Auth0CreateUserRequest.AppMetadata(
            request.internalUserId(),
            request.profileId(),
            "knight_platform",
            Instant.now().toString(),
            "pending",
            false
        )
    );

    // 4. Create user in Auth0
    Auth0UserResponse userResponse = httpClient.post(
        "/users",
        createRequest,
        Auth0UserResponse.class
    );

    if (userResponse == null || userResponse.userId() == null) {
        throw new Auth0IntegrationException("Failed to create user in Auth0");
    }

    String auth0UserId = userResponse.userId();

    // 5. Create password change ticket
    var ticketRequest = new Auth0PasswordChangeTicketRequest(
        auth0UserId,
        config.getPasswordResetResultUrl(),  // Add to config
        604800,  // 7 days in seconds
        true     // Mark email as verified after password set
    );

    Auth0PasswordChangeTicketResponse ticketResponse = httpClient.post(
        "/tickets/password-change",
        ticketRequest,
        Auth0PasswordChangeTicketResponse.class
    );

    String resetUrl = ticketResponse != null ? ticketResponse.ticket() : null;

    // 6. Publish domain event
    eventPublisher.publishEvent(new Auth0UserCreated(
        auth0UserId,
        request.email(),
        fullName,
        Instant.now()
    ));

    return new ProvisionUserResult(auth0UserId, resetUrl, Instant.now());
}
```

### 4.2 Implement Remaining CRUD Operations

```java
@Override
public Optional<Auth0UserInfo> getUserByEmail(String email) {
    String encodedEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
    Auth0UserResponse[] users = httpClient.get(
        "/users-by-email?email=" + encodedEmail,
        Auth0UserResponse[].class
    );

    if (users == null || users.length == 0) {
        return Optional.empty();
    }

    return Optional.of(mapToUserInfo(users[0]));
}

@Override
public Optional<Auth0UserInfo> getUser(String auth0UserId) {
    try {
        Auth0UserResponse user = httpClient.get(
            "/users/" + auth0UserId,
            Auth0UserResponse.class
        );
        return Optional.ofNullable(user).map(this::mapToUserInfo);
    } catch (Exception e) {
        return Optional.empty();
    }
}

@Override
public void blockUser(String auth0UserId) {
    httpClient.patch(
        "/users/" + auth0UserId,
        Map.of("blocked", true),
        Auth0UserResponse.class
    );

    eventPublisher.publishEvent(new Auth0UserBlocked(auth0UserId, Instant.now()));
}

@Override
public void unblockUser(String auth0UserId) {
    httpClient.patch(
        "/users/" + auth0UserId,
        Map.of("blocked", false),
        Auth0UserResponse.class
    );
}

@Override
public void deleteUser(String auth0UserId) {
    httpClient.delete("/users/" + auth0UserId);
}

private Auth0UserInfo mapToUserInfo(Auth0UserResponse response) {
    return new Auth0UserInfo(
        response.userId(),
        response.email(),
        response.name(),
        response.emailVerifiedStatus(),
        response.blocked(),
        response.picture(),
        response.lastLogin()
    );
}
```

### 4.3 Update OnboardingStatus to Use Enum

First, update the `OnboardingStatus` record in `Auth0IdentityService.java` to use an enum:

```java
// In Auth0IdentityService.java - update the OnboardingStatus record
record OnboardingStatus(
    String identityProviderUserId,
    boolean passwordSet,
    boolean mfaEnrolled,
    OnboardingState state,  // Use enum instead of String
    Instant lastLogin
) {}

enum OnboardingState {
    PENDING_PASSWORD,   // User created, awaiting password set
    PENDING_MFA,        // Password set, awaiting MFA enrollment
    COMPLETE            // Fully onboarded
}
```

### 4.4 Implement Onboarding Status Check

```java
@Override
public OnboardingStatus getOnboardingStatus(String identityProviderUserId) {
    Auth0UserResponse user = httpClient.get(
        "/users/" + identityProviderUserId,
        Auth0UserResponse.class
    );

    if (user == null) {
        throw new Auth0IntegrationException("User not found: " + identityProviderUserId);
    }

    // Check MFA enrollments
    MfaEnrollment[] enrollments = httpClient.get(
        "/users/" + identityProviderUserId + "/enrollments",
        MfaEnrollment[].class
    );

    boolean mfaEnrolled = enrollments != null && enrollments.length > 0;
    boolean passwordSet = user.emailVerifiedStatus(); // Proxy: verified after password reset

    OnboardingState state = determineOnboardingState(passwordSet, mfaEnrolled);

    return new OnboardingStatus(
        identityProviderUserId,
        passwordSet,
        mfaEnrolled,
        state,
        user.lastLogin() != null ? Instant.parse(user.lastLogin()) : null
    );
}

private OnboardingState determineOnboardingState(boolean passwordSet, boolean mfaEnrolled) {
    if (!passwordSet) {
        return OnboardingState.PENDING_PASSWORD;
    } else if (!mfaEnrolled) {
        return OnboardingState.PENDING_MFA;
    } else {
        return OnboardingState.COMPLETE;
    }
}

private record MfaEnrollment(
    String id,
    String status,
    String type,
    @JsonProperty("enrolled_at") String enrolledAt
) {}
```

---

## Phase 5: Error Handling

### 5.1 Create Exception Classes

```java
// Auth0IntegrationException.java
public class Auth0IntegrationException extends RuntimeException {
    public Auth0IntegrationException(String message) {
        super(message);
    }

    public Auth0IntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}

// UserAlreadyExistsException.java
public class UserAlreadyExistsException extends Auth0IntegrationException {
    private final String email;
    private final String existingUserId;

    public UserAlreadyExistsException(String message) {
        super(message);
        this.email = null;
        this.existingUserId = null;
    }

    public UserAlreadyExistsException(String email, String existingUserId) {
        super("User already exists: " + email);
        this.email = email;
        this.existingUserId = existingUserId;
    }
}
```

### 5.2 Add Error Response Handling

Enhance `Auth0HttpClient` with error handling:

```java
public <T> T post(String uri, Object body, Class<T> responseType) {
    return restClient.post()
        .uri(uri)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.getManagementApiToken())
        .body(body)
        .retrieve()
        .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
            String errorBody = new String(response.getBody().readAllBytes());
            throw new Auth0IntegrationException(
                "Auth0 API error: " + response.getStatusCode() + " - " + errorBody
            );
        })
        .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
            throw new Auth0IntegrationException(
                "Auth0 service unavailable: " + response.getStatusCode()
            );
        })
        .body(responseType);
}
```

---

## Phase 6: Testing

### 6.1 Unit Tests

Create `Auth0IdentityAdapterTest.java`:

```java
@ExtendWith(MockitoExtension.class)
class Auth0IdentityAdapterTest {

    @Mock private Auth0Config config;
    @Mock private Auth0TokenService tokenService;
    @Mock private Auth0HttpClient httpClient;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private Auth0IdentityAdapter adapter;

    @Test
    void provisionUser_shouldCreateUserAndReturnResetUrl() {
        // Given
        var request = new ProvisionUserRequest(
            "john@example.com", "John", "Doe", "user-123", "profile-456"
        );

        when(httpClient.get(contains("/users-by-email"), eq(Auth0UserResponse[].class)))
            .thenReturn(new Auth0UserResponse[0]);

        when(httpClient.post(eq("/users"), any(), eq(Auth0UserResponse.class)))
            .thenReturn(new Auth0UserResponse("auth0|abc123", "john@example.com",
                "John Doe", "John", "Doe", false, false, null, null, null, null));

        when(httpClient.post(eq("/tickets/password-change"), any(),
            eq(Auth0PasswordChangeTicketResponse.class)))
            .thenReturn(new Auth0PasswordChangeTicketResponse("https://reset.url"));

        // When
        var result = adapter.provisionUser(request);

        // Then
        assertThat(result.identityProviderUserId()).isEqualTo("auth0|abc123");
        assertThat(result.passwordResetUrl()).isEqualTo("https://reset.url");
        verify(eventPublisher).publishEvent(any(Auth0UserCreated.class));
    }

    @Test
    void provisionUser_shouldThrowWhenUserExists() {
        // Given
        var request = new ProvisionUserRequest(
            "john@example.com", "John", "Doe", "user-123", "profile-456"
        );

        when(httpClient.get(contains("/users-by-email"), eq(Auth0UserResponse[].class)))
            .thenReturn(new Auth0UserResponse[] {
                new Auth0UserResponse("auth0|existing", "john@example.com",
                    "John Doe", null, null, true, false, null, null, null, null)
            });

        // When/Then
        assertThatThrownBy(() -> adapter.provisionUser(request))
            .isInstanceOf(UserAlreadyExistsException.class)
            .hasMessageContaining("already exists");
    }
}
```

### 6.2 Integration Tests

Create `Auth0IntegrationTest.java` for testing against real Auth0 (in separate test profile):

```java
@SpringBootTest
@ActiveProfiles("auth0-integration-test")
@Disabled("Run manually with real Auth0 credentials")
class Auth0IntegrationTest {

    @Autowired
    private Auth0IdentityService auth0Service;

    @Test
    void shouldProvisionAndDeleteUser() {
        String testEmail = "test-" + UUID.randomUUID() + "@example.com";

        // Provision
        var result = auth0Service.provisionUser(new ProvisionUserRequest(
            testEmail, "Test", "User", "internal-123", "profile-456"
        ));

        assertThat(result.identityProviderUserId()).startsWith("auth0|");
        assertThat(result.passwordResetUrl()).isNotBlank();

        // Verify user exists
        var user = auth0Service.getUserByEmail(testEmail);
        assertThat(user).isPresent();

        // Cleanup
        auth0Service.deleteUser(result.identityProviderUserId());

        // Verify deleted
        var deleted = auth0Service.getUserByEmail(testEmail);
        assertThat(deleted).isEmpty();
    }
}
```

---

## Phase 7: Auth0 Tenant Configuration

### 7.1 Configure Machine-to-Machine Application

An M2M application already exists in the `dbc-test.auth0.com` tenant:

- **Client ID**: `yxeGeuWemoYWTqjManvon2u8RAdWVfls`
- **Client Secret**: Stored in `.env` as `AUTH0_M2M_CLIENT_SECRET`

Verify the M2M app has Management API permissions:

1. Go to Auth0 Dashboard > Applications > APIs > Auth0 Management API
2. Click "Machine to Machine Applications" tab
3. Find the M2M app and ensure these scopes are granted:
   - `read:users`
   - `create:users`
   - `update:users`
   - `delete:users`
   - `read:user_idp_tokens`
   - `create:user_tickets`
   - `read:guardian_enrollments` (for MFA status)

### 7.2 Configure Database Connection

1. Go to Authentication > Database > Username-Password-Authentication
2. Enable "Requires Username": No (use email as identifier)
3. Password Policy: Set to "Excellent" or custom policy matching security requirements
4. Disable Sign Ups: Yes (only admin provisioning allowed)

### 7.3 Configure MFA (Guardian)

1. Go to Security > Multi-factor Auth
2. Enable Push Notifications (Guardian)
3. Set Policy: "Always" or "Adaptive" based on requirements
4. Configure enrollment: Required for all users

### 7.4 Configure Password Reset Email Template

1. Go to Branding > Email Templates > Change Password
2. Customize template with Knight branding
3. Set redirect URL to Knight portal

---

## Phase 8: Security Considerations

### 8.1 Secret Management

- Store `AUTH0_CLIENT_SECRET` in a secrets manager (AWS Secrets Manager, HashiCorp Vault)
- Never log or expose secrets in error messages
- Rotate secrets periodically

### 8.2 Rate Limiting

Auth0 Management API has rate limits. Implement:

```java
@Component
public class Auth0RateLimiter {
    private final RateLimiter rateLimiter = RateLimiter.create(10.0); // 10 requests/sec

    public void acquire() {
        rateLimiter.acquire();
    }
}
```

### 8.3 Audit Logging

Log all Auth0 operations for audit purposes:

```java
@Aspect
@Component
public class Auth0AuditAspect {

    private static final Logger auditLog = LoggerFactory.getLogger("audit.auth0");

    @Around("execution(* com.knight.domain.auth0identity.adapter.Auth0IdentityAdapter.*(..))")
    public Object auditAuth0Operation(ProceedingJoinPoint joinPoint) throws Throwable {
        String operation = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        auditLog.info("Auth0 operation started: {} with args: {}", operation, args);

        try {
            Object result = joinPoint.proceed();
            auditLog.info("Auth0 operation completed: {}", operation);
            return result;
        } catch (Exception e) {
            auditLog.error("Auth0 operation failed: {} - {}", operation, e.getMessage());
            throw e;
        }
    }
}
```

---

## Implementation Checklist

### Configuration
- [ ] Add Auth0 configuration to `application.yml` (see Phase 1.2)
- [ ] Update `Auth0Config` record with `passwordResetResultUrl` field
- [ ] Update `Auth0ConfigProperties` with new field and getter/setter
- [ ] Set environment variables: `AUTH0_DOMAIN`, `AUTH0_M2M_CLIENT_ID`, `AUTH0_M2M_CLIENT_SECRET`

### HTTP Client Layer
- [ ] Create `Auth0HttpClient` component using Spring RestClient (Phase 2.2)
- [ ] Create Auth0 API DTOs in `adapter.dto` package (Phase 2.3)
- [ ] Create exception classes: `Auth0IntegrationException`, `UserAlreadyExistsException`
- [ ] Add error handling to `Auth0HttpClient` (Phase 5.2)

### Token Service
- [ ] Implement `Auth0TokenAdapter.getManagementApiToken()` with caching (Phase 3.1)
- [ ] Implement `Auth0TokenAdapter.revokeUserTokens()`

### Identity Service
- [ ] Update `OnboardingStatus` record to use `OnboardingState` enum (Phase 4.3)
- [ ] Implement `Auth0IdentityAdapter.provisionUser()` (Phase 4.1)
- [ ] Implement `Auth0IdentityAdapter.getUserByEmail()` (Phase 4.2)
- [ ] Implement `Auth0IdentityAdapter.getUser()` (Phase 4.2)
- [ ] Implement `Auth0IdentityAdapter.getOnboardingStatus()` (Phase 4.4)
- [ ] Implement `Auth0IdentityAdapter.resendPasswordResetEmail()`
- [ ] Implement `Auth0IdentityAdapter.blockUser()` (Phase 4.2)
- [ ] Implement `Auth0IdentityAdapter.unblockUser()` (Phase 4.2)
- [ ] Implement `Auth0IdentityAdapter.deleteUser()` (Phase 4.2)

### Testing
- [ ] Write unit tests for `Auth0IdentityAdapter` (Phase 6.1)
- [ ] Write integration tests with real Auth0 tenant (Phase 6.2)

### Auth0 Tenant Setup
- [ ] Verify M2M app (`yxeGeuWemoYWTqjManvon2u8RAdWVfls`) has Management API scopes
- [ ] Configure database connection settings (Phase 7.2)
- [ ] Configure MFA settings (Phase 7.3)
- [ ] Customize password reset email template (Phase 7.4)

### Event Integration (Phase 9)
- [ ] Add `KAFKA_PLATFORM_EVENTS_TOPIC` to `.env` files (both knight2 and okta-app)
- [ ] Create `PlatformEventType` enum (Phase 9.6)
- [ ] Create `PlatformEvent` record (Phase 9.6)
- [ ] Implement `Auth0UserService` in okta-app with event publishing (Phase 9.4)
- [ ] Implement `UserOnboardingEventConsumer` in knight2 (Phase 9.5)
- [ ] Add onboarding status fields to User aggregate (Phase 9.7)
- [ ] Add database migration for onboarding status columns

### Production Readiness
- [ ] Add rate limiting (Phase 8.2)
- [ ] Add audit logging (Phase 8.3)
- [ ] Configure secrets management for `AUTH0_M2M_CLIENT_SECRET`

---

## Phase 9: User Onboarding Events

This section describes the event-driven communication between applications during user onboarding. The okta-app invokes Auth0 Management API operations and publishes events to Kafka upon successful API responses for the Knight platform to consume.

### 9.1 Event Flow Overview

```
┌─────────────┐                      ┌───────────────┐     ┌─────────────┐
│  okta-app   │─────API calls───────▶│    Auth0      │     │   knight2   │
│ (Publisher) │◀────responses────────│ Management API│     │ (Consumer)  │
└──────┬──────┘                      └───────────────┘     └──────▲──────┘
       │                                                          │
       │  on success                                              │
       │                                                          │
       └──────────────────▶ Kafka (platform-events) ──────────────┘
```

**okta-app** → Calls Auth0 Management API, publishes events on success
**Auth0** → Processes API requests (password change, user queries)
**Kafka** → `platform-events` topic for cross-application messaging
**knight2** → Consumes events, updates user onboarding status

### 9.2 Environment Configuration

Add to both applications' `.env` files:

```bash
# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_PLATFORM_EVENTS_TOPIC=platform-events
```

### 9.3 Event Definitions

#### UserPasswordSet Event

Published by **okta-app** after successfully verifying user has set their password via Auth0 Management API.

```json
{
  "eventType": "USER_PASSWORD_SET",
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "timestamp": "2024-01-15T10:30:00Z",
  "source": "okta-app",
  "payload": {
    "auth0UserId": "auth0|abc123def456",
    "email": "john.doe@example.com",
    "passwordSetAt": "2024-01-15T10:30:00Z"
  }
}
```

#### UserMfaEnrolled Event

Published by **okta-app** after successfully detecting MFA enrollment via Auth0 Management API.

```json
{
  "eventType": "USER_MFA_ENROLLED",
  "eventId": "550e8400-e29b-41d4-a716-446655440002",
  "timestamp": "2024-01-15T10:35:00Z",
  "source": "okta-app",
  "payload": {
    "auth0UserId": "auth0|abc123def456",
    "email": "john.doe@example.com",
    "mfaType": "guardian",
    "enrolledAt": "2024-01-15T10:35:00Z"
  }
}
```

#### UserOnboardingComplete Event

Published by **okta-app** when user completes both password setup and MFA enrollment.

```json
{
  "eventType": "USER_ONBOARDING_COMPLETE",
  "eventId": "550e8400-e29b-41d4-a716-446655440003",
  "timestamp": "2024-01-15T10:35:00Z",
  "source": "okta-app",
  "payload": {
    "auth0UserId": "auth0|abc123def456",
    "email": "john.doe@example.com",
    "passwordSetAt": "2024-01-15T10:30:00Z",
    "mfaEnrolledAt": "2024-01-15T10:35:00Z",
    "onboardingCompletedAt": "2024-01-15T10:35:00Z"
  }
}
```

### 9.4 okta-app Auth0 Service (Publisher)

The okta-app calls Auth0 Management API and publishes events to Kafka on success:

```java
// In okta-app: Auth0UserService.java
@Service
public class Auth0UserService {

    private final Auth0ManagementClient auth0Client;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.platform-events-topic:platform-events}")
    private String platformEventsTopic;

    /**
     * Called after user successfully sets their password via Auth0 password reset flow.
     * The okta-app detects this by polling or checking user status after login.
     */
    public void handlePasswordSet(String auth0UserId) {
        // Verify password was set by checking Auth0 user status
        Auth0User user = auth0Client.getUser(auth0UserId);

        if (user.isEmailVerified()) {
            publishEvent(new PlatformEvent(
                PlatformEventType.USER_PASSWORD_SET,
                UUID.randomUUID().toString(),
                Instant.now(),
                "okta-app",
                Map.of(
                    "auth0UserId", auth0UserId,
                    "email", user.getEmail(),
                    "passwordSetAt", Instant.now().toString()
                )
            ));

            // Update app_metadata to track password set
            auth0Client.updateAppMetadata(auth0UserId, Map.of(
                "password_set", true,
                "password_set_at", Instant.now().toString()
            ));
        }
    }

    /**
     * Called after user successfully enrolls in MFA.
     * Detected by checking MFA enrollments after successful login.
     */
    public void handleMfaEnrollment(String auth0UserId) {
        // Check MFA enrollments via Auth0 Management API
        List<MfaEnrollment> enrollments = auth0Client.getMfaEnrollments(auth0UserId);

        if (!enrollments.isEmpty()) {
            MfaEnrollment enrollment = enrollments.get(0);

            publishEvent(new PlatformEvent(
                PlatformEventType.USER_MFA_ENROLLED,
                UUID.randomUUID().toString(),
                Instant.now(),
                "okta-app",
                Map.of(
                    "auth0UserId", auth0UserId,
                    "email", auth0Client.getUser(auth0UserId).getEmail(),
                    "mfaType", enrollment.getType(),
                    "enrolledAt", enrollment.getEnrolledAt()
                )
            ));

            // Update app_metadata
            auth0Client.updateAppMetadata(auth0UserId, Map.of(
                "mfa_enrolled", true,
                "mfa_enrolled_at", Instant.now().toString()
            ));

            // Check if onboarding is complete
            checkAndPublishOnboardingComplete(auth0UserId);
        }
    }

    /**
     * Check if user has completed all onboarding steps and publish completion event.
     */
    private void checkAndPublishOnboardingComplete(String auth0UserId) {
        Auth0User user = auth0Client.getUser(auth0UserId);
        Map<String, Object> appMetadata = user.getAppMetadata();

        boolean passwordSet = Boolean.TRUE.equals(appMetadata.get("password_set"));
        boolean mfaEnrolled = Boolean.TRUE.equals(appMetadata.get("mfa_enrolled"));

        if (passwordSet && mfaEnrolled) {
            publishEvent(new PlatformEvent(
                PlatformEventType.USER_ONBOARDING_COMPLETE,
                UUID.randomUUID().toString(),
                Instant.now(),
                "okta-app",
                Map.of(
                    "auth0UserId", auth0UserId,
                    "email", user.getEmail(),
                    "passwordSetAt", appMetadata.get("password_set_at"),
                    "mfaEnrolledAt", appMetadata.get("mfa_enrolled_at"),
                    "onboardingCompletedAt", Instant.now().toString()
                )
            ));

            // Update final status
            auth0Client.updateAppMetadata(auth0UserId, Map.of(
                "onboarding_status", "complete",
                "onboarding_completed_at", Instant.now().toString()
            ));
        }
    }

    private void publishEvent(PlatformEvent event) {
        try {
            String auth0UserId = (String) event.payload().get("auth0UserId");
            kafkaTemplate.send(platformEventsTopic, auth0UserId,
                objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
```

### 9.5 knight2 Event Consumer

The knight2 application consumes events and updates user status:

```java
// In knight2: UserOnboardingEventConsumer.java
package com.knight.application.events;

@Component
public class UserOnboardingEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserOnboardingEventConsumer.class);

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${kafka.platform-events-topic:platform-events}",
        groupId = "knight-platform-onboarding"
    )
    public void handlePlatformEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key) {

        PlatformEvent event = objectMapper.readValue(message, PlatformEvent.class);

        switch (event.eventType()) {
            case USER_PASSWORD_SET -> handlePasswordSet(event);
            case USER_MFA_ENROLLED -> handleMfaEnrolled(event);
            case USER_ONBOARDING_COMPLETE -> handleOnboardingComplete(event);
        }
    }

    private void handlePasswordSet(PlatformEvent event) {
        String auth0UserId = (String) event.payload().get("auth0UserId");
        log.info("User password set: {}", auth0UserId);

        userRepository.findByIdentityProviderUserId(auth0UserId)
            .ifPresent(user -> {
                user.markPasswordSet(Instant.parse((String) event.payload().get("passwordSetAt")));
                userRepository.save(user);

                eventPublisher.publishEvent(new UserPasswordSetEvent(
                    user.getId(),
                    auth0UserId
                ));
            });
    }

    private void handleMfaEnrolled(PlatformEvent event) {
        String auth0UserId = (String) event.payload().get("auth0UserId");
        log.info("User MFA enrolled: {}", auth0UserId);

        userRepository.findByIdentityProviderUserId(auth0UserId)
            .ifPresent(user -> {
                user.markMfaEnrolled(
                    (String) event.payload().get("mfaType"),
                    Instant.parse((String) event.payload().get("enrolledAt"))
                );
                userRepository.save(user);

                eventPublisher.publishEvent(new UserMfaEnrolledEvent(
                    user.getId(),
                    auth0UserId
                ));
            });
    }

    private void handleOnboardingComplete(PlatformEvent event) {
        String auth0UserId = (String) event.payload().get("auth0UserId");
        log.info("User onboarding complete: {}", auth0UserId);

        userRepository.findByIdentityProviderUserId(auth0UserId)
            .ifPresent(user -> {
                user.completeOnboarding(
                    Instant.parse((String) event.payload().get("onboardingCompletedAt"))
                );
                userRepository.save(user);

                eventPublisher.publishEvent(new UserOnboardingCompletedEvent(
                    user.getId(),
                    auth0UserId
                ));
            });
    }
}
```

### 9.6 Event Schema (Shared)

Define a shared event schema for the `platform-events` topic:

```java
// PlatformEventType.java - Enum for event types
public enum PlatformEventType {
    USER_PASSWORD_SET,
    USER_MFA_ENROLLED,
    USER_ONBOARDING_COMPLETE
}

// PlatformEvent.java - Shared record for platform events
public record PlatformEvent(
    PlatformEventType eventType,
    UUID eventId,
    Instant timestamp,
    String source,
    Map<String, Object> payload
) {
    public PlatformEvent(PlatformEventType eventType, String source, Map<String, Object> payload) {
        this(eventType, UUID.randomUUID(), Instant.now(), source, payload);
    }
}
```

### 9.7 User Domain Updates

Update the User aggregate to track onboarding status:

```java
// In knight2: User.java additions
public class User {
    // ... existing fields ...

    private Instant passwordSetAt;
    private Instant mfaEnrolledAt;
    private String mfaType;
    private Instant onboardingCompletedAt;
    private OnboardingStatus onboardingStatus = OnboardingStatus.PENDING_PASSWORD;

    public void markPasswordSet(Instant timestamp) {
        this.passwordSetAt = timestamp;
        updateOnboardingStatus();
    }

    public void markMfaEnrolled(String mfaType, Instant timestamp) {
        this.mfaType = mfaType;
        this.mfaEnrolledAt = timestamp;
        updateOnboardingStatus();
    }

    public void completeOnboarding(Instant timestamp) {
        this.onboardingCompletedAt = timestamp;
        this.onboardingStatus = OnboardingStatus.COMPLETE;
    }

    private void updateOnboardingStatus() {
        if (passwordSetAt == null) {
            onboardingStatus = OnboardingStatus.PENDING_PASSWORD;
        } else if (mfaEnrolledAt == null) {
            onboardingStatus = OnboardingStatus.PENDING_MFA;
        } else {
            onboardingStatus = OnboardingStatus.COMPLETE;
            onboardingCompletedAt = Instant.now();
        }
    }

    public enum OnboardingStatus {
        PENDING_PASSWORD,
        PENDING_MFA,
        COMPLETE
    }
}
```

### 9.8 Event Flow Summary

| Step | Trigger | Publisher | Event | Consumer | Action |
|------|---------|-----------|-------|----------|--------|
| 1 | User provisioned via knight2 | knight2 | `Auth0UserCreated` | (internal) | User created in Auth0 |
| 2 | okta-app detects password set | okta-app | `USER_PASSWORD_SET` | knight2 | Update status to `PENDING_MFA` |
| 3 | okta-app detects MFA enrollment | okta-app | `USER_MFA_ENROLLED` | knight2 | Update status to `COMPLETE` |
| 4 | Both steps confirmed | okta-app | `USER_ONBOARDING_COMPLETE` | knight2 | Mark fully onboarded |

---

## API Endpoints Summary

| Operation | Method | Endpoint |
|-----------|--------|----------|
| Get token | POST | `https://{domain}/oauth/token` |
| Create user | POST | `https://{domain}/api/v2/users` |
| Get user by ID | GET | `https://{domain}/api/v2/users/{id}` |
| Get user by email | GET | `https://{domain}/api/v2/users-by-email?email={email}` |
| Update user | PATCH | `https://{domain}/api/v2/users/{id}` |
| Delete user | DELETE | `https://{domain}/api/v2/users/{id}` |
| Create password ticket | POST | `https://{domain}/api/v2/tickets/password-change` |
| Get MFA enrollments | GET | `https://{domain}/api/v2/users/{id}/enrollments` |
