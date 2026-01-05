# Dual Authentication Architecture Plan

## Overview

This document describes the dual authentication approach for the Knight platform, enabling:
- **Production**: Entra ID (Azure AD) tokens via employee-gateway
- **Local/Docker Development**: Locally-generated JWT tokens via LDAP authentication

The Employee Portal will generate JWT tokens that mimic Entra ID structure, allowing the Application API to validate tokens from either source.

## Architecture

### Production Flow (with employee-gateway)

```
Browser → employee-gateway → Employee Portal → Application API
              │                    │                  │
              │ Entra OAuth2       │ Pass-through     │ Validate
              │ Authentication     │ Bearer token     │ JWT signature
              └────────────────────┴──────────────────┴─────────────
```

1. User accesses employee-gateway
2. Gateway redirects to Microsoft Entra ID for authentication
3. Entra ID returns access token and ID token
4. Gateway stores session in Redis, forwards access token to Portal
5. Portal detects `Authorization: Bearer <token>` header
6. Portal passes token through to Application API calls
7. Application validates token against Entra JWKS endpoint

### Local/Docker Development Flow (without employee-gateway)

```
Browser → Employee Portal → Application API
              │                  │
              │ LDAP Auth        │ Validate
              │ Generate JWT     │ JWT signature
              └──────────────────┴─────────────
```

1. User authenticates via LDAP (embedded or Active Directory)
2. Portal generates JWT signed with local RSA key
3. Portal includes JWT in all API calls
4. Application validates token against Portal's JWKS endpoint

## Token Structure

### Entra ID Access Token (Production)

```json
{
  "iss": "https://login.microsoftonline.com/bca97ec9-a960-4e36-bf65-c7c7d0270745/v2.0",
  "aud": "api://74fdc44e-8ef5-4465-8848-f67d608c5651",
  "iat": 1703347200,
  "nbf": 1703347200,
  "exp": 1703350800,
  "sub": "AAAAAAAAAAAAAAAAAAAAAIkzqFVrSaSaFHy782bbtaQ",
  "oid": "00000000-0000-0000-66f3-3332eca7ea81",
  "preferred_username": "john.doe@knight.com",
  "email": "john.doe@knight.com",
  "name": "John Doe",
  "tid": "bca97ec9-a960-4e36-bf65-c7c7d0270745",
  "ver": "2.0"
}
```

### Portal-Generated Token (Development)

```json
{
  "iss": "http://localhost:8081",
  "aud": "knight-platform-api",
  "iat": 1703347200,
  "nbf": 1703347200,
  "exp": 1703350800,
  "sub": "john.doe@knight.com",
  "oid": "john.doe@knight.com",
  "preferred_username": "john.doe@knight.com",
  "email": "john.doe@knight.com",
  "name": "John Doe"
}
```

### LDAP to JWT Claim Mapping

| LDAP Attribute | JWT Claim | Example Value |
|----------------|-----------|---------------|
| `mail` | `sub` | `john.doe@knight.com` |
| `mail` | `oid` | `john.doe@knight.com` |
| `mail` | `email` | `john.doe@knight.com` |
| `mail` | `preferred_username` | `john.doe@knight.com` |
| `cn` | `name` | `John Doe` |

## Implementation Details

### Employee Portal Changes

#### 1. JWT Generation Service

**Location**: `employee-portal/src/main/java/com/knight/portal/security/jwt/`

```java
@Service
public class JwtTokenService {
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer;

    @PostConstruct
    public void init() {
        // Generate RSA key pair at startup (2048-bit)
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair pair = keyGen.generateKeyPair();
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey = (RSAPublicKey) pair.getPublic();
    }

    public String generateToken(LdapAuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(issuer)
            .audience().add("knight-platform-api").and()
            .subject(user.getEmail())
            .claim("oid", user.getEmail())
            .claim("email", user.getEmail())
            .claim("preferred_username", user.getEmail())
            .claim("name", user.getDisplayName())
            .issuedAt(Date.from(now))
            .notBefore(Date.from(now))
            .expiration(Date.from(now.plus(1, ChronoUnit.HOURS)))
            .signWith(privateKey, Algs.RS256)
            .compact();
    }
}
```

#### 2. JWKS Endpoint

**Location**: `employee-portal/src/main/java/com/knight/portal/security/jwt/`

```java
@RestController
@RequestMapping("/.well-known")
public class JwksController {
    private final JwtTokenService jwtTokenService;

    @GetMapping("/jwks.json")
    public Map<String, Object> getJwks() {
        RSAPublicKey publicKey = jwtTokenService.getPublicKey();

        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", jwtTokenService.getKeyId());
        jwk.put("n", Base64.getUrlEncoder().encodeToString(publicKey.getModulus().toByteArray()));
        jwk.put("e", Base64.getUrlEncoder().encodeToString(publicKey.getPublicExponent().toByteArray()));

        return Map.of("keys", List.of(jwk));
    }
}
```

#### 3. API Client Interceptor

**Location**: `employee-portal/src/main/java/com/knight/portal/config/`

```java
@Component
public class JwtAuthorizationInterceptor implements ClientHttpRequestInterceptor {
    private final JwtTokenService jwtTokenService;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        // Check if request already has Authorization header (pass-through from gateway)
        if (request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return execution.execute(request, body);
        }

        // Get current authenticated user and generate token
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LdapAuthenticatedUser user) {
            String token = jwtTokenService.generateToken(user);
            request.getHeaders().setBearerAuth(token);
        }

        return execution.execute(request, body);
    }
}
```

#### 4. Pass-through Detection

When employee-gateway is in front of Portal, it forwards the Entra access token in the `Authorization` header. The Portal should detect this and pass it through instead of generating a new token.

```java
@Component
public class GatewayTokenFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain chain) {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // Store in request attribute for later use by API client
            request.setAttribute("GATEWAY_TOKEN", authHeader.substring(7));
        }

        chain.doFilter(request, response);
    }
}
```

### Application API Changes

#### 1. Multi-Issuer JWT Validation

**Location**: `application/src/main/java/com/knight/application/config/`

The Application must validate tokens from multiple issuers:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(multiIssuerJwtDecoder()))
            );
        return http.build();
    }

    @Bean
    public JwtDecoder multiIssuerJwtDecoder() {
        // Entra ID issuer
        JwtDecoder entraDecoder = JwtDecoders.fromIssuerLocation(
            "https://login.microsoftonline.com/" + tenantId + "/v2.0"
        );

        // Portal issuer (local development)
        JwtDecoder portalDecoder = NimbusJwtDecoder
            .withJwkSetUri("http://portal:8081/.well-known/jwks.json")
            .build();

        Map<String, JwtDecoder> decoders = Map.of(
            "https://login.microsoftonline.com/" + tenantId + "/v2.0", entraDecoder,
            "http://localhost:8081", portalDecoder,
            "http://portal:8081", portalDecoder
        );

        return new DelegatingJwtDecoder(decoders);
    }
}
```

#### 2. Custom Delegating JWT Decoder

```java
public class DelegatingJwtDecoder implements JwtDecoder {
    private final Map<String, JwtDecoder> decoders;

    @Override
    public Jwt decode(String token) throws JwtException {
        // Parse token to get issuer without validation
        String issuer = extractIssuer(token);

        JwtDecoder decoder = decoders.get(issuer);
        if (decoder == null) {
            throw new JwtException("Unknown issuer: " + issuer);
        }

        return decoder.decode(token);
    }

    private String extractIssuer(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        // Parse JSON and extract "iss" claim
        return JsonParser.parseString(payload).getAsJsonObject().get("iss").getAsString();
    }
}
```

### Configuration

#### Employee Portal (`application.yml`)

```yaml
jwt:
  issuer: ${JWT_ISSUER:http://localhost:8081}
  token-validity-seconds: 3600
  audience: knight-platform-api
```

#### Application (`application.yml`)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Multi-issuer configuration
          issuers:
            entra:
              issuer-uri: https://login.microsoftonline.com/${ENTRA_TENANT_ID}/v2.0
              audience: api://${ENTRA_CLIENT_ID}
            portal:
              jwk-set-uri: ${PORTAL_JWKS_URI:http://localhost:8081/.well-known/jwks.json}
              issuer: ${PORTAL_JWT_ISSUER:http://localhost:8081}
              audience: knight-platform-api
```

#### Docker Compose Updates

```yaml
portal:
  environment:
    - JWT_ISSUER=http://portal:8081

platform:
  environment:
    - PORTAL_JWKS_URI=http://portal:8081/.well-known/jwks.json
    - PORTAL_JWT_ISSUER=http://portal:8081
```

## Security Considerations

1. **Key Generation**: RSA key pair is generated at startup and is ephemeral. This is acceptable for development/testing but means tokens become invalid on Portal restart.

2. **Token Lifetime**: 1-hour token validity with no refresh mechanism. Session timeout in Portal controls actual session duration.

3. **Issuer Validation**: Application strictly validates issuer claim against whitelist.

4. **Audience Validation**: Different audiences for Entra (`api://...`) and Portal (`knight-platform-api`). Application must accept both.

5. **No Signature Verification at Portal**: When pass-through mode is active, Portal trusts the gateway to have validated the Entra token.

## Dependencies

### Employee Portal

```xml
<!-- JWT Support -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
```

### Application

```xml
<!-- Spring Security OAuth2 Resource Server -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

## Implementation Steps

### Phase 1: Employee Portal JWT Generation

1. Add JJWT dependencies to `employee-portal/pom.xml`
2. Create `JwtTokenService` with key generation and token creation
3. Create `JwksController` to expose public key
4. Create `JwtAuthorizationInterceptor` for WebClient/RestTemplate
5. Configure WebClient to use the interceptor
6. Test JWT generation and JWKS endpoint

### Phase 2: Application Multi-Issuer Validation

1. Add OAuth2 Resource Server dependency to `application/pom.xml`
2. Create `DelegatingJwtDecoder` for multi-issuer support
3. Configure `SecurityFilterChain` with JWT validation
4. Add configuration properties for issuers
5. Test token validation from Portal JWKS

### Phase 3: Gateway Pass-through

1. Create `GatewayTokenFilter` to detect incoming tokens
2. Update `JwtAuthorizationInterceptor` to use pass-through token
3. Test end-to-end flow with employee-gateway

### Phase 4: Integration Testing

1. Test local development flow (LDAP → JWT → API)
2. Test Docker flow (embedded LDAP → JWT → API)
3. Test production flow (Gateway → pass-through → API)

## File Structure

```
employee-portal/
└── src/main/java/com/knight/portal/
    └── security/
        └── jwt/
            ├── JwtTokenService.java      # Token generation
            ├── JwksController.java       # JWKS endpoint
            └── GatewayTokenFilter.java   # Pass-through detection
    └── config/
        └── WebClientConfig.java          # Interceptor registration

application/
└── src/main/java/com/knight/application/
    └── config/
        └── SecurityConfig.java           # Multi-issuer JWT validation
    └── security/
        └── DelegatingJwtDecoder.java     # Issuer-based decoder routing
```

## Testing

### Manual Testing

```bash
# 1. Start Portal with LDAP
cd employee-portal
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 2. Get JWKS
curl http://localhost:8081/.well-known/jwks.json

# 3. Login and make API call (check logs for JWT)

# 4. Decode JWT at jwt.io to verify claims
```

### Unit Tests

- `JwtTokenServiceTest`: Verify token generation and claims
- `JwksControllerTest`: Verify JWKS format
- `DelegatingJwtDecoderTest`: Verify issuer routing

---

# Comprehensive Exploratory Test Plan

This section provides a complete test plan covering all authentication scenarios across the Knight platform.

## Test Environment Setup

### Prerequisites
- Docker and Docker Compose installed
- Java 17+ installed
- Maven installed
- Access to test LDAP users (embedded LDAP in local/docker profiles)
- Access to test Auth0 tenant (for client portal testing)

### Test Users

| User | Email | Password | Role | Portal |
|------|-------|----------|------|--------|
| Admin | admin@knight.com | admin123 | SECURITY_ADMIN | Employee |
| John Doe | john.doe@knight.com | password | SERVICE_ADMIN | Employee |
| Client User | client@example.com | (Auth0) | CREATOR | Client |
| Indirect Client | indirect@example.com | (Auth0) | READER | Indirect Client |

---

## Part 1: Employee Portal Authentication Tests

### 1.1 LDAP Authentication - Happy Path

#### TEST-EP-001: Local LDAP Login Success
**Preconditions:**
- Employee Portal running with `local` profile
- Embedded LDAP server active
- Application API running

**Steps:**
1. Navigate to `http://localhost:8081/login`
2. Enter username: `admin`
3. Enter password: `admin123`
4. Click "Login"

**Expected Results:**
- User redirected to main dashboard
- JWT token generated with claims:
  - `sub`: admin@knight.com
  - `oid`: admin@knight.com
  - `email`: admin@knight.com
  - `name`: Admin User
- Session established in Portal
- API calls include `Authorization: Bearer <jwt>` header

**Verification:**
```bash
# Check JWKS endpoint is accessible
curl http://localhost:8081/.well-known/jwks.json

# Verify response contains RSA key
{
  "keys": [{
    "kty": "RSA",
    "use": "sig",
    "alg": "RS256",
    "kid": "...",
    "n": "...",
    "e": "AQAB"
  }]
}
```

---

#### TEST-EP-002: Docker LDAP Login Success
**Preconditions:**
- Full stack running via `docker-compose up`
- Embedded LDAP configured in `docker` profile

**Steps:**
1. Navigate to `http://localhost:8081/login`
2. Enter username: `john.doe`
3. Enter password: `password`
4. Click "Login"

**Expected Results:**
- User authenticated against embedded LDAP
- JWT issuer is `http://portal:8081` (Docker hostname)
- Application API accepts token via Portal's JWKS

---

#### TEST-EP-003: Gateway Pass-through Authentication
**Preconditions:**
- Employee Gateway running (production-like setup)
- Entra ID configured
- Portal behind gateway

**Steps:**
1. Access Portal via gateway URL
2. Gateway redirects to Microsoft Entra ID
3. Complete Entra ID login
4. Gateway forwards to Portal with `Authorization` header

**Expected Results:**
- Portal detects incoming bearer token
- Portal does NOT generate new JWT
- Portal passes Entra token to Application API
- Application validates against Entra JWKS
- User sees portal dashboard with identity from Entra

**Verification Headers (in Portal logs):**
```
X-Auth-User-Id: 00000000-0000-0000-66f3-3332eca7ea81
X-Auth-User-Email: john.doe@knight.com
X-Auth-User-Name: John Doe
Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJS...
```

---

#### TEST-EP-004: Session Persistence After Navigation
**Preconditions:**
- User logged in via LDAP

**Steps:**
1. Login successfully
2. Navigate to Profiles page
3. Navigate to Users page
4. Navigate to Settings page
5. Refresh browser

**Expected Results:**
- Session maintained across navigation
- JWT regenerated for each API call (or cached appropriately)
- No re-authentication required
- User info displayed consistently

---

#### TEST-EP-005: Logout Flow
**Preconditions:**
- User logged in

**Steps:**
1. Click logout button
2. Confirm logout

**Expected Results:**
- Session cleared in Portal
- Security context invalidated
- Redirect to login page
- Subsequent API calls fail with 401

---

### 1.2 LDAP Authentication - Exception Paths

#### TEST-EP-101: Invalid Username
**Steps:**
1. Enter username: `nonexistent`
2. Enter password: `password`
3. Click "Login"

**Expected Results:**
- Error message: "Invalid username or password"
- No session created
- No JWT generated
- Login form displayed again

---

#### TEST-EP-102: Invalid Password
**Steps:**
1. Enter username: `admin`
2. Enter password: `wrongpassword`
3. Click "Login"

**Expected Results:**
- Error message: "Invalid username or password"
- No session created
- Account NOT locked (first attempt)

---

#### TEST-EP-103: Empty Credentials
**Steps:**
1. Leave username empty
2. Leave password empty
3. Click "Login"

**Expected Results:**
- Client-side validation error
- Form not submitted
- Message: "Username is required"

---

#### TEST-EP-104: LDAP Server Unavailable
**Preconditions:**
- LDAP server stopped/unreachable

**Steps:**
1. Enter valid credentials
2. Click "Login"

**Expected Results:**
- Error message: "Authentication service unavailable"
- Graceful error handling
- No stack trace exposed to user
- Logged as ERROR with details

---

#### TEST-EP-105: Special Characters in Username
**Steps:**
1. Enter username: `admin'; DROP TABLE users;--`
2. Enter password: `password`
3. Click "Login"

**Expected Results:**
- Input sanitized
- LDAP injection prevented
- Error message: "Invalid username or password"
- Security event logged

---

#### TEST-EP-106: SQL/LDAP Injection in Password
**Steps:**
1. Enter username: `admin`
2. Enter password: `*)(uid=*))(|(uid=*`
3. Click "Login"

**Expected Results:**
- LDAP filter injection prevented
- Authentication fails normally
- No unintended access granted

---

#### TEST-EP-107: Session Timeout
**Preconditions:**
- Session timeout set to 5 minutes for testing

**Steps:**
1. Login successfully
2. Wait 6 minutes (no activity)
3. Attempt to navigate to protected page

**Expected Results:**
- Session expired
- Redirect to login page
- Message: "Your session has expired"
- Previous page URL remembered for redirect after re-login

---

#### TEST-EP-108: Concurrent Sessions
**Steps:**
1. Login in Browser A
2. Login with same user in Browser B
3. Perform actions in Browser A

**Expected Results:**
- Both sessions valid (no single-session enforcement by default)
- Each browser has independent JWT
- Actions in both browsers succeed

---

#### TEST-EP-109: Token Expiry During Operation
**Preconditions:**
- Token validity set to 1 minute for testing

**Steps:**
1. Login successfully
2. Wait 2 minutes
3. Attempt API operation

**Expected Results:**
- API returns 401 Unauthorized
- Portal refreshes token (if refresh implemented)
- OR user prompted to re-login
- Operation can be retried

---

#### TEST-EP-110: JWKS Endpoint Unavailable
**Preconditions:**
- Portal running, Application running
- Portal's JWKS endpoint returns 500

**Steps:**
1. Login to Portal
2. Attempt API call from Portal

**Expected Results:**
- Application cannot validate JWT
- API returns 401 or 503
- Error logged on Application side
- Portal shows error to user

---

### 1.3 JWT Token Tests

#### TEST-JWT-001: Token Structure Validation
**Steps:**
1. Login successfully
2. Capture JWT from API request (browser dev tools)
3. Decode at jwt.io

**Expected Results:**
Token Header:
```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

Token Payload contains:
```json
{
  "iss": "http://localhost:8081",
  "aud": "knight-platform-api",
  "sub": "admin@knight.com",
  "oid": "admin@knight.com",
  "email": "admin@knight.com",
  "preferred_username": "admin@knight.com",
  "name": "Admin User",
  "iat": <timestamp>,
  "nbf": <timestamp>,
  "exp": <timestamp + 3600>
}
```

---

#### TEST-JWT-002: Token Signature Verification
**Steps:**
1. Capture valid JWT
2. Modify payload (change email)
3. Keep original signature
4. Send modified token to API

**Expected Results:**
- API returns 401 Unauthorized
- Log shows signature validation failure
- Token rejected

---

#### TEST-JWT-003: Token from Unknown Issuer
**Steps:**
1. Create JWT with issuer: "https://evil.com"
2. Sign with any key
3. Send to API

**Expected Results:**
- API returns 401 Unauthorized
- Log: "Unknown issuer: https://evil.com"
- Token rejected

---

#### TEST-JWT-004: Expired Token
**Steps:**
1. Create JWT with exp in the past
2. Sign correctly
3. Send to API

**Expected Results:**
- API returns 401 Unauthorized
- Token rejected due to expiration

---

#### TEST-JWT-005: Token Not Yet Valid (nbf)
**Steps:**
1. Create JWT with nbf in the future
2. Sign correctly
3. Send to API

**Expected Results:**
- API returns 401 Unauthorized
- Token rejected (not yet valid)

---

#### TEST-JWT-006: Missing Required Claims
**Steps:**
1. Create JWT without 'sub' claim
2. Sign correctly
3. Send to API

**Expected Results:**
- API returns 401 or 400
- Token rejected (missing required claim)

---

### 1.4 Multi-Issuer Validation Tests

#### TEST-MI-001: Portal Token Accepted
**Steps:**
1. Login via Portal (LDAP)
2. Make API call

**Expected Results:**
- Token with issuer `http://localhost:8081` accepted
- API responds successfully
- User context populated correctly

---

#### TEST-MI-002: Entra Token Accepted
**Preconditions:**
- Valid Entra ID access token

**Steps:**
1. Call API with Entra token in Authorization header

**Expected Results:**
- Token with issuer `https://login.microsoftonline.com/{tenant}/v2.0` accepted
- API responds successfully
- IssuerValidator confirms Entra token

---

#### TEST-MI-003: Auth0 Token Accepted (Client Portal)
**Preconditions:**
- Valid Auth0 access token for client

**Steps:**
1. Call API with Auth0 token

**Expected Results:**
- Token with issuer `https://{domain}.auth0.com/` accepted
- API responds successfully
- Auth0UserContext populated

---

#### TEST-MI-004: Issuer Restriction - Bank Endpoints
**Preconditions:**
- Auth0 token (client user)

**Steps:**
1. Call bank-admin endpoint (e.g., `/api/bank/profiles`)

**Expected Results:**
- IssuerValidator.requireBankIssuer() rejects Auth0 token
- API returns 403 Forbidden
- Message: "Access denied - bank issuer required"

---

#### TEST-MI-005: Issuer Restriction - Client Endpoints
**Preconditions:**
- Portal/Entra token (bank user)

**Steps:**
1. Call client endpoint (e.g., `/api/client/accounts`)

**Expected Results:**
- IssuerValidator.requireClientIssuer() rejects non-Auth0 token
- API returns 403 Forbidden
- Message: "Access denied - client issuer required"

---

## Part 2: Client Portal Authentication Tests

### 2.1 Auth0 Authentication - Happy Path

#### TEST-CP-001: Password Login Success
**Steps:**
1. Navigate to client portal login
2. Enter email: `client@example.com`
3. Enter password: (valid password)
4. Click "Sign In"

**Expected Results:**
- Auth0 authenticates user
- MFA challenge if enrolled
- Session created in Redis
- User redirected to dashboard
- API calls use Auth0 access token

---

#### TEST-CP-002: Passkey Login Success
**Preconditions:**
- User has passkey enrolled with UV capability

**Steps:**
1. Navigate to login
2. Click "Sign in with Passkey"
3. Complete WebAuthn ceremony (biometric/PIN)

**Expected Results:**
- Passkey authenticated with UV=true
- MFA requirement satisfied (UV acts as MFA)
- Session created with `mfa_satisfied=true`, `mfa_method=passkey_uv`
- No additional MFA challenge required

---

#### TEST-CP-003: Password + Guardian MFA
**Preconditions:**
- User has Guardian enrolled

**Steps:**
1. Enter email and password
2. Complete password authentication
3. Receive Guardian push notification
4. Approve on mobile device

**Expected Results:**
- Session created after MFA approval
- `mfa_satisfied=true`, `mfa_method=guardian`
- User accesses dashboard

---

#### TEST-CP-004: Password + TOTP MFA
**Preconditions:**
- User has TOTP enrolled

**Steps:**
1. Enter email and password
2. Complete password authentication
3. Enter 6-digit TOTP code

**Expected Results:**
- TOTP verified against Auth0
- Session created with `mfa_satisfied=true`, `mfa_method=totp`

---

#### TEST-CP-005: First-Time Registration (FTR)
**Preconditions:**
- User provisioned but never logged in
- Status: PENDING_VERIFICATION

**Steps:**
1. Enter email (provisioned user)
2. Receive email OTP
3. Enter OTP code
4. Set new password
5. Enroll MFA (Guardian or TOTP)
6. Optionally enroll passkey

**Expected Results:**
- Email verified
- Password set in Auth0
- MFA enrolled
- Status updated to ACTIVE
- User can login with new credentials

---

### 2.2 Auth0 Authentication - Exception Paths

#### TEST-CP-101: Invalid Email
**Steps:**
1. Enter email: `notauser@example.com`
2. Enter any password
3. Click "Sign In"

**Expected Results:**
- Error: "Invalid email or password"
- No user enumeration (same error as wrong password)
- Login failed event logged

---

#### TEST-CP-102: Invalid Password
**Steps:**
1. Enter valid email
2. Enter wrong password
3. Click "Sign In"

**Expected Results:**
- Error: "Invalid email or password"
- Failed attempt counted
- After N attempts, account may be locked

---

#### TEST-CP-103: Account Locked
**Preconditions:**
- User locked by admin

**Steps:**
1. Attempt login with valid credentials

**Expected Results:**
- Error: "Account locked. Contact administrator."
- Login prevented regardless of correct credentials
- Lock type (CLIENT, BANK, SECURITY) determines unlock authority

---

#### TEST-CP-104: MFA Timeout
**Preconditions:**
- User at MFA challenge screen

**Steps:**
1. Wait for Guardian polling timeout (2 minutes)
2. Do not approve push notification

**Expected Results:**
- Message: "MFA verification timed out"
- Option to resend/retry MFA
- Session not created

---

#### TEST-CP-105: Invalid TOTP Code
**Steps:**
1. Complete password authentication
2. Enter incorrect TOTP code

**Expected Results:**
- Error: "Invalid code"
- Remaining attempts shown
- After 3 failures, lockout period

---

#### TEST-CP-106: Passkey Authentication Failure
**Preconditions:**
- User has passkey enrolled

**Steps:**
1. Click "Sign in with Passkey"
2. Cancel WebAuthn ceremony

**Expected Results:**
- Message: "Passkey authentication cancelled"
- Option to use password instead
- Fallback flow available

---

#### TEST-CP-107: Passkey Not Available - Fallback Flow
**Preconditions:**
- User on device without passkey

**Steps:**
1. See "Passkey unavailable" message
2. Click "Use email verification"
3. Receive OTP email
4. Enter OTP code
5. Optionally re-enroll passkey on new device

**Expected Results:**
- Fallback OTP sent
- User authenticated after OTP verification
- Option to enroll passkey on current device

---

#### TEST-CP-108: Guardian Reset Flow
**Preconditions:**
- User lost access to Guardian device

**Steps:**
1. At MFA challenge, click "Lost access to Guardian?"
2. Receive OTP email
3. Enter OTP code
4. Guardian enrollment deleted

**Expected Results:**
- OTP verified
- Guardian enrollment removed from Auth0
- User prompted to re-enroll Guardian on next login

---

#### TEST-CP-109: Session Hijacking Prevention
**Steps:**
1. Login and get session cookie
2. Copy session cookie to different browser/IP
3. Attempt to access protected resource

**Expected Results:**
- Session validation checks IP/fingerprint (if implemented)
- Suspicious access logged
- Session may be invalidated

---

#### TEST-CP-110: CSRF Attack Prevention
**Steps:**
1. Login to client portal
2. Attempt API call without CSRF token

**Expected Results:**
- Request rejected with 403
- CSRF validation enforced
- Attack logged

---

### 2.3 Password Reset Tests

#### TEST-PR-001: Password Reset Success
**Steps:**
1. Click "Forgot Password"
2. Enter login ID
3. Receive OTP email
4. Enter OTP code
5. Set new password
6. Login with new password

**Expected Results:**
- OTP sent to registered email
- Reset token valid for limited time
- Password updated in Auth0
- Old password no longer works

---

#### TEST-PR-002: Password Reset - Invalid Login ID
**Steps:**
1. Enter non-existent login ID
2. Request reset

**Expected Results:**
- Generic message: "If account exists, OTP sent"
- No user enumeration
- No email sent (no user)

---

#### TEST-PR-003: Password Reset - Locked Account
**Steps:**
1. User account is locked
2. Request password reset

**Expected Results:**
- Error: "Account locked. Contact administrator."
- Reset not allowed while locked

---

#### TEST-PR-004: Password Reset - Weak Password
**Steps:**
1. Complete OTP verification
2. Enter weak password: "123456"

**Expected Results:**
- Error: "Password does not meet requirements"
- Requirements displayed:
  - Minimum 12 characters
  - Uppercase and lowercase
  - Number
  - Special character

---

#### TEST-PR-005: Password Reset Token Replay
**Steps:**
1. Complete password reset
2. Try to use same reset token again

**Expected Results:**
- Error: "Token already used or expired"
- Token invalidated after use

---

## Part 3: Application API Security Tests

### 3.1 Authorization Tests

#### TEST-API-001: Unauthenticated Access
**Steps:**
1. Call protected endpoint without Authorization header

**Expected Results:**
- 401 Unauthorized
- WWW-Authenticate header present
- No data returned

---

#### TEST-API-002: Invalid Token Format
**Steps:**
1. Call API with `Authorization: Bearer notavalidtoken`

**Expected Results:**
- 401 Unauthorized
- Token parsing failure logged

---

#### TEST-API-003: Role-Based Access Control
**Preconditions:**
- User with READER role only

**Steps:**
1. Attempt to create a profile (requires CREATOR)

**Expected Results:**
- 403 Forbidden
- Insufficient permissions logged

---

#### TEST-API-004: Profile-Level Authorization
**Preconditions:**
- User A belongs to Profile X
- User B belongs to Profile Y

**Steps:**
1. User A tries to access data from Profile Y

**Expected Results:**
- 403 Forbidden
- Cross-profile access denied

---

### 3.2 Security Headers Tests

#### TEST-SEC-001: CORS Configuration
**Steps:**
1. Make API call from unauthorized origin

**Expected Results:**
- CORS error in browser
- Access-Control-Allow-Origin not matching

---

#### TEST-SEC-002: Security Headers Present
**Steps:**
1. Check API response headers

**Expected Results:**
Headers present:
- X-Content-Type-Options: nosniff
- X-Frame-Options: DENY
- Content-Security-Policy: (appropriate policy)
- Strict-Transport-Security: (in production)

---

### 3.3 Step-Up Authentication Tests

#### TEST-STEP-001: Step-Up Required for Sensitive Operation
**Preconditions:**
- User logged in without MFA (password only)

**Steps:**
1. Attempt sensitive operation (e.g., approve payment)
2. System requires step-up authentication
3. Complete MFA challenge
4. Retry operation

**Expected Results:**
- Initial request returns 403 with step-up required
- After MFA, `mfa_satisfied=true` in session
- Operation succeeds

---

#### TEST-STEP-002: Step-Up with Expired MFA Token
**Steps:**
1. MFA token in session has expired
2. Attempt sensitive operation
3. System provides new MFA challenge

**Expected Results:**
- Refresh token flow initiated
- New MFA challenge presented
- After completion, operation proceeds

---

## Part 4: Integration Tests

### 4.1 End-to-End Flows

#### TEST-E2E-001: Full Client Onboarding
**Steps:**
1. Admin provisions new client user
2. User receives invitation email
3. User completes FTR (OTP → Password → MFA)
4. User enrolls passkey
5. User logs out
6. User logs in with passkey
7. User performs business operation

**Expected Results:**
- Complete flow works without errors
- All state transitions correct
- Audit trail complete

---

#### TEST-E2E-002: Full Employee Workflow
**Steps:**
1. Employee logs in via LDAP
2. Views profiles list
3. Creates new profile
4. Adds user to profile
5. Approves pending workflow
6. Logs out

**Expected Results:**
- All API calls authenticated
- JWT validated on each call
- Operations succeed
- Audit logged

---

### 4.2 Failover Tests

#### TEST-FAIL-001: Redis Unavailable
**Preconditions:**
- Client portal relies on Redis for sessions

**Steps:**
1. Stop Redis
2. Attempt login

**Expected Results:**
- Graceful error message
- No session created
- System logs Redis connection failure
- Health check shows unhealthy

---

#### TEST-FAIL-002: Auth0 Unavailable
**Steps:**
1. Block Auth0 API access
2. Attempt client portal login

**Expected Results:**
- Error: "Authentication service unavailable"
- Retry option provided
- Existing sessions may still work (cached tokens)

---

#### TEST-FAIL-003: Application API Unavailable
**Steps:**
1. Stop Application API
2. Attempt operation in Portal

**Expected Results:**
- Error displayed in Portal
- No crash or infinite loading
- "Service temporarily unavailable" message

---

## Part 5: Performance & Load Tests

### TEST-PERF-001: JWT Generation Performance
**Metrics:**
- Generate 1000 tokens sequentially
- Expected: < 10ms per token

### TEST-PERF-002: Token Validation Performance
**Metrics:**
- Validate 1000 tokens (after JWKS cached)
- Expected: < 5ms per validation

### TEST-PERF-003: Concurrent Login Load
**Metrics:**
- 100 concurrent login attempts
- Expected: 95% complete within 2 seconds
- No failures due to contention

---

## Test Execution Checklist

### Pre-Test Setup
- [ ] All services running (docker-compose up)
- [ ] Database migrated to latest
- [ ] Test users created in embedded LDAP
- [ ] Auth0 test tenant configured
- [ ] Redis running and accessible

### Happy Path Execution
- [ ] TEST-EP-001 through TEST-EP-005
- [ ] TEST-CP-001 through TEST-CP-005
- [ ] TEST-JWT-001
- [ ] TEST-MI-001 through TEST-MI-003
- [ ] TEST-API-001 through TEST-API-004
- [ ] TEST-E2E-001, TEST-E2E-002

### Exception Path Execution
- [ ] TEST-EP-101 through TEST-EP-110
- [ ] TEST-CP-101 through TEST-CP-110
- [ ] TEST-JWT-002 through TEST-JWT-006
- [ ] TEST-MI-004, TEST-MI-005
- [ ] TEST-PR-001 through TEST-PR-005
- [ ] TEST-SEC-001, TEST-SEC-002
- [ ] TEST-STEP-001, TEST-STEP-002
- [ ] TEST-FAIL-001 through TEST-FAIL-003

### Sign-Off
- [ ] All critical tests passed
- [ ] Known issues documented
- [ ] Performance baselines established
- [ ] Security review completed
