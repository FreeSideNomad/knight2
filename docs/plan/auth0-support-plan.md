# Knight Platform: Auth0 JWT Support Plan

## Executive Summary

This document outlines the plan to add **Auth0** as a third JWT issuer for the Platform API, alongside existing Entra ID (Azure AD) and Portal JWT support. Auth0 will authenticate **indirect client users** who access the API through external applications.

### Current State
- **Multi-issuer JWT support**: Entra ID + Portal JWT via `MultiIssuerJwtDecoder`
- **User mapping**: `user.identity_provider_user_id` maps to JWT `sub` claim
- **Identity providers**: AUTH0 and ANP defined in `User.IdentityProvider` enum

### Target State
- **Three JWT issuers**: Entra ID, Portal JWT, and Auth0
- **Request-scoped context**: `Auth0UserContext` loaded per API request for Auth0 users
- **Environment-based configuration**: Auth0 tenant configured via `.env`

---

## Auth0 JWT Token Structure

### Sample Token Claims
```json
{
  "iss": "https://dbc-test.auth0.com/",
  "sub": "auth0|694aa9789daa0cb005839f68",
  "aud": [
    "https://auth-gateway.local/api",
    "https://dbc-test.auth0.com/userinfo"
  ],
  "iat": 1766514007,
  "exp": 1766542807,
  "scope": "openid profile email",
  "gty": "password",
  "azp": "LQUbLUmBJ2qPh0tZF72SemAdkzkAlhyO"
}
```

### Key Claims Mapping
| Claim | Description | Usage |
|-------|-------------|-------|
| `iss` | Issuer URL | Route to Auth0 decoder |
| `sub` | User identifier | Maps to `user.identity_provider_user_id` |
| `aud` | Audience(s) | Validate API is intended recipient |
| `azp` | Authorized party (client ID) | Identify calling application |
| `scope` | OAuth scopes | Determine permissions |

---

## Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                           Platform API                                 │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    MultiIssuerJwtDecoder                        │   │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐                    │   │
│  │  │ Entra ID  │  │  Portal   │  │   Auth0   │  ← NEW             │   │
│  │  │  Decoder  │  │  Decoder  │  │  Decoder  │                    │   │
│  │  └───────────┘  └───────────┘  └───────────┘                    │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                         │
│                              ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   Auth0UserContextFilter                        │   │
│  │   - Detects Auth0 issuer in JWT                                 │   │
│  │   - Extracts JWT claims (sub, iss, scope, azp)                  │   │
│  │   - Loads User by identity_provider_user_id                     │   │
│  │   - Populates request-scoped Auth0UserContext                   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                         │
│                              ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                   Auth0UserContext (Request-Scoped)             │   │
│  │   - JWT Claims: subject, issuer, scopes, authorizedParty        │   │
│  │   - user: User aggregate (loaded by identity_provider_user_id)  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                              │                                         │
│                              ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      REST Controllers                           │   │
│  │   - Inject Auth0UserContext where needed                        │   │
│  │   - Access JWT claims and user information                      │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Implementation Steps

### Phase 1: Configuration Updates

---

#### Step 1: Update JwtProperties for Auth0

**File**: `/application/src/main/java/com/knight/application/config/JwtProperties.java`

**Add Auth0 configuration class**:
```java
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private boolean enabled = true;
    private EntraConfig entra = new EntraConfig();
    private PortalConfig portal = new PortalConfig();
    private Auth0Config auth0 = new Auth0Config();  // NEW

    @Data
    public static class Auth0Config {
        private boolean enabled = true;
        private String domain;           // e.g., "dbc-test.auth0.com"
        private String audience;         // e.g., "https://auth-gateway.local/api"
        private String clientId;         // For validation (optional)

        public String getIssuerUri() {
            return "https://" + domain + "/";
        }

        public String getJwksUri() {
            return "https://" + domain + "/.well-known/jwks.json";
        }
    }
}
```

---

#### Step 2: Update Application Configuration

**File**: `/application/src/main/resources/application.yml`

**Add Auth0 section**:
```yaml
jwt:
  enabled: ${JWT_ENABLED:true}

  entra:
    enabled: ${JWT_ENTRA_ENABLED:true}
    tenant-id: ${ENTRA_TENANT_ID:}
    client-id: ${ENTRA_CLIENT_ID:}

  portal:
    enabled: ${JWT_PORTAL_ENABLED:true}
    issuer: ${JWT_PORTAL_ISSUER:http://localhost:8081}
    jwks-uri: ${JWT_PORTAL_JWKS_URI:http://localhost:8081/.well-known/jwks.json}
    audience: knight-platform-api

  # NEW: Auth0 configuration
  auth0:
    enabled: ${JWT_AUTH0_ENABLED:true}
    domain: ${AUTH0_DOMAIN:dbc-test.auth0.com}
    audience: ${AUTH0_AUDIENCE:https://auth-gateway.local/api}
    client-id: ${AUTH0_CLIENT_ID:}  # Optional, for azp validation
```

**File**: `/.env` (or docker-compose environment)

```bash
# Auth0 Configuration
JWT_AUTH0_ENABLED=true
AUTH0_DOMAIN=dbc-test.auth0.com
AUTH0_AUDIENCE=https://auth-gateway.local/api
AUTH0_CLIENT_ID=LQUbLUmBJ2qPh0tZF72SemAdkzkAlhyO
```

---

### Phase 2: JWT Decoder Updates

---

#### Step 3: Update MultiIssuerJwtDecoder

**File**: `/application/src/main/java/com/knight/application/security/MultiIssuerJwtDecoder.java`

**Add Auth0 decoder registration**:
```java
@Slf4j
public class MultiIssuerJwtDecoder implements JwtDecoder {

    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    public MultiIssuerJwtDecoder(JwtProperties jwtProperties) {
        // Existing: Register Entra ID decoder
        if (jwtProperties.getEntra().isEnabled()) {
            registerEntraDecoder(jwtProperties.getEntra());
        }

        // Existing: Register Portal decoder
        if (jwtProperties.getPortal().isEnabled()) {
            registerPortalDecoder(jwtProperties.getPortal());
        }

        // NEW: Register Auth0 decoder
        if (jwtProperties.getAuth0().isEnabled()) {
            registerAuth0Decoder(jwtProperties.getAuth0());
        }
    }

    private void registerAuth0Decoder(JwtProperties.Auth0Config config) {
        String issuerUri = config.getIssuerUri();

        NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri(config.getJwksUri())
            .build();

        // Configure validator for Auth0
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(config.getAudience());
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> combinedValidator = new DelegatingOAuth2TokenValidator<>(
            issuerValidator,
            audienceValidator
        );

        decoder.setJwtValidator(combinedValidator);

        decoders.put(issuerUri, decoder);
        log.info("Registered Auth0 JWT decoder for issuer: {}", issuerUri);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String issuer = extractIssuer(token);

        JwtDecoder decoder = decoders.get(issuer);
        if (decoder == null) {
            throw new JwtException("Unknown JWT issuer: " + issuer);
        }

        return decoder.decode(token);
    }

    private String extractIssuer(String token) {
        // Parse JWT without validation to extract issuer
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtException("Invalid JWT format");
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        // Parse JSON and extract "iss" claim
        // ... implementation
    }
}
```

---

#### Step 4: Create AudienceValidator

**File**: `/application/src/main/java/com/knight/application/security/AudienceValidator.java`

```java
package com.knight.application.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates that the JWT audience claim contains the expected audience.
 * Auth0 tokens have multiple audiences in an array.
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedAudience;

    public AudienceValidator(String expectedAudience) {
        this.expectedAudience = expectedAudience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audiences = jwt.getAudience();

        if (audiences != null && audiences.contains(expectedAudience)) {
            return OAuth2TokenValidatorResult.success();
        }

        OAuth2Error error = new OAuth2Error(
            "invalid_audience",
            "The required audience " + expectedAudience + " is missing",
            null
        );
        return OAuth2TokenValidatorResult.failure(error);
    }
}
```

---

### Phase 3: Request-Scoped User Context

---

#### Step 5: Create Auth0UserContext

**File**: `/application/src/main/java/com/knight/application/security/auth0/Auth0UserContext.java`

```java
package com.knight.application.security.auth0;

import com.knight.domain.users.aggregate.User;
import lombok.Getter;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Request-scoped context holder for Auth0 authenticated users.
 * Contains JWT claims and the loaded User entity.
 *
 * IMPORTANT: This data is valid only for the current HTTP request.
 * Do NOT cache or reuse across requests.
 */
@Component
@RequestScope
@Getter
public class Auth0UserContext {

    private boolean initialized = false;
    private boolean auth0Request = false;

    // JWT Claims
    private String subject;           // auth0|694aa9789daa0cb005839f68
    private String issuer;            // https://dbc-test.auth0.com/
    private List<String> scopes;      // openid, profile, email
    private String authorizedParty;   // Client ID (azp)

    // Loaded User (null if not found or not Auth0 request)
    private User user;

    /**
     * Initialize context with JWT claims and loaded user.
     * Called by Auth0UserContextFilter.
     */
    public void initialize(
            String subject,
            String issuer,
            List<String> scopes,
            String authorizedParty,
            User user
    ) {
        this.subject = subject;
        this.issuer = issuer;
        this.scopes = scopes;
        this.authorizedParty = authorizedParty;
        this.user = user;
        this.auth0Request = true;
        this.initialized = true;
    }

    /**
     * Check if this is an Auth0 authenticated request.
     */
    public boolean isAuth0Request() {
        return auth0Request;
    }

    /**
     * Get user if available.
     */
    public Optional<User> getUser() {
        return Optional.ofNullable(user);
    }

    /**
     * Get user's email from loaded user entity.
     */
    public Optional<String> getUserEmail() {
        return getUser().map(User::email);
    }

    /**
     * Get user's profile ID.
     */
    public Optional<String> getProfileId() {
        return getUser().map(u -> u.profileId().urn());
    }
}
```

---

#### Step 6: Create Auth0UserContextFilter

**File**: `/application/src/main/java/com/knight/application/security/auth0/Auth0UserContextFilter.java`

```java
package com.knight.application.security.auth0;

import com.knight.application.config.JwtProperties;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Filter that populates Auth0UserContext for Auth0 authenticated requests.
 * Runs after JWT authentication but before controller execution.
 *
 * Loads:
 * - JWT claims (sub, iss, scope, azp)
 * - User by identity_provider_user_id (JWT sub claim)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(10)  // Run after Spring Security filters
public class Auth0UserContextFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;
    private final Auth0UserContext auth0UserContext;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            populateAuth0Context();
        } catch (Exception e) {
            log.warn("Failed to populate Auth0 user context: {}", e.getMessage());
            // Continue processing - context will be empty but request continues
        }

        filterChain.doFilter(request, response);
    }

    private void populateAuth0Context() {
        if (!jwtProperties.getAuth0().isEnabled()) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        String issuer = jwt.getIssuer().toString();

        // Check if this is an Auth0 token
        String auth0Issuer = jwtProperties.getAuth0().getIssuerUri();
        if (!issuer.equals(auth0Issuer)) {
            return;
        }

        log.debug("Processing Auth0 authenticated request for subject: {}", jwt.getSubject());

        // Extract claims
        String subject = jwt.getSubject();  // auth0|694aa9789daa0cb005839f68
        String scopeString = jwt.getClaimAsString("scope");
        List<String> scopes = scopeString != null
            ? Arrays.asList(scopeString.split(" "))
            : Collections.emptyList();
        String authorizedParty = jwt.getClaimAsString("azp");

        // Load user by identity_provider_user_id
        Optional<User> userOpt = userRepository.findByIdentityProviderUserId(subject);

        if (userOpt.isEmpty()) {
            log.warn("No user found for Auth0 subject: {}", subject);
            auth0UserContext.initialize(subject, issuer, scopes, authorizedParty, null);
            return;
        }

        User user = userOpt.get();
        log.debug("Found user: {} for Auth0 subject", user.email());

        // Initialize context with JWT claims and user
        auth0UserContext.initialize(subject, issuer, scopes, authorizedParty, user);

        log.debug("Auth0 user context initialized - User: {}, Email: {}",
            user.userId().urn(),
            user.email()
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip for actuator, health checks, etc.
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }
}
```

---

#### Step 7: Add Repository Method

**File**: `/application/src/main/java/com/knight/application/persistence/users/repository/UserJpaRepository.java`

**Add method to find user by identity provider user ID**:
```java
@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    // NEW: Find user by Auth0 subject (identity_provider_user_id)
    Optional<UserEntity> findByIdentityProviderUserId(String identityProviderUserId);

    // ... existing methods
}
```

**Update Domain Repository Interface**:
```java
public interface UserRepository {
    Optional<User> findById(UserId userId);
    Optional<User> findByEmail(String email);

    // NEW
    Optional<User> findByIdentityProviderUserId(String identityProviderUserId);

    User save(User user);
}
```

---

### Phase 4: Controller Integration

---

#### Step 8: Example Controller Usage

**File**: `/application/src/main/java/com/knight/application/rest/example/Auth0ProtectedController.java`

```java
package com.knight.application.rest.example;

import com.knight.application.security.auth0.Auth0UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Example controller showing how to use Auth0UserContext.
 */
@RestController
@RequestMapping("/api/v1/my")
@RequiredArgsConstructor
public class Auth0ProtectedController {

    private final Auth0UserContext auth0UserContext;

    @GetMapping("/info")
    public ResponseEntity<?> getMyInfo() {
        if (!auth0UserContext.isAuth0Request()) {
            return ResponseEntity.status(403).body("Auth0 authentication required");
        }

        // Return JWT claims and user info
        return auth0UserContext.getUser()
            .map(user -> ResponseEntity.ok(Map.of(
                "subject", auth0UserContext.getSubject(),
                "issuer", auth0UserContext.getIssuer(),
                "scopes", auth0UserContext.getScopes(),
                "authorizedParty", auth0UserContext.getAuthorizedParty(),
                "userId", user.userId().urn(),
                "email", user.email(),
                "profileId", user.profileId().urn(),
                "userType", user.userType().name(),
                "status", user.status().name()
            )))
            .orElse(ResponseEntity.ok(Map.of(
                "subject", auth0UserContext.getSubject(),
                "issuer", auth0UserContext.getIssuer(),
                "scopes", auth0UserContext.getScopes(),
                "authorizedParty", auth0UserContext.getAuthorizedParty(),
                "user", "not found"
            )));
    }
}
```

---

### Phase 5: Testing

---

#### Step 9: Create Integration Test

**File**: `/application/src/test/java/com/knight/application/security/auth0/Auth0AuthenticationTest.java`

```java
package com.knight.application.security.auth0;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Auth0AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/my/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptValidAuth0Token() throws Exception {
        // Use test JWT signed with test key
        String testToken = generateTestAuth0Token();

        mockMvc.perform(get("/api/v1/my/profile")
                .header("Authorization", "Bearer " + testToken))
            .andExpect(status().isOk());
    }

    private String generateTestAuth0Token() {
        // Generate test token for integration tests
        // ...
    }
}
```

---

## Complete File List

### New Files
| File | Description |
|------|-------------|
| `Auth0UserContext.java` | Request-scoped context holder |
| `Auth0UserContextFilter.java` | Filter to populate context |
| `AudienceValidator.java` | JWT audience validator |

### Modified Files
| File | Change |
|------|--------|
| `JwtProperties.java` | Add Auth0Config inner class |
| `application.yml` | Add auth0 configuration section |
| `MultiIssuerJwtDecoder.java` | Register Auth0 decoder |
| `UserJpaRepository.java` | Add findByIdentityProviderUserId |
| `UserRepository.java` | Add findByIdentityProviderUserId |
| `.env` | Add AUTH0_* variables |

---

## Configuration Summary

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `JWT_AUTH0_ENABLED` | Enable/disable Auth0 | `true` |
| `AUTH0_DOMAIN` | Auth0 tenant domain | `dbc-test.auth0.com` |
| `AUTH0_AUDIENCE` | Expected API audience | `https://auth-gateway.local/api` |
| `AUTH0_CLIENT_ID` | Authorized client ID | `LQUbLUmBJ2qPh0tZF72SemAdkzkAlhyO` |

### JWKS Endpoint
Auth0 publishes public keys at:
```
https://dbc-test.auth0.com/.well-known/jwks.json
```

---

## Security Considerations

1. **Token Validation**
   - Signature verified via Auth0 JWKS
   - Issuer validated against configured domain
   - Audience validated to ensure token is for this API
   - Expiration checked automatically

2. **Request Isolation**
   - `Auth0UserContext` is request-scoped
   - New instance created for each request
   - No data sharing between requests
   - Garbage collected after request completes

3. **User Mapping**
   - `sub` claim maps to `identity_provider_user_id`
   - User must exist in database before API access
   - User provisioning handled separately (Auth0 Actions or batch sync)

4. **Authorization**
   - Context provides account/service access checks
   - Controllers must explicitly verify access
   - No implicit authorization from authentication alone

---

## Sequence Diagram

```
┌──────┐     ┌─────────┐     ┌─────────────────┐     ┌─────────────┐     ┌──────────┐
│Client│     │API GW   │     │MultiIssuerDecoder│     │Auth0Filter  │     │Controller│
└──┬───┘     └────┬────┘     └────────┬────────┘     └──────┬──────┘     └────┬─────┘
   │              │                   │                     │                 │
   │ Request +    │                   │                     │                 │
   │ Auth0 JWT    │                   │                     │                 │
   │─────────────>│                   │                     │                 │
   │              │                   │                     │                 │
   │              │  Validate JWT     │                     │                 │
   │              │──────────────────>│                     │                 │
   │              │                   │                     │                 │
   │              │                   │ Fetch JWKS from     │                 │
   │              │                   │ Auth0               │                 │
   │              │                   │─────────────────────│                 │
   │              │                   │                     │                 │
   │              │  Authenticated    │                     │                 │
   │              │<──────────────────│                     │                 │
   │              │                   │                     │                 │
   │              │                   │  Populate Context   │                 │
   │              │                   │────────────────────>│                 │
   │              │                   │                     │                 │
   │              │                   │                     │ Load User by    │
   │              │                   │                     │ identity_       │
   │              │                   │                     │ provider_       │
   │              │                   │                     │ user_id         │
   │              │                   │                     │─────────────────│
   │              │                   │                     │                 │
   │              │                   │                     │  Handle Request │
   │              │                   │                     │────────────────>│
   │              │                   │                     │                 │
   │              │                   │                     │  Use Context    │
   │              │                   │                     │<────────────────│
   │              │                   │                     │                 │
   │  Response    │                   │                     │                 │
   │<─────────────│                   │                     │                 │
   │              │                   │                     │                 │
```

---

## Phase 6: Controller Refactoring - BFF Architecture

This phase reorganizes REST controllers into three BFF (Backend For Frontend) specific controllers, each serving a different UI application with appropriate authentication requirements.

### Target Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Platform API - Three BFFs                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐
│  │   Bank Admin BFF     │  │    Client BFF        │  │  Indirect Client BFF │
│  │   /api/v1/bank/*     │  │   /api/v1/client/*   │  │  /api/v1/indirect/*  │
│  ├──────────────────────┤  ├──────────────────────┤  ├──────────────────────┤
│  │ Auth: Entra ID/LDAP  │  │ Auth: Auth0 (future: │  │ Auth: Auth0          │
│  │                      │  │       ANP)           │  │                      │
│  │ Users: Bank Staff    │  │ Users: Direct Client │  │ Users: Indirect      │
│  │                      │  │        Staff         │  │        Client Staff  │
│  └──────────────────────┘  └──────────────────────┘  └──────────────────────┘
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Authentication Requirements

| Controller | Token Issuers | Rejection Rule |
|-----------|---------------|----------------|
| `BankAdminController` | Entra ID, Portal JWT | Reject Auth0 tokens |
| `ClientController` | Auth0 (future: ANP) | Reject Entra ID/Portal tokens |
| `IndirectClientController` | Auth0 | Reject Entra ID/Portal tokens |

---

### Current Endpoints Inventory

#### From ClientController (`/api/clients`)
| Method | Endpoint | Target Controller |
|--------|----------|-------------------|
| GET | `/api/clients` | BankAdminController |
| GET | `/api/clients/{clientId}` | BankAdminController |
| GET | `/api/clients/{clientId}/accounts` | BankAdminController |

#### From ProfileController (`/api/profiles`, `/api/clients/{clientId}/profiles`)
| Method | Endpoint | Target Controller |
|--------|----------|-------------------|
| POST | `/api/profiles` | BankAdminController |
| GET | `/api/profiles/{profileId}` | BankAdminController |
| GET | `/api/profiles/{profileId}/detail` | BankAdminController |
| POST | `/api/profiles/search` | BankAdminController |
| POST | `/api/profiles/{profileId}/services` | BankAdminController |
| POST | `/api/profiles/{profileId}/clients` | BankAdminController |
| DELETE | `/api/profiles/{profileId}/clients/{clientId}` | BankAdminController |
| GET | `/api/clients/{clientId}/profiles` | BankAdminController |
| GET | `/api/clients/{clientId}/profiles/primary` | BankAdminController |
| GET | `/api/clients/{clientId}/profiles/secondary` | BankAdminController |
| GET | `/api/clients/{clientId}/profiles/servicing` | BankAdminController |
| GET | `/api/clients/{clientId}/profiles/online` | BankAdminController |

#### From IndirectProfileController (`/api/indirect-profiles`)
| Method | Endpoint | Target Controller |
|--------|----------|-------------------|
| GET | `/api/indirect-profiles/parent-clients` | BankAdminController |
| GET | `/api/indirect-profiles` | BankAdminController |
| GET | `/api/indirect-profiles/all` | BankAdminController |

#### From PermissionPolicyController (`/api/profiles/{profileId}/permission-policies`)
| Method | Endpoint | Target Controller |
|--------|----------|-------------------|
| GET | `/api/profiles/{profileId}/permission-policies` | BankAdmin, Client*, Indirect* |
| GET | `/api/profiles/{profileId}/permission-policies/{policyId}` | BankAdmin, Client*, Indirect* |
| POST | `/api/profiles/{profileId}/permission-policies` | BankAdminController |
| PUT | `/api/profiles/{profileId}/permission-policies/{policyId}` | BankAdminController |
| DELETE | `/api/profiles/{profileId}/permission-policies/{policyId}` | BankAdminController |
| POST | `/api/profiles/{profileId}/authorize` | BankAdminController |
| GET | `/api/profiles/{profileId}/users/{userId}/permissions` | BankAdminController |

*Note: Client and Indirect get read-only access with profileId derived from JWT

#### From PayorEnrolmentController (`/api/profiles/{profileId}/payor-enrolment`)
| Method | Endpoint | Target Controller |
|--------|----------|-------------------|
| POST | `/api/profiles/{profileId}/payor-enrolment/validate` | ClientController (onboard indirect clients) |
| POST | `/api/profiles/{profileId}/payor-enrolment/execute` | ClientController |
| GET | `/api/profiles/{profileId}/payor-enrolment/batches` | ClientController |

#### From BatchController (`/api/batches`)
| Method | Endpoint | Target Controller |
|--------|----------|-------------------|
| GET | `/api/batches/{batchId}` | BankAdmin, ClientController |
| GET | `/api/batches/{batchId}/items` | BankAdmin, ClientController |

#### From ProfileUsersController (`/api/profiles/{profileId}/users`, `/api/users`)
| Method | Endpoint | Target Controller |
|--------|----------|-------------------|
| GET | `/api/profiles/{profileId}/users` | BankAdmin, Client*, Indirect* |
| POST | `/api/profiles/{profileId}/users` | BankAdmin, ClientController |
| GET | `/api/profiles/{profileId}/users/{userId}` | BankAdmin, Client*, Indirect* |
| GET | `/api/profiles/{profileId}/users/counts` | BankAdmin, Client*, Indirect* |
| POST | `/api/users/{userId}/resend-invitation` | BankAdmin, ClientController |
| PUT | `/api/users/{userId}/lock` | BankAdmin, ClientController |
| PUT | `/api/users/{userId}/unlock` | BankAdmin, ClientController |
| PUT | `/api/users/{userId}/deactivate` | BankAdmin, ClientController |
| PUT | `/api/users/{userId}/activate` | BankAdmin, ClientController |
| PUT | `/api/users/{userId}` | BankAdmin, Client*, Indirect* |
| POST | `/api/users/{userId}/roles` | BankAdmin, ClientController |
| DELETE | `/api/users/{userId}/roles/{role}` | BankAdmin, ClientController |

*Note: Client and Indirect can only manage users/self within their own profile (derived from JWT)

#### From IndirectClientController (`/api/indirect-clients`)
| Method | Endpoint | Target Controller |
|--------|----------|-------------------|
| GET | `/api/indirect-clients/by-client/{clientId}` | BankAdminController |
| GET | `/api/indirect-clients/by-profile` | BankAdmin, ClientController* |
| GET | `/api/indirect-clients/{id}` | BankAdmin, Client*, Indirect* |
| POST | `/api/indirect-clients` | BankAdmin, ClientController |
| POST | `/api/indirect-clients/{id}/persons` | BankAdmin, Client*, Indirect* |
| PUT | `/api/indirect-clients/{id}/persons/{personId}` | BankAdmin, Client*, Indirect* |
| DELETE | `/api/indirect-clients/{id}/persons/{personId}` | BankAdmin, Client*, Indirect* |
| POST | `/api/indirect-clients/{id}/accounts` | IndirectClientController only |
| GET | `/api/indirect-clients/{id}/accounts` | BankAdmin, Client*, Indirect* |
| DELETE | `/api/indirect-clients/{id}/accounts/{accountId}` | IndirectClientController only |

*Note: Client can manage indirect clients under their profile. Indirect can only manage their own client.

---

### New Controller Structure

---

#### Step 10: Create BankAdminController

**Package**: `com.knight.application.rest.bank`

**File**: `/application/src/main/java/com/knight/application/rest/bank/BankAdminController.java`

```java
package com.knight.application.rest.bank;

import com.knight.application.security.IssuerValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * BFF Controller for Bank Admin UI (Employee Portal).
 *
 * Authentication: Entra ID (Azure AD) or Portal JWT only.
 * Users: Bank employees managing clients, profiles, and system configuration.
 *
 * Rejects Auth0 tokens - those are for client/indirect users only.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bank")
@RequiredArgsConstructor
public class BankAdminController {

    private final IssuerValidator issuerValidator;

    // Inject existing services
    private final ClientRepository clientRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final ProfileCommands profileCommands;
    private final ProfileQueries profileQueries;
    private final IndirectClientRepository indirectClientRepository;
    private final UserCommands userCommands;
    private final UserQueries userQueries;
    private final PermissionPolicyCommands policyCommands;
    private final PermissionPolicyQueries policyQueries;
    private final PayorEnrolmentService payorEnrolmentService;

    // ==================== Client Endpoints ====================

    @GetMapping("/clients")
    public ResponseEntity<?> searchClients(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ClientController.searchClients logic
    }

    @GetMapping("/clients/{clientId}")
    public ResponseEntity<?> getClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String clientId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ClientController.getClient logic
    }

    @GetMapping("/clients/{clientId}/accounts")
    public ResponseEntity<?> getClientAccounts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ClientController.getClientAccounts logic
    }

    // ==================== Profile Endpoints ====================

    @PostMapping("/profiles")
    public ResponseEntity<?> createProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateProfileRequest request) {

        issuerValidator.rejectAuth0(jwt);
        String createdBy = jwt.getSubject();
        // ... existing ProfileController.createProfile logic
    }

    @GetMapping("/profiles/{profileId}")
    public ResponseEntity<?> getProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileController.getProfile logic
    }

    @GetMapping("/profiles/{profileId}/detail")
    public ResponseEntity<?> getProfileDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileController.getProfileDetail logic
    }

    @PostMapping("/profiles/search")
    public ResponseEntity<?> searchProfiles(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ProfileSearchRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileController.searchProfiles logic
    }

    @PostMapping("/profiles/{profileId}/services")
    public ResponseEntity<?> enrollService(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @RequestBody EnrollServiceRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileController.enrollService logic
    }

    @PostMapping("/profiles/{profileId}/clients")
    public ResponseEntity<?> addSecondaryClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @RequestBody AddSecondaryClientRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileController.addSecondaryClient logic
    }

    @DeleteMapping("/profiles/{profileId}/clients/{clientId}")
    public ResponseEntity<?> removeSecondaryClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @PathVariable String clientId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileController.removeSecondaryClient logic
    }

    // ==================== Client-Profile Relationships ====================

    @GetMapping("/clients/{clientId}/profiles")
    public ResponseEntity<?> getClientProfiles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String clientId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileController.getClientProfiles logic
    }

    @GetMapping("/clients/{clientId}/profiles/primary")
    public ResponseEntity<?> getPrimaryProfiles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String clientId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileController.getPrimaryProfiles logic
    }

    @GetMapping("/clients/{clientId}/profiles/secondary")
    public ResponseEntity<?> getSecondaryProfiles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String clientId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileController.getSecondaryProfiles logic
    }

    // ==================== Indirect Profile Endpoints ====================

    @GetMapping("/indirect-profiles/parent-clients")
    public ResponseEntity<?> getIndirectProfileParentClients(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectProfileController.getParentClients logic
    }

    @GetMapping("/indirect-profiles")
    public ResponseEntity<?> searchIndirectProfiles(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String parentClientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectProfileController.searchIndirectProfiles logic
    }

    // ==================== Indirect Client Endpoints ====================

    @GetMapping("/indirect-clients/by-client/{clientId}")
    public ResponseEntity<?> getIndirectClientsByClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String clientId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectClientController.getByClient logic
    }

    @GetMapping("/indirect-clients/by-profile")
    public ResponseEntity<?> getIndirectClientsByProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String parentProfileId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectClientController.getByProfile logic
    }

    @GetMapping("/indirect-clients/{id}")
    public ResponseEntity<?> getIndirectClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectClientController.getById logic
    }

    @PostMapping("/indirect-clients")
    public ResponseEntity<?> createIndirectClient(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateIndirectClientRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectClientController.create logic
    }

    @PostMapping("/indirect-clients/{id}/persons")
    public ResponseEntity<?> addRelatedPerson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestBody RelatedPersonRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectClientController.addRelatedPerson logic
    }

    @PutMapping("/indirect-clients/{id}/persons/{personId}")
    public ResponseEntity<?> updateRelatedPerson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @PathVariable String personId,
            @RequestBody RelatedPersonRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectClientController.updateRelatedPerson logic
    }

    @DeleteMapping("/indirect-clients/{id}/persons/{personId}")
    public ResponseEntity<?> removeRelatedPerson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @PathVariable String personId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectClientController.removeRelatedPerson logic
    }

    @GetMapping("/indirect-clients/{id}/accounts")
    public ResponseEntity<?> getIndirectClientAccounts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing IndirectClientController.getOfiAccounts logic
    }

    // ==================== User Management Endpoints ====================

    @GetMapping("/profiles/{profileId}/users")
    public ResponseEntity<?> listProfileUsers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.listProfileUsers logic
    }

    @PostMapping("/profiles/{profileId}/users")
    public ResponseEntity<?> addUserToProfile(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @RequestBody AddUserRequest request) {

        issuerValidator.rejectAuth0(jwt);
        String createdBy = jwt.getSubject();
        // ... existing ProfileUsersController.addUserToProfile logic
    }

    @GetMapping("/profiles/{profileId}/users/{userId}")
    public ResponseEntity<?> getUserDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @PathVariable String userId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.getUserDetail logic
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody UpdateUserRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.updateUser logic
    }

    @PostMapping("/users/{userId}/resend-invitation")
    public ResponseEntity<?> resendInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.resendInvitation logic
    }

    @PutMapping("/users/{userId}/lock")
    public ResponseEntity<?> lockUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody LockUserRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.lockUser logic
    }

    @PutMapping("/users/{userId}/unlock")
    public ResponseEntity<?> unlockUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.unlockUser logic
    }

    @PutMapping("/users/{userId}/deactivate")
    public ResponseEntity<?> deactivateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody DeactivateUserRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.deactivateUser logic
    }

    @PutMapping("/users/{userId}/activate")
    public ResponseEntity<?> activateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.activateUser logic
    }

    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<?> addRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody RoleRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.addRole logic
    }

    @DeleteMapping("/users/{userId}/roles/{role}")
    public ResponseEntity<?> removeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @PathVariable String role) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing ProfileUsersController.removeRole logic
    }

    // ==================== Permission Policy Endpoints ====================

    @GetMapping("/profiles/{profileId}/permission-policies")
    public ResponseEntity<?> listPolicies(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing PermissionPolicyController.listPolicies logic
    }

    @GetMapping("/profiles/{profileId}/permission-policies/{policyId}")
    public ResponseEntity<?> getPolicy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @PathVariable String policyId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing PermissionPolicyController.getPolicy logic
    }

    @PostMapping("/profiles/{profileId}/permission-policies")
    public ResponseEntity<?> createPolicy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @RequestBody CreatePermissionPolicyRequest request) {

        issuerValidator.rejectAuth0(jwt);
        String createdBy = jwt.getSubject();
        // ... existing PermissionPolicyController.createPolicy logic
    }

    @PutMapping("/profiles/{profileId}/permission-policies/{policyId}")
    public ResponseEntity<?> updatePolicy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @PathVariable String policyId,
            @RequestBody UpdatePermissionPolicyRequest request) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing PermissionPolicyController.updatePolicy logic
    }

    @DeleteMapping("/profiles/{profileId}/permission-policies/{policyId}")
    public ResponseEntity<?> deletePolicy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String profileId,
            @PathVariable String policyId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing PermissionPolicyController.deletePolicy logic
    }

    // ==================== Batch Endpoints ====================

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<?> getBatch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String batchId) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing BatchController.getBatch logic
    }

    @GetMapping("/batches/{batchId}/items")
    public ResponseEntity<?> getBatchItems(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String batchId,
            @RequestParam(required = false) String status) {

        issuerValidator.rejectAuth0(jwt);
        // ... existing BatchController.getBatchItems logic
    }
}
```

---

#### Step 11: Create ClientController (Direct Client BFF)

**Package**: `com.knight.application.rest.client`

**File**: `/application/src/main/java/com/knight/application/rest/client/ClientController.java`

```java
package com.knight.application.rest.client;

import com.knight.application.security.IssuerValidator;
import com.knight.application.security.auth0.Auth0UserContext;
import com.knight.platform.sharedkernel.ProfileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * BFF Controller for Direct Client UI.
 *
 * Authentication: Auth0 (future: ANP).
 * Users: Direct client staff managing their indirect clients and users.
 *
 * Key behaviors:
 * - ProfileId is ALWAYS derived from JWT (never passed as parameter)
 * - Can onboard and manage indirect clients under their profile
 * - Can manage users within their profile
 * - Cannot manage their own OFI accounts (only indirect clients can)
 *
 * Rejects Entra ID and Portal JWT tokens.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/client")
@RequiredArgsConstructor
public class ClientController {

    private final IssuerValidator issuerValidator;
    private final Auth0UserContext auth0UserContext;

    // Inject existing services
    private final IndirectClientRepository indirectClientRepository;
    private final UserCommands userCommands;
    private final UserQueries userQueries;
    private final PermissionPolicyQueries policyQueries;
    private final PayorEnrolmentService payorEnrolmentService;

    // ==================== Helper Methods ====================

    private ProfileId getProfileIdFromContext() {
        return auth0UserContext.getUser()
            .map(user -> user.profileId())
            .orElseThrow(() -> new UnauthorizedException("User not found for Auth0 subject"));
    }

    // ==================== Indirect Client Management ====================

    /**
     * Get all indirect clients under this client's profile.
     * ProfileId derived from JWT.
     */
    @GetMapping("/indirect-clients")
    public ResponseEntity<?> getMyIndirectClients(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.requireAuth0(jwt);

        ProfileId profileId = getProfileIdFromContext();
        List<IndirectClient> clients = indirectClientRepository.findByParentProfileId(profileId);

        return ResponseEntity.ok(clients.stream().map(this::toDto).toList());
    }

    /**
     * Get specific indirect client (must belong to this profile).
     */
    @GetMapping("/indirect-clients/{id}")
    public ResponseEntity<?> getIndirectClient(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        return indirectClientRepository.findById(IndirectClientId.fromUrn(id))
            .filter(client -> client.parentProfileId().equals(profileId))
            .map(this::toDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new indirect client (under this profile).
     * Client can onboard indirect clients but NOT create their OFI accounts.
     */
    @PostMapping("/indirect-clients")
    public ResponseEntity<?> createIndirectClient(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateIndirectClientRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Force parentProfileId to current user's profile
        // ... create logic with profileId override
    }

    /**
     * Update indirect client business name (limited).
     */
    @PutMapping("/indirect-clients/{id}/name")
    public ResponseEntity<?> updateIndirectClientName(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestBody UpdateNameRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify ownership and update name only
    }

    /**
     * Add related person to indirect client.
     */
    @PostMapping("/indirect-clients/{id}/persons")
    public ResponseEntity<?> addRelatedPerson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @RequestBody RelatedPersonRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify ownership then add person
    }

    /**
     * Update related person on indirect client.
     */
    @PutMapping("/indirect-clients/{id}/persons/{personId}")
    public ResponseEntity<?> updateRelatedPerson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @PathVariable String personId,
            @RequestBody RelatedPersonRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify ownership then update person
    }

    /**
     * Remove related person from indirect client.
     */
    @DeleteMapping("/indirect-clients/{id}/persons/{personId}")
    public ResponseEntity<?> removeRelatedPerson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id,
            @PathVariable String personId) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify ownership then remove person
    }

    /**
     * Get OFI accounts for indirect client (read-only for direct clients).
     * Direct clients can VIEW but NOT modify OFI accounts.
     */
    @GetMapping("/indirect-clients/{id}/accounts")
    public ResponseEntity<?> getIndirectClientAccounts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String id) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify ownership then return accounts (read-only)
    }

    // ==================== Payor Enrolment (Batch Onboarding) ====================

    /**
     * Validate payor enrolment batch file.
     * ProfileId derived from JWT.
     */
    @PostMapping(value = "/payor-enrolment/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> validatePayorEnrolment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();
        String requestedBy = auth0UserContext.getUserEmail().orElse("system");

        // ... existing PayorEnrolmentController.validate logic with profileId from context
    }

    /**
     * Execute payor enrolment batch.
     * ProfileId derived from JWT.
     */
    @PostMapping("/payor-enrolment/execute")
    public ResponseEntity<?> executePayorEnrolment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody ExecuteBatchRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify batch belongs to this profile
        // ... existing PayorEnrolmentController.execute logic
    }

    /**
     * List batches for this profile.
     * ProfileId derived from JWT.
     */
    @GetMapping("/payor-enrolment/batches")
    public ResponseEntity<?> listBatches(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // ... existing PayorEnrolmentController.listBatches logic
    }

    /**
     * Get batch details.
     */
    @GetMapping("/batches/{batchId}")
    public ResponseEntity<?> getBatch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String batchId) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify batch belongs to this profile
        // ... existing BatchController.getBatch logic
    }

    @GetMapping("/batches/{batchId}/items")
    public ResponseEntity<?> getBatchItems(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String batchId,
            @RequestParam(required = false) String status) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify batch belongs to this profile
        // ... existing BatchController.getBatchItems logic
    }

    // ==================== User Management (Within Own Profile) ====================

    /**
     * List users in this profile.
     */
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // ... existing ProfileUsersController.listProfileUsers logic
    }

    /**
     * Add user to this profile.
     */
    @PostMapping("/users")
    public ResponseEntity<?> addUser(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AddUserRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();
        String createdBy = auth0UserContext.getUserEmail().orElse("system");

        // ... existing ProfileUsersController.addUserToProfile logic
    }

    /**
     * Get user details (must belong to this profile).
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify user belongs to this profile
        // ... existing ProfileUsersController.getUserDetail logic
    }

    /**
     * Update user name.
     */
    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody UpdateUserRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify user belongs to this profile
    }

    @PostMapping("/users/{userId}/resend-invitation")
    public ResponseEntity<?> resendInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();
        // Verify user belongs to this profile
    }

    @PutMapping("/users/{userId}/lock")
    public ResponseEntity<?> lockUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody LockUserRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();
        // Verify user belongs to this profile
    }

    @PutMapping("/users/{userId}/unlock")
    public ResponseEntity<?> unlockUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();
        // Verify user belongs to this profile
    }

    @PutMapping("/users/{userId}/deactivate")
    public ResponseEntity<?> deactivateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody DeactivateUserRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();
        // Verify user belongs to this profile
    }

    @PutMapping("/users/{userId}/activate")
    public ResponseEntity<?> activateUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();
        // Verify user belongs to this profile
    }

    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<?> addRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @RequestBody RoleRequest request) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();
        // Verify user belongs to this profile
    }

    @DeleteMapping("/users/{userId}/roles/{role}")
    public ResponseEntity<?> removeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String userId,
            @PathVariable String role) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();
        // Verify user belongs to this profile
    }

    // ==================== Permission Policies (Read-Only) ====================

    /**
     * List permission policies for this profile.
     */
    @GetMapping("/permission-policies")
    public ResponseEntity<?> listPolicies(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // ... existing PermissionPolicyController.listPolicies logic
    }

    /**
     * Get specific policy.
     */
    @GetMapping("/permission-policies/{policyId}")
    public ResponseEntity<?> getPolicy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String policyId) {

        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        // Verify policy belongs to this profile
    }
}
```

---

#### Step 12: Create IndirectClientController (Indirect Client BFF)

**Package**: `com.knight.application.rest.indirect`

**File**: `/application/src/main/java/com/knight/application/rest/indirect/IndirectClientController.java`

```java
package com.knight.application.rest.indirect;

import com.knight.application.security.IssuerValidator;
import com.knight.application.security.auth0.Auth0UserContext;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * BFF Controller for Indirect Client UI.
 *
 * Authentication: Auth0 only.
 * Users: Indirect client staff managing their own business info and OFI accounts.
 *
 * Key behaviors:
 * - ProfileId and IndirectClientId derived from JWT (never passed as parameter)
 * - Can manage their own related persons
 * - Can add/update/remove their own OFI accounts
 * - Cannot have their own indirect clients (no nesting!)
 *
 * Rejects Entra ID and Portal JWT tokens.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/indirect")
@RequiredArgsConstructor
public class IndirectClientController {

    private final IssuerValidator issuerValidator;
    private final Auth0UserContext auth0UserContext;

    // Inject existing services
    private final IndirectClientRepository indirectClientRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final UserQueries userQueries;
    private final PermissionPolicyQueries policyQueries;

    // ==================== Helper Methods ====================

    private ProfileId getProfileIdFromContext() {
        return auth0UserContext.getUser()
            .map(user -> user.profileId())
            .orElseThrow(() -> new UnauthorizedException("User not found for Auth0 subject"));
    }

    /**
     * Get the indirect client ID associated with this user's profile.
     * Indirect profiles have a 1:1 relationship with IndirectClient.
     */
    private IndirectClientId getIndirectClientIdFromContext() {
        ProfileId profileId = getProfileIdFromContext();

        // Find the indirect client where this profile is the client's profile
        return indirectClientRepository.findByProfileId(profileId)
            .map(IndirectClient::id)
            .orElseThrow(() -> new UnauthorizedException("No indirect client found for profile"));
    }

    // ==================== My Indirect Client Info ====================

    /**
     * Get my indirect client details.
     * The indirect client is determined by the user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyIndirectClient(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.requireAuth0(jwt);

        IndirectClientId clientId = getIndirectClientIdFromContext();

        return indirectClientRepository.findById(clientId)
            .map(this::toDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Related Persons Management ====================

    /**
     * Add a related person to my indirect client.
     */
    @PostMapping("/persons")
    public ResponseEntity<?> addRelatedPerson(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody RelatedPersonRequest request) {

        issuerValidator.requireAuth0(jwt);
        IndirectClientId clientId = getIndirectClientIdFromContext();

        return indirectClientRepository.findById(clientId)
            .map(client -> {
                Email email = request.email() != null && !request.email().isBlank()
                    ? Email.of(request.email()) : null;
                Phone phone = request.phone() != null && !request.phone().isBlank()
                    ? Phone.of(request.phone()) : null;
                PersonRole role = PersonRole.valueOf(request.role());

                client.addRelatedPerson(request.name(), role, email, phone);
                indirectClientRepository.save(client);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update a related person on my indirect client.
     */
    @PutMapping("/persons/{personId}")
    public ResponseEntity<?> updateRelatedPerson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String personId,
            @RequestBody RelatedPersonRequest request) {

        issuerValidator.requireAuth0(jwt);
        IndirectClientId clientId = getIndirectClientIdFromContext();

        // ... update logic
    }

    /**
     * Remove a related person from my indirect client.
     */
    @DeleteMapping("/persons/{personId}")
    public ResponseEntity<?> removeRelatedPerson(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String personId) {

        issuerValidator.requireAuth0(jwt);
        IndirectClientId clientId = getIndirectClientIdFromContext();

        // ... remove logic
    }

    // ==================== OFI Account Management (Only Indirect Clients!) ====================

    /**
     * Get my OFI accounts.
     */
    @GetMapping("/accounts")
    public ResponseEntity<?> getMyAccounts(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.requireAuth0(jwt);
        IndirectClientId clientId = getIndirectClientIdFromContext();

        List<OfiAccountDto> accounts = clientAccountRepository.findByIndirectClientId(clientId.urn())
            .stream()
            .map(this::toOfiAccountDto)
            .toList();

        return ResponseEntity.ok(accounts);
    }

    /**
     * Add an OFI account to my indirect client.
     * ONLY indirect clients can add OFI accounts (not direct clients on their behalf).
     */
    @PostMapping("/accounts")
    public ResponseEntity<?> addOfiAccount(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody AddOfiAccountRequest request) {

        issuerValidator.requireAuth0(jwt);
        IndirectClientId clientId = getIndirectClientIdFromContext();

        return indirectClientRepository.findById(clientId)
            .map(client -> {
                // Create OFI account ID in format: OFI:CAN:bank(3):transit(5):accountNumber(12)
                String paddedAccountNumber = String.format("%012d", Long.parseLong(request.accountNumber()));
                String segments = request.bankCode() + ":" + request.transitNumber() + ":" + paddedAccountNumber;
                ClientAccountId accountId = new ClientAccountId(AccountSystem.OFI, OfiAccountType.CAN.name(), segments);

                // Create and save the OFI account
                ClientAccount ofiAccount = ClientAccount.createOfiAccount(
                    accountId,
                    clientId.urn(),
                    Currency.CAD,
                    request.accountHolderName()
                );
                clientAccountRepository.save(ofiAccount);

                return ResponseEntity.created(URI.create("/api/v1/indirect/accounts/" + accountId.urn()))
                    .body(toOfiAccountDto(ofiAccount));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update an OFI account.
     */
    @PutMapping("/accounts/{accountId}")
    public ResponseEntity<?> updateOfiAccount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountId,
            @RequestBody UpdateOfiAccountRequest request) {

        issuerValidator.requireAuth0(jwt);
        IndirectClientId clientId = getIndirectClientIdFromContext();

        // Verify account belongs to this indirect client, then update
        // ...
    }

    /**
     * Deactivate (close) an OFI account.
     */
    @DeleteMapping("/accounts/{accountId}")
    public ResponseEntity<?> deactivateOfiAccount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountId) {

        issuerValidator.requireAuth0(jwt);
        IndirectClientId clientId = getIndirectClientIdFromContext();

        // Verify account belongs to this indirect client, then close
        return clientAccountRepository.findById(ClientAccountId.of(accountId))
            .filter(account -> account.clientId().equals(clientId.urn()))
            .map(account -> {
                account.close();
                clientAccountRepository.save(account);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== User Management (Self Only) ====================

    /**
     * Get my user details.
     */
    @GetMapping("/me/user")
    public ResponseEntity<?> getMyUser(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.requireAuth0(jwt);

        return auth0UserContext.getUser()
            .map(this::toUserDetailDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Update my user name.
     */
    @PutMapping("/me/user")
    public ResponseEntity<?> updateMyUser(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateUserRequest request) {

        issuerValidator.requireAuth0(jwt);

        return auth0UserContext.getUser()
            .map(user -> {
                userCommands.updateUserName(new UpdateUserNameCmd(
                    user.userId(),
                    request.firstName(),
                    request.lastName()
                ));
                UserDetail updated = userQueries.getUserDetail(user.userId());
                return ResponseEntity.ok(toUserDetailDto(updated));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * List users in my profile (co-workers).
     */
    @GetMapping("/users")
    public ResponseEntity<?> listProfileUsers(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        List<ProfileUserSummary> users = userQueries.listUsersByProfile(profileId);
        return ResponseEntity.ok(users.stream().map(this::toProfileUserDto).toList());
    }

    // ==================== Permission Policies (Read-Only) ====================

    /**
     * List my permission policies.
     */
    @GetMapping("/permission-policies")
    public ResponseEntity<?> listPolicies(@AuthenticationPrincipal Jwt jwt) {
        issuerValidator.requireAuth0(jwt);
        ProfileId profileId = getProfileIdFromContext();

        List<PolicyDto> policies = policyQueries.listPoliciesByProfile(profileId);
        return ResponseEntity.ok(policies.stream().map(this::toDto).toList());
    }
}
```

---

#### Step 13: Create IssuerValidator Service

**File**: `/application/src/main/java/com/knight/application/security/IssuerValidator.java`

```java
package com.knight.application.security;

import com.knight.application.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Service to validate JWT issuers and enforce BFF access rules.
 */
@Component
@RequiredArgsConstructor
public class IssuerValidator {

    private final JwtProperties jwtProperties;

    /**
     * Reject Auth0 tokens - used by BankAdminController.
     * Bank admin endpoints are for Entra ID / Portal JWT only.
     */
    public void rejectAuth0(Jwt jwt) {
        String issuer = jwt.getIssuer().toString();
        String auth0Issuer = jwtProperties.getAuth0().getIssuerUri();

        if (issuer.equals(auth0Issuer)) {
            throw new ForbiddenException("Auth0 tokens are not allowed for bank admin endpoints");
        }
    }

    /**
     * Require Auth0 token - used by ClientController and IndirectClientController.
     * Client/Indirect endpoints are for Auth0 (and future ANP) only.
     */
    public void requireAuth0(Jwt jwt) {
        String issuer = jwt.getIssuer().toString();
        String auth0Issuer = jwtProperties.getAuth0().getIssuerUri();

        if (!issuer.equals(auth0Issuer)) {
            throw new ForbiddenException("Only Auth0 tokens are allowed for client endpoints");
        }
    }

    /**
     * Check if this is an Auth0 token.
     */
    public boolean isAuth0Token(Jwt jwt) {
        String issuer = jwt.getIssuer().toString();
        return issuer.equals(jwtProperties.getAuth0().getIssuerUri());
    }

    /**
     * Check if this is an Entra ID token.
     */
    public boolean isEntraToken(Jwt jwt) {
        String issuer = jwt.getIssuer().toString();
        return issuer.contains("login.microsoftonline.com");
    }

    /**
     * Check if this is a Portal JWT token.
     */
    public boolean isPortalToken(Jwt jwt) {
        String issuer = jwt.getIssuer().toString();
        return issuer.equals(jwtProperties.getPortal().getIssuer());
    }
}
```

---

#### Step 14: Create ForbiddenException

**File**: `/application/src/main/java/com/knight/application/security/ForbiddenException.java`

```java
package com.knight.application.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
```

---

### Phase 7: Portal (Employee) Service Updates

The Employee Portal calls the Platform API via WebClient. All endpoint references must be updated to use the new `/api/v1/bank/*` prefix.

---

#### Step 15: Update Portal Services

**Files to update in `/employee-portal/src/main/java/com/knight/portal/services/`:**

| Service | Old Endpoint | New Endpoint |
|---------|-------------|--------------|
| `ClientService.java` | `/api/clients` | `/api/v1/bank/clients` |
| `ClientService.java` | `/api/clients/{clientId}` | `/api/v1/bank/clients/{clientId}` |
| `ClientService.java` | `/api/clients/{clientId}/accounts` | `/api/v1/bank/clients/{clientId}/accounts` |
| `ClientService.java` | `/api/clients/{clientId}/profiles` | `/api/v1/bank/clients/{clientId}/profiles` |
| `ProfileService.java` | `/api/profiles` | `/api/v1/bank/profiles` |
| `ProfileService.java` | `/api/profiles/search` | `/api/v1/bank/profiles/search` |
| `ProfileService.java` | `/api/profiles/{profileId}` | `/api/v1/bank/profiles/{profileId}` |
| `ProfileService.java` | `/api/profiles/{profileId}/detail` | `/api/v1/bank/profiles/{profileId}/detail` |
| `ProfileService.java` | `/api/profiles/{profileId}/clients` | `/api/v1/bank/profiles/{profileId}/clients` |
| `ProfileService.java` | `/api/profiles/{profileId}/services` | `/api/v1/bank/profiles/{profileId}/services` |
| `ProfileService.java` | `/api/indirect-profiles` | `/api/v1/bank/indirect-profiles` |
| `ProfileService.java` | `/api/indirect-profiles/parent-clients` | `/api/v1/bank/indirect-profiles/parent-clients` |
| `ProfileService.java` | `/api/clients/{clientId}/profiles/primary` | `/api/v1/bank/clients/{clientId}/profiles/primary` |
| `ProfileService.java` | `/api/clients/{clientId}/profiles/secondary` | `/api/v1/bank/clients/{clientId}/profiles/secondary` |
| `IndirectClientService.java` | `/api/indirect-clients/by-client/{clientId}` | `/api/v1/bank/indirect-clients/by-client/{clientId}` |
| `IndirectClientService.java` | `/api/indirect-clients/by-profile` | `/api/v1/bank/indirect-clients/by-profile` |
| `IndirectClientService.java` | `/api/indirect-clients/{id}` | `/api/v1/bank/indirect-clients/{id}` |
| `IndirectClientService.java` | `/api/indirect-clients` | `/api/v1/bank/indirect-clients` |
| `IndirectClientService.java` | `/api/indirect-clients/{id}/persons` | `/api/v1/bank/indirect-clients/{id}/persons` |
| `IndirectClientService.java` | `/api/indirect-clients/{id}/persons/{personId}` | `/api/v1/bank/indirect-clients/{id}/persons/{personId}` |
| `IndirectClientService.java` | `/api/indirect-clients/{id}/accounts` | `/api/v1/bank/indirect-clients/{id}/accounts` |
| `UserService.java` | `/api/profiles/{profileId}/users` | `/api/v1/bank/profiles/{profileId}/users` |
| `UserService.java` | `/api/profiles/{profileId}/users/{userId}` | `/api/v1/bank/profiles/{profileId}/users/{userId}` |
| `UserService.java` | `/api/users/{userId}` | `/api/v1/bank/users/{userId}` |
| `UserService.java` | `/api/users/{userId}/resend-invitation` | `/api/v1/bank/users/{userId}/resend-invitation` |
| `UserService.java` | `/api/users/{userId}/lock` | `/api/v1/bank/users/{userId}/lock` |
| `UserService.java` | `/api/users/{userId}/unlock` | `/api/v1/bank/users/{userId}/unlock` |
| `UserService.java` | `/api/users/{userId}/deactivate` | `/api/v1/bank/users/{userId}/deactivate` |
| `UserService.java` | `/api/users/{userId}/activate` | `/api/v1/bank/users/{userId}/activate` |
| `UserService.java` | `/api/users/{userId}/roles` | `/api/v1/bank/users/{userId}/roles` |
| `UserService.java` | `/api/users/{userId}/roles/{role}` | `/api/v1/bank/users/{userId}/roles/{role}` |
| `PayorEnrolmentService.java` | `/api/profiles/{profileId}/payor-enrolment/validate` | `/api/v1/bank/profiles/{profileId}/payor-enrolment/validate` |
| `PayorEnrolmentService.java` | `/api/profiles/{profileId}/payor-enrolment/execute` | `/api/v1/bank/profiles/{profileId}/payor-enrolment/execute` |
| `PayorEnrolmentService.java` | `/api/profiles/{profileId}/payor-enrolment/batches` | `/api/v1/bank/profiles/{profileId}/payor-enrolment/batches` |
| `PayorEnrolmentService.java` | `/api/batches/{batchId}` | `/api/v1/bank/batches/{batchId}` |
| `PayorEnrolmentService.java` | `/api/batches/{batchId}/items` | `/api/v1/bank/batches/{batchId}/items` |

---

### Phase 8: Test Updates

All integration tests that call the old endpoints must be updated to use the new paths.

---

#### Step 16: Update Integration Tests

**Files to update in `/application/src/test/java/`:**

Search for all files containing `/api/` and update to use appropriate BFF prefix:
- Bank admin tests: `/api/v1/bank/*`
- Client tests (if any): `/api/v1/client/*`
- Indirect client tests (if any): `/api/v1/indirect/*`

Example pattern:
```java
// Before
mockMvc.perform(get("/api/clients")
    .header("Authorization", "Bearer " + token))

// After (Bank Admin)
mockMvc.perform(get("/api/v1/bank/clients")
    .header("Authorization", "Bearer " + entraToken))

// After (Client BFF)
mockMvc.perform(get("/api/v1/client/indirect-clients")
    .header("Authorization", "Bearer " + auth0Token))
```

---

### Endpoint Summary Tables

---

#### BankAdminController Endpoints (`/api/v1/bank`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/clients` | Search clients |
| GET | `/clients/{clientId}` | Get client details |
| GET | `/clients/{clientId}/accounts` | Get client accounts |
| GET | `/clients/{clientId}/profiles` | Get profiles for client |
| GET | `/clients/{clientId}/profiles/primary` | Get primary profiles |
| GET | `/clients/{clientId}/profiles/secondary` | Get secondary profiles |
| POST | `/profiles` | Create profile |
| GET | `/profiles/{profileId}` | Get profile summary |
| GET | `/profiles/{profileId}/detail` | Get profile detail |
| POST | `/profiles/search` | Search profiles |
| POST | `/profiles/{profileId}/services` | Enroll service |
| POST | `/profiles/{profileId}/clients` | Add secondary client |
| DELETE | `/profiles/{profileId}/clients/{clientId}` | Remove secondary client |
| GET | `/indirect-profiles` | Search indirect profiles |
| GET | `/indirect-profiles/parent-clients` | Get parent clients filter |
| GET | `/indirect-clients/by-client/{clientId}` | Get indirect clients by client |
| GET | `/indirect-clients/by-profile` | Get indirect clients by profile |
| GET | `/indirect-clients/{id}` | Get indirect client detail |
| POST | `/indirect-clients` | Create indirect client |
| POST | `/indirect-clients/{id}/persons` | Add related person |
| PUT | `/indirect-clients/{id}/persons/{personId}` | Update related person |
| DELETE | `/indirect-clients/{id}/persons/{personId}` | Remove related person |
| GET | `/indirect-clients/{id}/accounts` | Get OFI accounts |
| GET | `/profiles/{profileId}/users` | List profile users |
| POST | `/profiles/{profileId}/users` | Add user to profile |
| GET | `/profiles/{profileId}/users/{userId}` | Get user detail |
| PUT | `/users/{userId}` | Update user |
| POST | `/users/{userId}/resend-invitation` | Resend invitation |
| PUT | `/users/{userId}/lock` | Lock user |
| PUT | `/users/{userId}/unlock` | Unlock user |
| PUT | `/users/{userId}/deactivate` | Deactivate user |
| PUT | `/users/{userId}/activate` | Activate user |
| POST | `/users/{userId}/roles` | Add role |
| DELETE | `/users/{userId}/roles/{role}` | Remove role |
| GET | `/profiles/{profileId}/permission-policies` | List policies |
| GET | `/profiles/{profileId}/permission-policies/{policyId}` | Get policy |
| POST | `/profiles/{profileId}/permission-policies` | Create policy |
| PUT | `/profiles/{profileId}/permission-policies/{policyId}` | Update policy |
| DELETE | `/profiles/{profileId}/permission-policies/{policyId}` | Delete policy |
| GET | `/batches/{batchId}` | Get batch |
| GET | `/batches/{batchId}/items` | Get batch items |
| POST | `/profiles/{profileId}/payor-enrolment/validate` | Validate batch |
| POST | `/profiles/{profileId}/payor-enrolment/execute` | Execute batch |
| GET | `/profiles/{profileId}/payor-enrolment/batches` | List batches |

---

#### ClientController Endpoints (`/api/v1/client`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/indirect-clients` | Get my indirect clients |
| GET | `/indirect-clients/{id}` | Get indirect client detail |
| POST | `/indirect-clients` | Create indirect client |
| PUT | `/indirect-clients/{id}/name` | Update business name |
| POST | `/indirect-clients/{id}/persons` | Add related person |
| PUT | `/indirect-clients/{id}/persons/{personId}` | Update related person |
| DELETE | `/indirect-clients/{id}/persons/{personId}` | Remove related person |
| GET | `/indirect-clients/{id}/accounts` | Get OFI accounts (read-only) |
| POST | `/payor-enrolment/validate` | Validate batch |
| POST | `/payor-enrolment/execute` | Execute batch |
| GET | `/payor-enrolment/batches` | List batches |
| GET | `/batches/{batchId}` | Get batch |
| GET | `/batches/{batchId}/items` | Get batch items |
| GET | `/users` | List profile users |
| POST | `/users` | Add user |
| GET | `/users/{userId}` | Get user |
| PUT | `/users/{userId}` | Update user |
| POST | `/users/{userId}/resend-invitation` | Resend invitation |
| PUT | `/users/{userId}/lock` | Lock user |
| PUT | `/users/{userId}/unlock` | Unlock user |
| PUT | `/users/{userId}/deactivate` | Deactivate user |
| PUT | `/users/{userId}/activate` | Activate user |
| POST | `/users/{userId}/roles` | Add role |
| DELETE | `/users/{userId}/roles/{role}` | Remove role |
| GET | `/permission-policies` | List policies (read-only) |
| GET | `/permission-policies/{policyId}` | Get policy (read-only) |

---

#### IndirectClientController Endpoints (`/api/v1/indirect`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/me` | Get my indirect client |
| POST | `/persons` | Add related person |
| PUT | `/persons/{personId}` | Update related person |
| DELETE | `/persons/{personId}` | Remove related person |
| GET | `/accounts` | Get my OFI accounts |
| POST | `/accounts` | Add OFI account |
| PUT | `/accounts/{accountId}` | Update OFI account |
| DELETE | `/accounts/{accountId}` | Close OFI account |
| GET | `/me/user` | Get my user details |
| PUT | `/me/user` | Update my user |
| GET | `/users` | List profile users |
| GET | `/permission-policies` | List policies (read-only) |

---

### Files to Delete After Migration

Once the new controllers are implemented and tested, remove the old controllers:

| File | Reason |
|------|--------|
| `ClientController.java` | Migrated to BankAdminController |
| `ProfileController.java` | Migrated to BankAdminController |
| `IndirectProfileController.java` | Migrated to BankAdminController |
| `IndirectClientController.java` | Split across all three controllers |
| `PermissionPolicyController.java` | Migrated to all three controllers |
| `ProfileUsersController.java` | Migrated to all three controllers |
| `PayorEnrolmentController.java` | Migrated to BankAdmin & ClientController |
| `BatchController.java` | Migrated to BankAdmin & ClientController |

---

*Document Version: 2.0*
*Created: 2025-12-23*
*Updated: 2025-12-23*
*Author: Claude Code Assistant*
