# Client Portal Migration Plan

## Overview

This document outlines the plan to migrate components from `/Users/igormusic/code/okta-app` to the Knight2 platform, enabling client-facing authentication and portal functionality.

## Source Projects (okta-app)

| Source | Description | Target |
|--------|-------------|--------|
| `auth-service` | Spring Boot backend for Auth0 authentication, MFA, user provisioning | Merge into `application` as `AuthenticationController` |
| `nginx` (OpenResty) | Lua-based authentication gateway with custom login page | `client-login` |
| `vaadin-app` | Vaadin UI for authenticated users | `client-portal` + `indirect-client-portal` |

## Target Architecture

```
                                    ┌─────────────────────────────────────────┐
                                    │           Knight2 Platform              │
                                    └─────────────────────────────────────────┘
                                                       │
        ┌──────────────────────────────────────────────┼
        │                                              │                                              
        ▼                                              ▼                                              
┌───────────────────┐                      ┌───────────────────┐                                      
│  employee-gateway │                      │   client-login    │                                      
│   (Entra ID)      │                      │   (Auth0 + Lua)   │                                      
│   Port: 8000      │                      │   Port: 9000      │                                      
└─────────┬─────────┘                      └─────────┬─────────┘                                      
          │                                          │                                                
          ▼                                          │    
┌───────────────────┐                                │    
│  employee-portal  │                                │  Portal routing based on client type
│   (Vaadin)        │                                │  returned from /api/login/user/check
│   Port: 8001      │                                │    
└─────────┬─────────┘                                ▼    
          │                          ┌───────────────────────────────────────┐
          │                          │         client-login routes to:       │
          │                          │  CLIENT → client-portal (8003)        │
          │                          │  INDIRECT_CLIENT → indirect-portal    │
          │                          │                      (8004)           │
          │                          └───────────────────────────────────────┘
          │                                          │
          │                    ┌─────────────────────┼─────────────────────┐
          │                    ▼                                           ▼
          │        ┌───────────────────┐                      ┌───────────────────┐
          │        │  client-portal    │                      │indirect-client    │
          │        │   (Vaadin)        │                      │    -portal        │
          │        │   Port: 8003      │                      │   Port: 8004      │
          │        └─────────┬─────────┘                      └─────────┬─────────┘
          │                  │                                          │
          └──────────────────┼──────────────────────────────────────────┘
                             │
                             ▼
                ┌───────────────────────────────────────────────────────┐
                │                    application                        │
                │                  (Platform API)                       │
                │                   Port: 8002                          │
                │                                                       │
                │  /api/v1/bank/*      ← @BankAccess (Entra/Portal)     │
                │  /api/v1/client/*    ← @ClientAccess (Auth0)          │
                │  /api/v1/indirect/*  ← @IndirectClientAccess (Auth0)  │
                │                                                       │
                │  /api/login/*        ← Basic Auth (client-login)      │
                │    ├── /api/login/auth/*       (login/logout)         │
                │    ├── /api/login/user/*       (user management)      │
                │    ├── /api/login/mfa/*        (MFA operations)       │
                │    └── /api/login/stepup/*     (step-up auth)         │
                └───────────────────────────────────────────────────────┘
                                         │
                                         ▼
                            ┌───────────────────────┐
                            │      SQL Server       │
                            │      Port: 1433       │
                            └───────────────────────┘
```

## Key Design Decisions

### 1. Auth Service Merged into Application

Instead of a separate `client-auth-service`, the authentication functionality is merged into the `application` module as `AuthenticationController`:

- **Endpoint prefix**: `/api/login/*`
- **Security**: Basic Auth between `client-login` and API
- **No separate microservice** - reduces operational complexity

### 2. Single Login Gateway for Both Client Types

The `client-login` gateway serves both direct clients and indirect clients:

1. User enters email on login page
2. `client-login` calls `/api/login/user/check` with email
3. API returns client type: `CLIENT` or `INDIRECT_CLIENT`
4. After successful authentication, `client-login` redirects to appropriate portal:
   - `CLIENT` → `client-portal` (port 8003)
   - `INDIRECT_CLIENT` → `indirect-client-portal` (port 8004)

### 3. Basic Auth for Login API

The `/api/login/*` endpoints use Basic Authentication:
- Username/password configured via environment variables
- Only `client-login` gateway has credentials
- Protects login endpoints from direct access

## API Access Matrix

| Controller | Endpoint Prefix | Access Method | Allowed Callers |
|------------|-----------------|---------------|-----------------|
| BankAdminController | `/api/v1/bank/*` | `@BankAccess` | Entra ID, Portal JWT |
| DirectClientController | `/api/v1/client/*` | `@ClientAccess` | Auth0 JWT |
| IndirectClientBffController | `/api/v1/indirect/*` | `@IndirectClientAccess` | Auth0 JWT |
| AuthenticationController | `/api/login/*` | Basic Auth | client-login gateway |

---

## Phase 1: Module Structure Setup

### 1.1 New Maven Modules

Add to root `pom.xml` modules section:
```xml
<modules>
    <!-- existing -->
    <module>kernel</module>
    <module>domain</module>
    <module>application</module>
    <module>employee-portal</module>
    <module>coverage-report</module>
    <!-- new -->
    <module>client-portal</module>
    <module>indirect-client-portal</module>
</modules>
```

> **Note**: No `client-auth-service` module - merged into `application`

### 1.2 Directory Structure

```
knight2/
├── application/                   # UPDATED - add AuthenticationController
│   └── src/main/java/com/knight/application/
│       ├── rest/
│       │   ├── bank/              # existing
│       │   ├── client/            # existing
│       │   ├── indirect/          # existing
│       │   └── login/             # NEW - from okta-app/auth-service
│       │       ├── AuthenticationController.java
│       │       ├── MfaController.java
│       │       ├── UserController.java
│       │       ├── StepupController.java
│       │       └── dto/
│       ├── security/
│       │   └── login/             # NEW - Basic Auth config for /api/login
│       │       └── LoginSecurityConfig.java
│       └── service/
│           └── auth0/             # NEW - Auth0 adapter
│               ├── Auth0Adapter.java
│               ├── Auth0Properties.java
│               └── Auth0Service.java
│
├── client-login/                  # NEW - from okta-app/nginx
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── conf.d/
│   │   └── default.conf
│   ├── lua/
│   │   ├── auth.lua
│   │   ├── oauth_login.lua
│   │   ├── oauth_callback.lua
│   │   ├── logout.lua
│   │   ├── redis_client.lua
│   │   ├── csrf.lua
│   │   ├── utils.lua
│   │   ├── config.lua
│   │   ├── api_proxy.lua
│   │   └── portal_router.lua      # NEW - routes to correct portal
│   └── login/
│       ├── index.html
│       ├── css/styles.css
│       └── js/
│
├── client-portal/                 # NEW - from okta-app/vaadin-app
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/knight/clientportal/
│       ├── ClientPortalApplication.java
│       ├── config/
│       ├── security/
│       ├── services/              # Calls /api/v1/client/*
│       └── views/
│
├── indirect-client-portal/        # NEW - similar to client-portal
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/knight/indirectportal/
│       ├── IndirectClientPortalApplication.java
│       ├── config/
│       ├── security/
│       ├── services/              # Calls /api/v1/indirect/*
│       └── views/
```

---

## Phase 2: Merge auth-service into Application

### 2.1 New Package Structure

Add to `application/src/main/java/com/knight/application/`:

```
rest/login/
├── AuthenticationController.java    # /api/login/auth/*
├── MfaController.java               # /api/login/mfa/*
├── UserController.java              # /api/login/user/*
├── StepupController.java            # /api/login/stepup/*
└── dto/
    ├── LoginRequest.java
    ├── LoginResponse.java
    ├── UserCheckRequest.java
    ├── UserCheckResponse.java       # includes clientType field
    ├── MfaChallengeRequest.java
    ├── MfaVerifyRequest.java
    └── ...

service/auth0/
├── Auth0Adapter.java                # All Auth0 Management API calls
├── Auth0Properties.java             # Configuration
└── Auth0Service.java                # Business logic

security/login/
└── LoginSecurityConfig.java         # Basic Auth for /api/login/*
```

### 2.2 Package Renaming

| Original (okta-app) | New (knight2) |
|---------------------|---------------|
| `com.example.auth.controller.AuthController` | `com.knight.application.rest.login.AuthenticationController` |
| `com.example.auth.controller.MfaController` | `com.knight.application.rest.login.MfaController` |
| `com.example.auth.controller.UserController` | `com.knight.application.rest.login.UserController` |
| `com.example.auth.controller.StepupController` | `com.knight.application.rest.login.StepupController` |
| `com.example.auth.adapter.Auth0Adapter` | `com.knight.application.service.auth0.Auth0Adapter` |
| `com.example.auth.config.Auth0Properties` | `com.knight.application.service.auth0.Auth0Properties` |

### 2.3 AuthenticationController

```java
package com.knight.application.rest.login;

@RestController
@RequestMapping("/api/login/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final Auth0Service auth0Service;

    /**
     * Exchange authorization code for tokens (called after Auth0 redirect).
     */
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> exchangeToken(@RequestBody @Valid TokenExchangeRequest request) {
        TokenResponse tokens = auth0Service.exchangeAuthorizationCode(
            request.code(),
            request.redirectUri()
        );
        return ResponseEntity.ok(tokens);
    }

    /**
     * Refresh access token using refresh token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        TokenResponse tokens = auth0Service.refreshAccessToken(request.refreshToken());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequest request) {
        auth0Service.revokeToken(request.refreshToken());
        return ResponseEntity.ok().build();
    }
}
```

### 2.4 UserController with Client Type

```java
package com.knight.application.rest.login;

@RestController
@RequestMapping("/api/login/user")
@RequiredArgsConstructor
public class UserController {

    private final Auth0Service auth0Service;
    private final UserRepository userRepository;  // Domain user lookup

    /**
     * Check if user exists and return their client type.
     * Used by client-login to determine portal routing.
     */
    @PostMapping("/check")
    public ResponseEntity<UserCheckResponse> checkUser(@RequestBody @Valid UserCheckRequest request) {
        // Check Auth0 for user existence
        Optional<Auth0User> auth0User = auth0Service.findUserByEmail(request.email());

        if (auth0User.isEmpty()) {
            return ResponseEntity.ok(UserCheckResponse.notFound());
        }

        // Determine client type from user's profile
        ClientType clientType = userRepository.findClientTypeByEmail(request.email())
            .orElse(ClientType.CLIENT);  // Default to CLIENT

        return ResponseEntity.ok(UserCheckResponse.found(
            auth0User.get().getId(),
            auth0User.get().getEmail(),
            clientType,  // CLIENT or INDIRECT_CLIENT
            auth0User.get().hasMfaEnrolled()
        ));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@RequestBody @Valid CreateUserRequest request) {
        // Create user in Auth0
        Auth0User user = auth0Service.createUser(request);
        return ResponseEntity.created(URI.create("/api/login/user/" + user.getId()))
            .body(UserResponse.from(user));
    }

    // Additional endpoints: updateUser, deleteUser, resetPassword...
}
```

### 2.5 UserCheckResponse DTO

```java
package com.knight.application.rest.login.dto;

public record UserCheckResponse(
    boolean exists,
    String userId,
    String email,
    ClientType clientType,  // CLIENT or INDIRECT_CLIENT
    boolean mfaEnrolled,
    List<String> mfaFactors
) {
    public enum ClientType {
        CLIENT,
        INDIRECT_CLIENT
    }

    public static UserCheckResponse notFound() {
        return new UserCheckResponse(false, null, null, null, false, List.of());
    }

    public static UserCheckResponse found(String userId, String email, ClientType clientType, boolean mfaEnrolled) {
        return new UserCheckResponse(true, userId, email, clientType, mfaEnrolled, List.of());
    }
}
```

### 2.6 Security Configuration for /api/login/*

```java
package com.knight.application.security.login;

@Configuration
@Order(1)  // Higher priority than JWT security
public class LoginSecurityConfig {

    @Value("${login.basic-auth.username}")
    private String username;

    @Value("${login.basic-auth.password}")
    private String password;

    @Bean
    @Order(1)
    public SecurityFilterChain loginSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/login/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService loginUserDetailsService() {
        UserDetails gatewayUser = User.builder()
            .username(username)
            .password("{noop}" + password)
            .roles("GATEWAY")
            .build();
        return new InMemoryUserDetailsManager(gatewayUser);
    }
}
```

### 2.7 Application Configuration Updates

Add to `application/src/main/resources/application.yml`:

```yaml
# Login API Basic Auth (for client-login gateway)
login:
  basic-auth:
    username: ${LOGIN_API_USERNAME:gateway}
    password: ${LOGIN_API_PASSWORD}

# Auth0 Configuration (moved from client-auth-service)
auth0:
  domain: ${AUTH0_DOMAIN}
  web-client-id: ${AUTH0_WEB_CLIENT_ID}
  web-client-secret: ${AUTH0_WEB_CLIENT_SECRET}
  m2m-client-id: ${AUTH0_M2M_CLIENT_ID}
  m2m-client-secret: ${AUTH0_M2M_CLIENT_SECRET}
  api-audience: ${AUTH0_API_AUDIENCE}
  connection: ${AUTH0_CONNECTION:Username-Password-Authentication}
```

---

## Phase 3: client-login Migration (OpenResty)

### 3.1 Directory Setup

Copy from `okta-app/nginx/` to `knight2/client-login/`:

```bash
mkdir -p client-login/{conf.d,lua,login}
cp -r /Users/igormusic/code/okta-app/nginx/nginx.conf client-login/
cp -r /Users/igormusic/code/okta-app/nginx/conf.d/* client-login/conf.d/
cp -r /Users/igormusic/code/okta-app/nginx/lua/* client-login/lua/
cp -r /Users/igormusic/code/okta-app/nginx/login/* client-login/login/
cp /Users/igormusic/code/okta-app/nginx/Dockerfile client-login/
```

### 3.2 Configuration Updates

Update `conf.d/default.conf`:

```nginx
# Upstream for Platform API (includes /api/login/*)
upstream platform_api {
    server platform:8080;
}

# Upstream for client-portal (Direct Clients)
upstream client_portal {
    server client-portal:8080;
}

# Upstream for indirect-client-portal (Indirect Clients)
upstream indirect_portal {
    server indirect-client-portal:8080;
}

server {
    listen 80;

    # Health check
    location /health {
        return 200 'OK';
        add_header Content-Type text/plain;
    }

    # Static login page
    location /login/ {
        alias /usr/local/openresty/nginx/html/login/;
        index index.html;
    }

    # API calls to platform (with Basic Auth)
    location /api/login/ {
        access_by_lua_file /usr/local/openresty/nginx/lua/api_proxy.lua;

        proxy_pass http://platform_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # Basic Auth header added by api_proxy.lua
    }

    # OAuth2 endpoints
    location /oauth2/login {
        content_by_lua_file /usr/local/openresty/nginx/lua/oauth_login.lua;
    }

    location /oauth2/callback {
        content_by_lua_file /usr/local/openresty/nginx/lua/oauth_callback.lua;
    }

    location /logout {
        content_by_lua_file /usr/local/openresty/nginx/lua/logout.lua;
    }

    # All other requests - route based on client type
    location / {
        access_by_lua_file /usr/local/openresty/nginx/lua/auth.lua;
        content_by_lua_file /usr/local/openresty/nginx/lua/portal_router.lua;
    }
}
```

### 3.3 Portal Router (New Lua Script)

Create `lua/portal_router.lua`:

```lua
-- portal_router.lua
-- Routes authenticated users to the correct portal based on client type

local redis_client = require "redis_client"
local config = require "config"

local session_id = ngx.var.cookie_CLIENT_SESSION
if not session_id then
    ngx.redirect("/login/")
    return
end

local session = redis_client.get_session(session_id)
if not session then
    ngx.redirect("/login/")
    return
end

-- Route based on client type stored in session
local client_type = session.client_type or "CLIENT"

if client_type == "INDIRECT_CLIENT" then
    -- Proxy to indirect-client-portal
    ngx.var.upstream = "indirect_portal"
else
    -- Default: Proxy to client-portal
    ngx.var.upstream = "client_portal"
end

-- Set headers for downstream
ngx.req.set_header("IV-USER", session.email)
ngx.req.set_header("Authorization", "Bearer " .. session.access_token)
ngx.req.set_header("X-Auth-User-Id", session.user_id)
ngx.req.set_header("X-Auth-User-Email", session.email)
ngx.req.set_header("X-Client-Type", client_type)

-- Proxy the request
local res = ngx.location.capture("/@" .. client_type .. ngx.var.request_uri)
ngx.status = res.status
ngx.say(res.body)
```

### 3.4 Update api_proxy.lua for Basic Auth

```lua
-- api_proxy.lua
-- Proxies requests to /api/login/* with Basic Auth

local config = require "config"

-- Add Basic Auth header
local username = config.get("login_api_username") or "gateway"
local password = config.get("login_api_password")
local auth = ngx.encode_base64(username .. ":" .. password)
ngx.req.set_header("Authorization", "Basic " .. auth)
```

### 3.5 Update oauth_callback.lua

Store client type in session after authentication:

```lua
-- In oauth_callback.lua, after successful auth:

-- Get client type from user check response (cached during login)
local client_type = ngx.ctx.user_check_response.client_type or "CLIENT"

-- Store in session
local session_data = {
    user_id = user_id,
    email = email,
    access_token = access_token,
    id_token = id_token,
    client_type = client_type,  -- NEW: store client type
    created_at = ngx.time(),
    last_activity = ngx.time()
}

redis_client.set_session(session_id, session_data, config.session_ttl)
```

### 3.6 Environment Variables

Update `lua/config.lua`:

```lua
local config = {
    -- Auth0
    auth0_domain = os.getenv("AUTH0_DOMAIN"),
    auth0_client_id = os.getenv("AUTH0_WEB_CLIENT_ID"),
    auth0_client_secret = os.getenv("AUTH0_WEB_CLIENT_SECRET"),

    -- Redis
    redis_host = os.getenv("REDIS_HOST") or "redis",
    redis_port = tonumber(os.getenv("REDIS_PORT")) or 6379,

    -- Session
    session_ttl = tonumber(os.getenv("CLIENT_SESSION_TTL")) or 1200,
    session_cookie_name = os.getenv("CLIENT_SESSION_COOKIE") or "CLIENT_SESSION",

    -- Platform API (Basic Auth)
    platform_api_url = os.getenv("PLATFORM_API_URL") or "http://platform:8080",
    login_api_username = os.getenv("LOGIN_API_USERNAME") or "gateway",
    login_api_password = os.getenv("LOGIN_API_PASSWORD"),

    -- Portals
    client_portal_url = os.getenv("CLIENT_PORTAL_URL") or "http://client-portal:8080",
    indirect_portal_url = os.getenv("INDIRECT_PORTAL_URL") or "http://indirect-client-portal:8080"
}
```

### 3.7 Dockerfile

```dockerfile
FROM openresty/openresty:1.25.3.1-alpine

RUN apk add --no-cache curl openssl perl \
    && opm get ledgetech/lua-resty-http

COPY nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
COPY conf.d/ /etc/nginx/conf.d/
COPY lua/ /usr/local/openresty/nginx/lua/
COPY login/ /usr/local/openresty/nginx/html/login/

EXPOSE 80

CMD ["/usr/local/openresty/bin/openresty", "-g", "daemon off;"]
```

---

## Phase 4: client-portal Migration

### 4.1 Create pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.knight</groupId>
        <artifactId>commercial-platform</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>client-portal</artifactId>
    <name>Client Portal</name>
    <description>Vaadin UI for direct clients</description>

    <properties>
        <vaadin.version>24.9.7</vaadin.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.vaadin</groupId>
                <artifactId>vaadin-bom</artifactId>
                <version>${vaadin.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Vaadin -->
        <dependency>
            <groupId>com.vaadin</groupId>
            <artifactId>vaadin-spring-boot-starter</artifactId>
        </dependency>

        <!-- Spring Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- JWT Validation -->
        <dependency>
            <groupId>com.auth0</groupId>
            <artifactId>java-jwt</artifactId>
            <version>4.4.0</version>
        </dependency>
        <dependency>
            <groupId>com.auth0</groupId>
            <artifactId>jwks-rsa</artifactId>
            <version>0.22.1</version>
        </dependency>

        <!-- Actuator -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.github.mvysny.kaributesting</groupId>
            <artifactId>karibu-testing-v24</artifactId>
            <version>2.1.6</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>com.vaadin</groupId>
                <artifactId>vaadin-maven-plugin</artifactId>
                <version>${vaadin.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-frontend</goal>
                            <goal>build-frontend</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

    <profiles>
        <profile>
            <id>production</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>com.vaadin</groupId>
                        <artifactId>vaadin-maven-plugin</artifactId>
                        <configuration>
                            <productionMode>true</productionMode>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

### 4.2 Configuration

```yaml
server:
  port: 8080
  forward-headers-strategy: framework

spring:
  application:
    name: client-portal

api:
  base-url: ${API_URL:http://platform:8080/api/v1/client}

jwt:
  issuer: https://${AUTH0_DOMAIN}/
  audience: ${AUTH0_API_AUDIENCE}
  jwks-url: https://${AUTH0_DOMAIN}/.well-known/jwks.json

vaadin:
  launch-browser: false

management:
  endpoints:
    web:
      exposure:
        include: health, info
```

### 4.3 Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dvaadin.productionMode=true", "-jar", "app.jar"]
```

---

## Phase 5: indirect-client-portal Setup

### 5.1 Structure

Same as `client-portal` but configured for `/api/v1/indirect/*`:

```yaml
api:
  base-url: ${API_URL:http://platform:8080/api/v1/indirect}
```

### 5.2 Views

- `DashboardView` - Indirect client overview
- `OfiAccountsView` - View/manage OFI accounts
- `UsersView` - Manage portal users and their permission policies
- `ApprovalsView` - Approve outstanding items based on user permissions (placeholder)

> **Note**: Related persons are managed by direct clients via the `client-portal`, not by indirect clients. Permission policies are displayed within the UsersView rather than as a separate view.

---

## Phase 6: Docker Compose Updates

### 6.1 Add New Services

Add to `docker-compose.yml`:

```yaml
services:
  # ... existing services (gateway, portal, platform, sqlserver, redis, kafka) ...

  # Client Login Gateway (OpenResty + Lua)
  client-login:
    build: ./client-login
    container_name: knight-client-login
    ports:
      - "9000:80"
    environment:
      - AUTH0_DOMAIN=${AUTH0_DOMAIN}
      - AUTH0_WEB_CLIENT_ID=${AUTH0_WEB_CLIENT_ID}
      - AUTH0_WEB_CLIENT_SECRET=${AUTH0_WEB_CLIENT_SECRET}
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - CLIENT_SESSION_TTL=1200
      - CLIENT_SESSION_COOKIE=CLIENT_SESSION
      - PLATFORM_API_URL=http://platform:8080
      - LOGIN_API_USERNAME=${LOGIN_API_USERNAME}
      - LOGIN_API_PASSWORD=${LOGIN_API_PASSWORD}
      - CLIENT_PORTAL_URL=http://client-portal:8080
      - INDIRECT_PORTAL_URL=http://indirect-client-portal:8080
    depends_on:
      redis:
        condition: service_healthy
      platform:
        condition: service_started
      client-portal:
        condition: service_started
      indirect-client-portal:
        condition: service_started
    networks:
      - knight-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost/health"]
      interval: 10s
      timeout: 5s
      retries: 3

  # Client Portal (Vaadin UI for Direct Clients)
  client-portal:
    build: ./client-portal
    container_name: knight-client-portal
    ports:
      - "8003:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - API_URL=http://platform:8080/api/v1/client
      - AUTH0_DOMAIN=${AUTH0_DOMAIN}
      - AUTH0_API_AUDIENCE=${AUTH0_API_AUDIENCE}
    depends_on:
      platform:
        condition: service_started
    networks:
      - knight-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3

  # Indirect Client Portal (Vaadin UI for Indirect Clients)
  indirect-client-portal:
    build: ./indirect-client-portal
    container_name: knight-indirect-portal
    ports:
      - "8004:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - API_URL=http://platform:8080/api/v1/indirect
      - AUTH0_DOMAIN=${AUTH0_DOMAIN}
      - AUTH0_API_AUDIENCE=${AUTH0_API_AUDIENCE}
    depends_on:
      platform:
        condition: service_started
    networks:
      - knight-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3
```

### 6.2 Port Assignments

| Service | Internal Port | External Port | Purpose |
|---------|---------------|---------------|---------|
| gateway (Entra ID) | 80 | 8000 | Employee authentication |
| portal (Employee) | 8081 | 8001 | Employee Vaadin UI |
| platform (API) | 8080 | 8002 | Platform REST API (includes /api/login/*) |
| client-login | 80 | 9000 | Client Auth0 gateway |
| client-portal | 8080 | 8003 | Direct client Vaadin UI |
| indirect-client-portal | 8080 | 8004 | Indirect client Vaadin UI |
| sqlserver | 1433 | 1433 | Database |
| redis | 6379 | 6379 | Session storage |
| kafka | 9092/9094 | 9092/9094 | Event streaming |

---

## Phase 7: Environment Variables

### 7.1 Add to .env

```bash
# ========================================
# Client Auth0 Configuration (Web App)
# ========================================
AUTH0_WEB_CLIENT_ID=your-web-client-id
AUTH0_WEB_CLIENT_SECRET=your-web-client-secret

# ========================================
# Login API Basic Auth (client-login → platform)
# ========================================
LOGIN_API_USERNAME=gateway
LOGIN_API_PASSWORD=your-secure-password

# ========================================
# Client Session Configuration
# ========================================
CLIENT_SESSION_TTL=1200
CLIENT_SESSION_COOKIE=CLIENT_SESSION

# ========================================
# Password Reset URL (for client onboarding)
# ========================================
AUTH0_PASSWORD_RESET_URL=http://localhost:9000/
```

### 7.2 Update .env.example

Add same variables with placeholder values for documentation.

---

## Phase 8: Coverage Report Updates

### 8.1 Update coverage-report/pom.xml

Add new modules as dependencies:

```xml
<dependencies>
    <!-- Existing modules -->
    <dependency>
        <groupId>com.knight</groupId>
        <artifactId>application</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.knight.platform</groupId>
        <artifactId>kernel</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.knight</groupId>
        <artifactId>employee-portal</artifactId>
        <version>${project.version}</version>
    </dependency>
    <!-- Domain modules... -->

    <!-- NEW: Client portals -->
    <dependency>
        <groupId>com.knight</groupId>
        <artifactId>client-portal</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.knight</groupId>
        <artifactId>indirect-client-portal</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

> **Note**: No `client-auth-service` - auth code is now in `application` module and already covered.

---

## Phase 9: Build Script Updates

### 9.1 Update build.sh

```bash
#!/bin/bash

echo "Building Knight2 Platform..."

# Build all Maven modules
mvn clean package -DskipTests -Pproduction

# Stop existing containers
docker-compose down

# Rebuild all containers
docker-compose build --no-cache \
    platform \
    portal \
    client-login \
    client-portal \
    indirect-client-portal

# Start all services
docker-compose up -d

echo ""
echo "Services available at:"
echo "  Employee Gateway:       http://localhost:8000"
echo "  Employee Portal:        http://localhost:8001"
echo "  Platform API:           http://localhost:8002"
echo "  Client Login:           http://localhost:9000"
echo "  Client Portal:          http://localhost:8003"
echo "  Indirect Client Portal: http://localhost:8004"
```

---

## Migration Checklist

### Phase 1: Module Structure
- [ ] Update root `pom.xml` with new modules (client-portal, indirect-client-portal)
- [ ] Create `client-login/` directory structure
- [ ] Create `client-portal/` directory structure
- [ ] Create `indirect-client-portal/` directory structure

### Phase 2: Merge auth-service into Application
- [ ] Create `rest/login/` package with controllers
- [ ] Create `service/auth0/` package with Auth0 adapter
- [ ] Create `security/login/` package with Basic Auth config
- [ ] Add `/api/login/*` endpoints
- [ ] Add `UserCheckResponse` with `clientType` field
- [ ] Update `application.yml` with new configuration
- [ ] Test Basic Auth security for `/api/login/*`

### Phase 3: client-login
- [ ] Copy from `okta-app/nginx`
- [ ] Update `conf.d/default.conf` for new routing
- [ ] Create `portal_router.lua` for client type routing
- [ ] Update `api_proxy.lua` for Basic Auth
- [ ] Update `oauth_callback.lua` to store client type
- [ ] Update `config.lua` for new environment variables
- [ ] Create `Dockerfile`
- [ ] Test authentication flow with portal routing

### Phase 4: client-portal
- [ ] Copy source from `okta-app/vaadin-app`
- [ ] Update package names to `com.knight.clientportal`
- [ ] Create `pom.xml`
- [ ] Configure to call `/api/v1/client/*`
- [ ] Create `Dockerfile`
- [ ] Add client-specific views

### Phase 5: indirect-client-portal
- [ ] Create based on `client-portal` template
- [ ] Configure for `/api/v1/indirect/*`
- [ ] Create indirect-client-specific views
- [ ] Create `Dockerfile`

### Phase 6: Docker Compose
- [ ] Add `client-login` service
- [ ] Add `client-portal` service
- [ ] Add `indirect-client-portal` service
- [ ] Configure networking
- [ ] Add health checks
- [ ] Test service dependencies

### Phase 7: Environment
- [ ] Update `.env` with new variables
- [ ] Update `.env.example`
- [ ] Document required Auth0 configuration

### Phase 8: Coverage
- [ ] Update `coverage-report/pom.xml`
- [ ] Verify aggregated coverage includes new portal modules

### Phase 9: Testing
- [ ] Run full build
- [ ] Test employee portal still works
- [ ] Test client login flow
- [ ] Verify portal routing based on client type
- [ ] Test client portal functionality
- [ ] Test indirect client portal functionality
- [ ] Verify API access controls

---

## Authentication Flow Summary

```
1. User visits http://localhost:9000/login
2. Enters email address
3. client-login calls POST /api/login/user/check (Basic Auth)
4. Platform API returns:
   - exists: true/false
   - clientType: CLIENT | INDIRECT_CLIENT
   - mfaEnrolled: true/false
5. client-login redirects to Auth0 Universal Login (OAuth2 authorization code flow)
6. User authenticates with Auth0 (username/password, MFA if enrolled)
7. Auth0 redirects back to /oauth2/callback with authorization code
8. oauth_callback.lua:
   - Exchanges code for tokens via POST /api/login/auth/token
   - Creates Redis session with client_type (from step 4)
   - Sets CLIENT_SESSION cookie
9. Subsequent requests:
   - auth.lua validates session
   - portal_router.lua routes based on client_type:
     - CLIENT → client-portal:8080
     - INDIRECT_CLIENT → indirect-client-portal:8080
10. Portal makes API calls with Auth0 JWT:
    - client-portal → /api/v1/client/* (@ClientAccess)
    - indirect-client-portal → /api/v1/indirect/* (@IndirectClientAccess)
```

---

## Future Considerations

### Shared UI Components
Consider extracting shared Vaadin components between `employee-portal`, `client-portal`, and `indirect-client-portal` into a shared module.

### API Gateway Consolidation
Long-term, consider consolidating authentication gateways using Spring Cloud Gateway or similar, replacing the Lua-based OpenResty setup.
