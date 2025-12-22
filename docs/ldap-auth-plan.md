# LDAP Authentication Plan for Employee Portal

## Overview

This document outlines the plan to replace the current nginx gateway JWT-based authentication with LDAP authentication performed directly by the Employee Portal. The solution supports two deployment profiles:

1. **Local Development/Testing**: Embedded LDAP server using UnboundID
2. **Enterprise (Test/Prod)**: Microsoft Active Directory via LDAP

## Current State

The Employee Portal currently uses:
- **Security Framework**: Spring Security 6.x with Vaadin 24.5.8 integration
- **Authentication**: JWT tokens validated via `JwtAuthenticationFilter`
- **Session**: `VaadinWebSecurity` base class with `SessionCreationPolicy.IF_REQUIRED`
- **Gateway**: Nginx proxy handling authentication upstream

### Current Files to Replace/Modify
- `SecurityConfiguration.java` - Current JWT-based security config
- `JwtAuthenticationFilter.java` - JWT validation filter (to be removed)
- `JwtValidator.java` - JWT validation logic (to be removed)
- `JwtAuthenticationToken.java` - Custom auth token (to be replaced)
- `LoginView.java` - OAuth2/Azure redirect (to become LDAP login form)
- `SecurityService.java` - User info accessor (to be adapted)
- `UserInfo.java` - User model (to be adapted for LDAP attributes)

## Technology Stack

### Core Dependencies (Latest Versions - December 2024)

```xml
<!-- Spring Boot 3.5.8 (current) with Spring Security 6.x -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Spring Security LDAP -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-ldap</artifactId>
    <version>6.4.2</version>
</dependency>

<!-- Spring Data LDAP (optional, for user operations) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-ldap</artifactId>
</dependency>

<!-- UnboundID for embedded LDAP (local/testing) -->
<dependency>
    <groupId>com.unboundid</groupId>
    <artifactId>unboundid-ldapsdk</artifactId>
    <version>7.0.3</version>
</dependency>
```

### Key Library Notes

1. **Spring Security 7 removes Apache DS support** - UnboundID is the recommended embedded LDAP server
2. **VaadinWebSecurity is deprecated in Vaadin 24.9+** - Use `VaadinSecurityConfigurer` instead
3. **Use bean-based configuration** - `WebSecurityConfigurerAdapter` is deprecated

## Architecture

### Profile-Based Configuration

```
employee-portal/
├── src/main/java/com/knight/portal/
│   ├── config/
│   │   └── SecurityConfiguration.java         # Base security config
│   ├── security/
│   │   ├── ldap/
│   │   │   ├── LdapSecurityConfig.java        # LDAP authentication config
│   │   │   ├── LdapUserDetailsMapper.java     # Map LDAP to UserDetails
│   │   │   ├── EmbeddedLdapConfig.java        # Local embedded LDAP (@Profile("local"))
│   │   │   └── ActiveDirectoryConfig.java     # AD config (@Profile("!local"))
│   │   ├── LdapAuthenticatedUser.java         # User principal after LDAP auth
│   │   └── SecurityService.java               # Adapted for LDAP
│   ├── model/
│   │   └── UserInfo.java                      # Adapted for LDAP attributes
│   └── views/
│       └── LoginView.java                     # Form-based LDAP login
└── src/main/resources/
    ├── application.yml                        # Base config
    ├── application-local.yml                  # Embedded LDAP settings
    ├── application-test.yml                   # Test LDAP settings
    ├── application-prod.yml                   # AD LDAP settings
    └── ldap/
        └── test-users.ldif                    # Test data for embedded LDAP
```

## Implementation Plan

### Phase 1: Add LDAP Dependencies

Update `employee-portal/pom.xml`:

```xml
<!-- Spring Security LDAP -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-ldap</artifactId>
</dependency>

<!-- Spring LDAP Core -->
<dependency>
    <groupId>org.springframework.ldap</groupId>
    <artifactId>spring-ldap-core</artifactId>
</dependency>

<!-- UnboundID for embedded LDAP (local development) -->
<dependency>
    <groupId>com.unboundid</groupId>
    <artifactId>unboundid-ldapsdk</artifactId>
    <version>7.0.3</version>
</dependency>

<!-- Spring LDAP Test -->
<dependency>
    <groupId>org.springframework.ldap</groupId>
    <artifactId>spring-ldap-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Phase 2: Create LDAP Configuration Classes

#### 2.1 Base LDAP Security Configuration

```java
@Configuration
@EnableWebSecurity
public class LdapSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            AuthenticationManager authManager) throws Exception {

        http.apply(new VaadinSecurityConfigurer());

        http
            .authenticationManager(authManager)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/login").permitAll()
                .requestMatchers("/VAADIN/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login")
                .permitAll()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .csrf(csrf -> csrf.disable()); // Vaadin handles CSRF

        return http.build();
    }
}
```

#### 2.2 Embedded LDAP Configuration (Local Profile)

```java
@Configuration
@Profile("local")
public class EmbeddedLdapConfig {

    @Value("${ldap.embedded.port:8389}")
    private int ldapPort;

    @Value("${ldap.base-dn:dc=knight,dc=com}")
    private String baseDn;

    @Bean
    public EmbeddedLdapServer embeddedLdapServer() {
        return EmbeddedLdapServer.withPartitionSuffix(baseDn)
            .partitionName("knight")
            .port(ldapPort)
            .ldif("classpath:ldap/test-users.ldif")
            .build();
    }

    @Bean
    public BaseLdapPathContextSource contextSource() {
        return new DefaultSpringSecurityContextSource(
            "ldap://localhost:" + ldapPort,
            baseDn
        );
    }

    @Bean
    public AuthenticationManager authenticationManager(
            BaseLdapPathContextSource contextSource) {
        LdapBindAuthenticationManagerFactory factory =
            new LdapBindAuthenticationManagerFactory(contextSource);
        factory.setUserDnPatterns("uid={0},ou=people");
        factory.setUserDetailsContextMapper(ldapUserDetailsMapper());
        factory.setLdapAuthoritiesPopulator(ldapAuthoritiesPopulator(contextSource));
        return factory.createAuthenticationManager();
    }

    @Bean
    public LdapAuthoritiesPopulator ldapAuthoritiesPopulator(
            BaseLdapPathContextSource contextSource) {
        DefaultLdapAuthoritiesPopulator populator =
            new DefaultLdapAuthoritiesPopulator(contextSource, "ou=groups");
        populator.setGroupSearchFilter("(member={0})");
        populator.setRolePrefix("ROLE_");
        populator.setConvertToUpperCase(true);
        return populator;
    }

    @Bean
    public UserDetailsContextMapper ldapUserDetailsMapper() {
        return new LdapUserDetailsMapper();
    }
}
```

#### 2.3 Active Directory Configuration (Enterprise Profile)

```java
@Configuration
@Profile("!local")
public class ActiveDirectoryConfig {

    @Value("${ldap.ad.domain}")
    private String adDomain;

    @Value("${ldap.ad.url}")
    private String adUrl;

    @Value("${ldap.ad.root-dn:}")
    private String rootDn;

    @Value("${ldap.ad.search-filter:(sAMAccountName={0})}")
    private String searchFilter;

    @Bean
    public ActiveDirectoryLdapAuthenticationProvider adAuthProvider() {
        ActiveDirectoryLdapAuthenticationProvider provider =
            new ActiveDirectoryLdapAuthenticationProvider(adDomain, adUrl, rootDn);

        provider.setSearchFilter(searchFilter);
        provider.setConvertSubErrorCodesToExceptions(true);
        provider.setUseAuthenticationRequestCredentials(true);
        provider.setUserDetailsContextMapper(ldapUserDetailsMapper());

        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            ActiveDirectoryLdapAuthenticationProvider adAuthProvider) {
        return new ProviderManager(adAuthProvider);
    }

    @Bean
    public UserDetailsContextMapper ldapUserDetailsMapper() {
        return new LdapUserDetailsMapper();
    }
}
```

#### 2.4 Custom User Details Mapper

```java
@Component
public class LdapUserDetailsMapper implements UserDetailsContextMapper {

    @Override
    public UserDetails mapUserFromContext(DirContextOperations ctx,
            String username,
            Collection<? extends GrantedAuthority> authorities) {

        String email = ctx.getStringAttribute("mail");
        String displayName = ctx.getStringAttribute("cn");
        String firstName = ctx.getStringAttribute("givenName");
        String lastName = ctx.getStringAttribute("sn");
        String employeeId = ctx.getStringAttribute("employeeNumber");
        String department = ctx.getStringAttribute("department");

        // For AD, also check these attributes
        String userPrincipalName = ctx.getStringAttribute("userPrincipalName");
        String sAMAccountName = ctx.getStringAttribute("sAMAccountName");

        return LdapAuthenticatedUser.builder()
            .username(username)
            .email(email != null ? email : userPrincipalName)
            .displayName(displayName)
            .firstName(firstName)
            .lastName(lastName)
            .employeeId(employeeId)
            .department(department)
            .authorities(authorities)
            .build();
    }

    @Override
    public void mapUserToContext(UserDetails user, DirContextAdapter ctx) {
        // Not needed for authentication
    }
}
```

### Phase 3: Create Login View with Form-Based Authentication

```java
@Route("login")
@PageTitle("Login | Knight Employee Portal")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final TextField username = new TextField("Username");
    private final PasswordField password = new PasswordField("Password");
    private final Button loginButton = new Button("Sign In");
    private final Div errorMessage = new Div();

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H1 title = new H1("Knight Employee Portal");

        FormLayout loginForm = new FormLayout();
        loginForm.add(username, password);
        loginForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1)
        );

        username.setRequired(true);
        username.setAutofocus(true);
        password.setRequired(true);

        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        loginButton.setWidthFull();
        loginButton.addClickShortcut(Key.ENTER);

        errorMessage.addClassName("error-message");
        errorMessage.setVisible(false);

        // Submit form via POST to Spring Security
        loginButton.addClickListener(e -> {
            // Form will be submitted via JavaScript to /login
            UI.getCurrent().getPage().executeJs(
                "document.getElementById('login-form').submit()"
            );
        });

        VerticalLayout container = new VerticalLayout(
            title, errorMessage, loginForm, loginButton
        );
        container.setAlignItems(Alignment.CENTER);
        container.setMaxWidth("400px");

        add(container);

        // Add hidden form for Spring Security
        addHiddenLoginForm();
    }

    private void addHiddenLoginForm() {
        getElement().executeJs("""
            const form = document.createElement('form');
            form.id = 'login-form';
            form.method = 'POST';
            form.action = '/login';
            form.style.display = 'none';

            const usernameInput = document.createElement('input');
            usernameInput.name = 'username';
            usernameInput.id = 'form-username';
            form.appendChild(usernameInput);

            const passwordInput = document.createElement('input');
            passwordInput.type = 'password';
            passwordInput.name = 'password';
            passwordInput.id = 'form-password';
            form.appendChild(passwordInput);

            document.body.appendChild(form);
        """);

        // Sync Vaadin fields to hidden form
        username.addValueChangeListener(e ->
            getElement().executeJs(
                "document.getElementById('form-username').value = $0",
                e.getValue()
            )
        );
        password.addValueChangeListener(e ->
            getElement().executeJs(
                "document.getElementById('form-password').value = $0",
                e.getValue()
            )
        );
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Check for login error
        if (event.getLocation().getQueryParameters()
                .getParameters().containsKey("error")) {
            errorMessage.setText("Invalid username or password");
            errorMessage.setVisible(true);
        }
    }
}
```

### Phase 4: Create Test LDIF Data

Create `src/main/resources/ldap/test-users.ldif`:

```ldif
# Root entry
dn: dc=knight,dc=com
objectclass: top
objectclass: dcObject
objectclass: organization
o: Knight Corporation
dc: knight

# People organizational unit
dn: ou=people,dc=knight,dc=com
objectclass: top
objectclass: organizationalUnit
ou: people

# Groups organizational unit
dn: ou=groups,dc=knight,dc=com
objectclass: top
objectclass: organizationalUnit
ou: groups

# Admin user
dn: uid=admin,ou=people,dc=knight,dc=com
objectclass: top
objectclass: person
objectclass: organizationalPerson
objectclass: inetOrgPerson
cn: Admin User
sn: User
givenName: Admin
uid: admin
mail: admin@knight.com
userPassword: admin123
employeeNumber: EMP001
department: IT

# Regular user
dn: uid=john.doe,ou=people,dc=knight,dc=com
objectclass: top
objectclass: person
objectclass: organizationalPerson
objectclass: inetOrgPerson
cn: John Doe
sn: Doe
givenName: John
uid: john.doe
mail: john.doe@knight.com
userPassword: password123
employeeNumber: EMP002
department: Operations

# Manager user
dn: uid=jane.smith,ou=people,dc=knight,dc=com
objectclass: top
objectclass: person
objectclass: organizationalPerson
objectclass: inetOrgPerson
cn: Jane Smith
sn: Smith
givenName: Jane
uid: jane.smith
mail: jane.smith@knight.com
userPassword: password123
employeeNumber: EMP003
department: Management

# Admin group
dn: cn=admins,ou=groups,dc=knight,dc=com
objectclass: top
objectclass: groupOfNames
cn: admins
member: uid=admin,ou=people,dc=knight,dc=com

# Users group
dn: cn=users,ou=groups,dc=knight,dc=com
objectclass: top
objectclass: groupOfNames
cn: users
member: uid=admin,ou=people,dc=knight,dc=com
member: uid=john.doe,ou=people,dc=knight,dc=com
member: uid=jane.smith,ou=people,dc=knight,dc=com

# Managers group
dn: cn=managers,ou=groups,dc=knight,dc=com
objectclass: top
objectclass: groupOfNames
cn: managers
member: uid=jane.smith,ou=people,dc=knight,dc=com
```

### Phase 5: Application Configuration

#### 5.1 Base Configuration (`application.yml`)

```yaml
spring:
  profiles:
    active: local  # Default to local for development

ldap:
  base-dn: dc=knight,dc=com
```

#### 5.2 Local Profile (`application-local.yml`)

```yaml
ldap:
  embedded:
    enabled: true
    port: 8389
    ldif: classpath:ldap/test-users.ldif
  base-dn: dc=knight,dc=com
  user-dn-patterns: uid={0},ou=people
  group-search-base: ou=groups
```

#### 5.3 Test/Prod Profile (`application-prod.yml`)

```yaml
ldap:
  embedded:
    enabled: false
  ad:
    domain: ${AD_DOMAIN}           # e.g., corp.knight.com
    url: ${AD_LDAP_URL}            # e.g., ldap://ad.corp.knight.com:389
    root-dn: ${AD_ROOT_DN}         # e.g., dc=corp,dc=knight,dc=com
    search-filter: (sAMAccountName={0})
```

### Phase 6: Adapt User Model

Update `UserInfo.java` to work with LDAP attributes:

```java
public record UserInfo(
    String username,
    String email,
    String displayName,
    String firstName,
    String lastName,
    String employeeId,
    String department,
    Collection<String> roles
) {
    public String getInitials() {
        if (firstName != null && lastName != null) {
            return (firstName.substring(0, 1) + lastName.substring(0, 1)).toUpperCase();
        }
        if (displayName != null && !displayName.isBlank()) {
            String[] parts = displayName.split("\\s+");
            if (parts.length >= 2) {
                return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
            }
            return displayName.substring(0, Math.min(2, displayName.length())).toUpperCase();
        }
        return username != null ? username.substring(0, Math.min(2, username.length())).toUpperCase() : "?";
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ROLE_ADMINS");
    }
}
```

### Phase 7: Update Security Service

```java
@Service
public class SecurityService {

    public UserInfo getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof LdapAuthenticatedUser ldapUser) {
            return new UserInfo(
                ldapUser.getUsername(),
                ldapUser.getEmail(),
                ldapUser.getDisplayName(),
                ldapUser.getFirstName(),
                ldapUser.getLastName(),
                ldapUser.getEmployeeId(),
                ldapUser.getDepartment(),
                ldapUser.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet())
            );
        }
        return null;
    }

    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken);
    }

    public void logout() {
        SecurityContextHolder.clearContext();
        UI.getCurrent().getPage().setLocation("/login");
    }
}
```

### Phase 8: Clean Up JWT Components

Remove the following files:
- `JwtAuthenticationFilter.java`
- `JwtValidator.java`
- `JwtAuthenticationToken.java`

Remove from `pom.xml`:
```xml
<!-- Remove these -->
<dependency>
    <groupId>com.auth0</groupId>
    <artifactId>java-jwt</artifactId>
</dependency>
<dependency>
    <groupId>com.auth0</groupId>
    <artifactId>jwks-rsa</artifactId>
</dependency>
```

## Testing Strategy

### Unit Tests

```java
@SpringBootTest
@ActiveProfiles("local")
class LdapAuthenticationTest {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Test
    void shouldAuthenticateValidUser() {
        UsernamePasswordAuthenticationToken token =
            new UsernamePasswordAuthenticationToken("john.doe", "password123");

        Authentication result = authenticationManager.authenticate(token);

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("john.doe");
    }

    @Test
    void shouldRejectInvalidCredentials() {
        UsernamePasswordAuthenticationToken token =
            new UsernamePasswordAuthenticationToken("john.doe", "wrongpassword");

        assertThrows(BadCredentialsException.class, () ->
            authenticationManager.authenticate(token)
        );
    }

    @Test
    void shouldMapUserAttributes() {
        UsernamePasswordAuthenticationToken token =
            new UsernamePasswordAuthenticationToken("john.doe", "password123");

        Authentication result = authenticationManager.authenticate(token);
        LdapAuthenticatedUser user = (LdapAuthenticatedUser) result.getPrincipal();

        assertThat(user.getEmail()).isEqualTo("john.doe@knight.com");
        assertThat(user.getDisplayName()).isEqualTo("John Doe");
        assertThat(user.getDepartment()).isEqualTo("Operations");
    }

    @Test
    void shouldPopulateRolesFromGroups() {
        UsernamePasswordAuthenticationToken token =
            new UsernamePasswordAuthenticationToken("admin", "admin123");

        Authentication result = authenticationManager.authenticate(token);

        assertThat(result.getAuthorities())
            .extracting("authority")
            .contains("ROLE_ADMINS", "ROLE_USERS");
    }
}
```

### Integration Tests with Testcontainers (Optional)

For testing against real Active Directory-like behavior:

```java
@Testcontainers
@SpringBootTest
class ActiveDirectoryIntegrationTest {

    @Container
    static GenericContainer<?> openLdap = new GenericContainer<>("osixia/openldap:1.5.0")
        .withExposedPorts(389)
        .withEnv("LDAP_ORGANISATION", "Knight Corporation")
        .withEnv("LDAP_DOMAIN", "knight.com")
        .withEnv("LDAP_ADMIN_PASSWORD", "admin");

    @DynamicPropertySource
    static void ldapProperties(DynamicPropertyRegistry registry) {
        registry.add("ldap.url", () ->
            "ldap://localhost:" + openLdap.getMappedPort(389));
    }

    // Tests...
}
```

## Migration Checklist

- [ ] Add LDAP dependencies to `pom.xml`
- [ ] Create `LdapSecurityConfig.java`
- [ ] Create `EmbeddedLdapConfig.java` for local profile
- [ ] Create `ActiveDirectoryConfig.java` for prod profile
- [ ] Create `LdapUserDetailsMapper.java`
- [ ] Create `LdapAuthenticatedUser.java`
- [ ] Create `test-users.ldif` test data
- [ ] Update `application.yml` with profile configs
- [ ] Update `LoginView.java` for form-based login
- [ ] Update `UserInfo.java` for LDAP attributes
- [ ] Update `SecurityService.java` for LDAP auth
- [ ] Remove `JwtAuthenticationFilter.java`
- [ ] Remove `JwtValidator.java`
- [ ] Remove `JwtAuthenticationToken.java`
- [ ] Remove JWT dependencies from `pom.xml`
- [ ] Update `MainLayout.java` logout handling
- [ ] Write unit tests
- [ ] Write integration tests
- [ ] Update CI/CD for profile-based deployment
- [ ] Document environment variables for AD configuration

## Environment Variables for Production

| Variable | Description | Example |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active profile | `prod` |
| `AD_DOMAIN` | Active Directory domain | `corp.knight.com` |
| `AD_LDAP_URL` | AD LDAP URL | `ldap://ad.corp.knight.com:389` |
| `AD_ROOT_DN` | Root DN for user search | `dc=corp,dc=knight,dc=com` |

## Security Considerations

1. **LDAPS in Production**: Use `ldaps://` (port 636) for encrypted connections
2. **Service Account**: For AD, may need a service account for bind operations
3. **Password Policy**: AD enforces password policies; handle account lockout gracefully
4. **Session Timeout**: Configure appropriate session timeouts in Vaadin
5. **Audit Logging**: Log authentication attempts for security monitoring

## Rollback Plan

If issues arise:
1. Revert to JWT-based authentication by restoring JWT filter classes
2. Re-enable nginx gateway authentication
3. Set `spring.profiles.active=jwt` (create a JWT profile that uses old config)

## References

- [Spring Security LDAP Reference](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/ldap.html)
- [Spring Security Active Directory](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/ldap.html#_active_directory)
- [Vaadin 24 Security Documentation](https://vaadin.com/docs/latest/flow/security)
- [UnboundID LDAP SDK](https://ldap.com/unboundid-ldap-sdk-for-java/)
- [Spring Boot 3.x LDAP Guide](https://spring.io/guides/gs/authenticating-ldap/)
