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
