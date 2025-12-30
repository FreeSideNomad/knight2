# Authorization Refactoring Plan

## Problem Statement

Currently, issuer validation is done imperatively at the start of every controller method:

```java
@GetMapping("/clients/{clientId}")
public ResponseEntity<ClientDetailDto> getClient(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable String clientId) {

    issuerValidator.rejectAuth0(jwt);  // Repeated in ~90 methods
    // ... business logic
}
```

Issues:
1. **Boilerplate**: Same validation call repeated ~90 times across 3 controllers
2. **Unclear semantics**: `rejectAuth0(jwt)` is confusing - it means "allow Entra/Portal, reject Auth0"
3. **Error-prone**: Easy to forget adding the validation call to new methods
4. **Not declarative**: Access policy is buried in method body, not visible at a glance

## Proposed Solution

Replace imperative validation with declarative class-level annotations enforced via Spring AOP.

### New Annotations

| Annotation | Current Allowed Issuers | Future Issuers | Use Case |
|------------|-------------------------|----------------|----------|
| `@BankAccess` | Entra ID, Employee Portal | - | Bank admin endpoints (employee UI) |
| `@ClientAccess` | Auth0 | Auth0 + ANP | Direct client BFF endpoints |
| `@IndirectClientAccess` | Auth0 | Auth0 (no ANP) | Indirect client BFF endpoints |

> **Note**: `@ClientAccess` and `@IndirectClientAccess` currently have the same policy (Auth0 only),
> but are kept separate because Direct Clients will need ANP token support in the future,
> while Indirect Clients will remain Auth0-only.

### Usage

**Class-level annotation only** (controllers are per-UI, no mixing):

```java
@RestController
@RequestMapping("/api/v1/bank")
@BankAccess  // All methods require Entra/Portal JWT
public class BankAdminController {

    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ClientDetailDto> getClient(
            @PathVariable String clientId) {
        // No @AuthenticationPrincipal needed - aspect handles validation
        // No manual validation needed
        // ... business logic
    }

    @GetMapping("/audit-log")
    public ResponseEntity<AuditLog> getAuditLog(
            @AuthenticationPrincipal Jwt jwt) {  // Keep only if method needs JWT claims
        String userEmail = jwt.getClaim("email");
        // ... use claims
    }
}
```

```java
@RestController
@RequestMapping("/api/v1/client")
@ClientAccess  // All methods require Auth0 (future: Auth0 or ANP)
public class DirectClientController {
    // ...
}
```

```java
@RestController
@RequestMapping("/api/v1/indirect")
@IndirectClientAccess  // All methods require Auth0
public class IndirectClientBffController {
    // ...
}
```

> **Note**: `@AuthenticationPrincipal Jwt jwt` can be removed from method signatures.
> The aspect gets the JWT from `SecurityContextHolder` (populated by Spring Security filters before the controller is called).
> Only keep `@AuthenticationPrincipal Jwt jwt` in methods that actually use JWT claims (e.g., `jwt.getSubject()`, `jwt.getClaim("email")`).

## Implementation Details

### 1. Annotation Definitions

```java
package com.knight.application.security.access;

@Target(ElementType.TYPE)  // Class-level only
@Retention(RetentionPolicy.RUNTIME)
public @interface BankAccess { }

@Target(ElementType.TYPE)  // Class-level only
@Retention(RetentionPolicy.RUNTIME)
public @interface ClientAccess { }

@Target(ElementType.TYPE)  // Class-level only
@Retention(RetentionPolicy.RUNTIME)
public @interface IndirectClientAccess { }
```

### 2. AOP Aspect

```java
package com.knight.application.security.access;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // Run before other aspects
public class AccessPolicyAspect {

    private final IssuerValidator issuerValidator;

    @Before("@within(bankAccess)")
    public void enforceBankAccess(BankAccess bankAccess) {
        Jwt jwt = extractJwtFromSecurityContext();
        issuerValidator.requireBankIssuer(jwt);
    }

    @Before("@within(clientAccess)")
    public void enforceClientAccess(ClientAccess clientAccess) {
        Jwt jwt = extractJwtFromSecurityContext();
        issuerValidator.requireClientIssuer(jwt);
    }

    @Before("@within(indirectClientAccess)")
    public void enforceIndirectClientAccess(IndirectClientAccess indirectClientAccess) {
        Jwt jwt = extractJwtFromSecurityContext();
        issuerValidator.requireIndirectClientIssuer(jwt);
    }

    private Jwt extractJwtFromSecurityContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;  // Security disabled or non-JWT auth
    }
}
```

### 3. Refactored IssuerValidator

Three distinct methods with **explicit positive checks** (whitelist allowed issuers, not blacklist):

```java
@Component
public class IssuerValidator {

    /**
     * Require a bank employee issuer (Entra ID or Portal).
     * Used by: BankAdminController
     */
    public void requireBankIssuer(Jwt jwt) {
        if (jwt == null) return;  // Security disabled
        if (!isEntraToken(jwt) && !isPortalToken(jwt)) {
            throw new ForbiddenException("Bank endpoints require Entra ID or Portal authentication");
        }
    }

    /**
     * Require a direct client issuer (Auth0, future: ANP).
     * Used by: DirectClientController
     */
    public void requireClientIssuer(Jwt jwt) {
        if (jwt == null) return;  // Security disabled
        if (!isAuth0Token(jwt)) {  // Future: && !isAnpToken(jwt)
            throw new ForbiddenException("Client endpoints require Auth0 authentication");
        }
    }

    /**
     * Require an indirect client issuer (Auth0 only, no ANP).
     * Used by: IndirectClientBffController
     */
    public void requireIndirectClientIssuer(Jwt jwt) {
        if (jwt == null) return;  // Security disabled
        if (!isAuth0Token(jwt)) {
            throw new ForbiddenException("Indirect client endpoints require Auth0 authentication");
        }
    }

    // Helper methods remain unchanged
    public boolean isAuth0Token(Jwt jwt) { ... }
    public boolean isEntraToken(Jwt jwt) { ... }
    public boolean isPortalToken(Jwt jwt) { ... }
    // Future: public boolean isAnpToken(Jwt jwt) { ... }
}
```

## Files to Modify

### New Files
- `application/src/main/java/com/knight/application/security/access/BankAccess.java`
- `application/src/main/java/com/knight/application/security/access/ClientAccess.java`
- `application/src/main/java/com/knight/application/security/access/IndirectClientAccess.java`
- `application/src/main/java/com/knight/application/security/access/AccessPolicyAspect.java`

### Modified Files
- `application/src/main/java/com/knight/application/security/IssuerValidator.java` - Add new methods, deprecate old
- `application/src/main/java/com/knight/application/rest/bank/BankAdminController.java` - Add `@BankAccess`, remove ~40 `issuerValidator.rejectAuth0(jwt)` calls, remove `IssuerValidator` field
- `application/src/main/java/com/knight/application/rest/client/DirectClientController.java` - Add `@ClientAccess`, remove ~27 `issuerValidator.requireAuth0(jwt)` calls, remove `IssuerValidator` field
- `application/src/main/java/com/knight/application/rest/indirect/IndirectClientBffController.java` - Add `@IndirectClientAccess`, remove ~12 `issuerValidator.requireAuth0(jwt)` calls, remove `IssuerValidator` field
- `application/src/test/java/com/knight/application/security/IssuerValidatorTest.java` - Add tests for new methods

### Dependencies
- Need `spring-boot-starter-aop` in `application/pom.xml` (may already be transitive)

## Migration Steps

1. Add AOP dependency if needed
2. Create the three annotation classes (class-level only)
3. Create `AccessPolicyAspect`
4. Add new methods to `IssuerValidator` (`requireBankIssuer`, `requireClientIssuer`, `requireIndirectClientIssuer`)
5. Add class-level annotations to controllers
6. Remove individual `issuerValidator.*` calls from controller methods
7. Remove `IssuerValidator` field injection from controllers
8. Update/add tests for the aspect and new validator methods
9. Remove old deprecated methods from `IssuerValidator` (`rejectAuth0`, `requireAuth0`)

## Testing Strategy

1. **Unit test `IssuerValidator`**: Test each `require*` method with valid/invalid tokens
2. **Unit test `AccessPolicyAspect`**: Mock `IssuerValidator`, verify correct method called based on annotation
3. **Integration tests**: Existing E2E tests should continue to pass
4. **Verify rejection**: Test that wrong issuer type gets 403 Forbidden

## Future: ANP Token Support

When ANP is added for direct clients:

```java
public void requireClientIssuer(Jwt jwt) {
    if (jwt == null) return;
    if (!isAuth0Token(jwt) && !isAnpToken(jwt)) {
        throw new ForbiddenException("Client endpoints require Auth0 or ANP authentication");
    }
}

public boolean isAnpToken(Jwt jwt) {
    if (jwt == null || jwt.getIssuer() == null) return false;
    String issuer = jwt.getIssuer().toString();
    String anpIssuer = jwtProperties.getAnp().getIssuerUri();
    return anpIssuer != null && issuer.equals(anpIssuer);
}
```

Only `requireClientIssuer` changes - `requireIndirectClientIssuer` remains Auth0-only.

## Comparison

| Aspect | Current (Imperative) | Proposed (Declarative) |
|--------|---------------------|----------------------|
| Visibility | Hidden in method body | Visible on class |
| Boilerplate | ~90 validation calls | 3 annotations |
| Consistency | Manual, error-prone | Automatic via AOP |
| Testability | Must test each method | Test aspect once |
| Scope | Per-method | Per-controller (class) |
| Future-proof | Manual updates everywhere | Update one validator method |