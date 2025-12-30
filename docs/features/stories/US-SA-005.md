# US-SA-005: Account Filtering in Permission Check

## Story

**As a** system security component
**I want** permission checks to include service eligibility validation
**So that** users cannot perform actions on accounts that are ineligible for a service, even if they have the permission

## Acceptance Criteria

- [ ] Permission check method accepts both permission URN and ClientAccount
- [ ] Permission validation includes two-step check: user has permission AND account is eligible
- [ ] Service eligibility is determined by calling service.isAccountEligible(account)
- [ ] Permission check returns clear result indicating reason for denial (no permission vs ineligible account)
- [ ] Authorization annotations support account-based filtering
- [ ] API endpoints can enforce permission checks with account context
- [ ] Audit logs capture both permission and eligibility check results
- [ ] Unit tests verify permission granted but account ineligible scenario
- [ ] Integration tests verify end-to-end permission checking with real accounts

## Technical Notes

### Enhanced Permission Check Service

```java
package com.knight.application.security;

@Component
public class PermissionCheckService {

    private final ServiceRegistry serviceRegistry;
    private final PermissionRepository permissionRepository;

    public PermissionCheckResult checkPermission(
            String userId,
            String permissionUrn,
            ClientAccount account) {

        // Step 1: Check if user has the permission
        boolean hasPermission = permissionRepository
            .hasPermission(userId, permissionUrn);

        if (!hasPermission) {
            return PermissionCheckResult.denied(
                PermissionDenialReason.PERMISSION_NOT_GRANTED,
                "User does not have required permission: " + permissionUrn
            );
        }

        // Step 2: Parse URN to get service identifier
        String serviceId = extractServiceId(permissionUrn);

        // Step 3: Check service eligibility for the account
        return serviceRegistry.getService(serviceId)
            .map(service -> {
                if (service.isAccountEligible(account)) {
                    return PermissionCheckResult.allowed();
                } else {
                    return PermissionCheckResult.denied(
                        PermissionDenialReason.ACCOUNT_INELIGIBLE,
                        String.format("Account %s is not eligible for service %s",
                            account.getId(), service.getDisplayName())
                    );
                }
            })
            .orElseGet(() -> PermissionCheckResult.denied(
                PermissionDenialReason.SERVICE_NOT_FOUND,
                "Service not found: " + serviceId
            ));
    }

    private String extractServiceId(String urn) {
        // Parse: urn:knight:service:{serviceId}:action:{actionId}
        String[] parts = urn.split(":");
        if (parts.length >= 4) {
            return parts[3];
        }
        throw new IllegalArgumentException("Invalid permission URN: " + urn);
    }
}
```

### Permission Check Result

```java
package com.knight.application.security;

public record PermissionCheckResult(
    boolean allowed,
    PermissionDenialReason denialReason,
    String denialMessage
) {
    public static PermissionCheckResult allowed() {
        return new PermissionCheckResult(true, null, null);
    }

    public static PermissionCheckResult denied(
            PermissionDenialReason reason,
            String message) {
        return new PermissionCheckResult(false, reason, message);
    }

    public void throwIfDenied() {
        if (!allowed) {
            throw new PermissionDeniedException(denialReason, denialMessage);
        }
    }
}

public enum PermissionDenialReason {
    PERMISSION_NOT_GRANTED,
    ACCOUNT_INELIGIBLE,
    SERVICE_NOT_FOUND,
    ACCOUNT_NOT_FOUND
}
```

### Custom Authorization Annotation

```java
package com.knight.application.security;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireServicePermission {
    String value(); // Permission URN or action identifier
    String accountParam() default "accountId"; // Parameter name containing account ID
}
```

### Aspect for Authorization

```java
package com.knight.application.security;

@Aspect
@Component
public class ServicePermissionAspect {

    private final PermissionCheckService permissionCheckService;
    private final ClientAccountRepository accountRepository;
    private final SecurityContextHolder securityContextHolder;

    @Around("@annotation(requirePermission)")
    public Object checkPermission(
            ProceedingJoinPoint joinPoint,
            RequireServicePermission requirePermission) throws Throwable {

        // Get current user
        String userId = securityContextHolder.getCurrentUserId();

        // Extract account from method parameters
        ClientAccount account = extractAccount(joinPoint, requirePermission.accountParam());

        // Check permission with account eligibility
        PermissionCheckResult result = permissionCheckService.checkPermission(
            userId,
            requirePermission.value(),
            account
        );

        result.throwIfDenied();

        return joinPoint.proceed();
    }

    private ClientAccount extractAccount(ProceedingJoinPoint joinPoint, String paramName) {
        // Implementation to extract account from method parameters
        // Could be by parameter name, annotation, or ID that needs to be loaded
    }
}
```

### Controller Usage Example

```java
package com.knight.application.rest.payment;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/submit")
    @RequireServicePermission(
        value = "urn:knight:service:payment:action:submit",
        accountParam = "accountId"
    )
    public PaymentResponse submitPayment(
            @RequestParam String accountId,
            @RequestBody PaymentRequest request) {

        // If we reach here, permission check passed including eligibility
        return paymentService.submitPayment(accountId, request);
    }

    @PostMapping("/{paymentId}/approve")
    @RequireServicePermission(
        value = "urn:knight:service:payment:action:approve",
        accountParam = "accountId"
    )
    public PaymentResponse approvePayment(
            @PathVariable String paymentId,
            @RequestParam String accountId) {

        return paymentService.approvePayment(paymentId, accountId);
    }
}
```

### Audit Logging

```java
package com.knight.application.security;

@Component
public class PermissionAuditLogger {

    private static final Logger logger = LoggerFactory.getLogger(PermissionAuditLogger.class);

    public void logPermissionCheck(
            String userId,
            String permissionUrn,
            String accountId,
            PermissionCheckResult result) {

        if (result.allowed()) {
            logger.info("Permission check ALLOWED - User: {}, Permission: {}, Account: {}",
                userId, permissionUrn, accountId);
        } else {
            logger.warn("Permission check DENIED - User: {}, Permission: {}, Account: {}, Reason: {}, Message: {}",
                userId, permissionUrn, accountId, result.denialReason(), result.denialMessage());
        }
    }
}
```

### Error Response Example

```json
{
  "error": "PermissionDenied",
  "reason": "ACCOUNT_INELIGIBLE",
  "message": "Account ACC-123 is not eligible for service Payment Service",
  "timestamp": "2025-12-30T10:15:30Z",
  "path": "/api/payments/submit"
}
```

## Dependencies

- US-SA-001: Service Registration (requires ServiceRegistry)
- US-SA-002: Account Eligibility Check (requires isAccountEligible implementation)

## Test Cases

1. **Test Permission Granted and Account Eligible**
   - Given user has permission and account is eligible
   - When checkPermission is called
   - Then returns allowed result

2. **Test Permission Not Granted**
   - Given user does not have required permission
   - When checkPermission is called
   - Then returns denied with PERMISSION_NOT_GRANTED reason

3. **Test Account Ineligible**
   - Given user has permission but account is not eligible
   - When checkPermission is called
   - Then returns denied with ACCOUNT_INELIGIBLE reason

4. **Test Service Not Found**
   - Given permission URN references non-existent service
   - When checkPermission is called
   - Then returns denied with SERVICE_NOT_FOUND reason

5. **Test Controller Authorization Success**
   - Given user has permission and eligible account
   - When POST /api/payments/submit is called
   - Then controller method executes successfully

6. **Test Controller Authorization Failure - No Permission**
   - Given user lacks permission
   - When POST /api/payments/submit is called
   - Then returns 403 Forbidden with PERMISSION_NOT_GRANTED

7. **Test Controller Authorization Failure - Ineligible Account**
   - Given user has permission but account ineligible
   - When POST /api/payments/submit is called
   - Then returns 403 Forbidden with ACCOUNT_INELIGIBLE

8. **Test Audit Logging**
   - Given permission check is performed
   - When check completes (allowed or denied)
   - Then audit log entry is created with all details

9. **Test Multiple Permission Checks**
   - Given request requires checking multiple permissions
   - When any check fails
   - Then request is denied with first failure reason

10. **Test URN Parsing**
    - Given various valid and invalid URN formats
    - When extracting service ID
    - Then correct service ID is extracted or exception thrown

## UI/UX (if applicable)

### User Feedback for Denied Actions

When a user attempts an action on an ineligible account, provide clear feedback:

**Permission Denied Dialog:**
```
┌─────────────────────────────────────────┐
│  Action Not Available                  │
├─────────────────────────────────────────┤
│                                          │
│  You cannot submit payments for this    │
│  account because it is not eligible     │
│  for the Payment Service.               │
│                                          │
│  Reason: Account does not have payment  │
│  configuration enabled.                 │
│                                          │
│  Please contact your administrator for  │
│  assistance.                            │
│                                          │
│              [OK]                        │
└─────────────────────────────────────────┘
```

**Disabled UI Elements:**
- Actions for ineligible accounts should be visually disabled
- Tooltip on disabled actions should explain eligibility requirements
- UI should pre-filter actions based on account eligibility where possible

### Admin View of Permission Conflicts

```
Permission Audit Report
Account: ACC-123 (Status: Suspended)
User: john.doe@example.com

Recent Denied Actions:
• 2025-12-30 10:15 - Submit Payment [ACCOUNT_INELIGIBLE]
  Reason: Account suspended, payment submission disabled

• 2025-12-30 09:45 - Approve Payment [PERMISSION_NOT_GRANTED]
  Reason: User lacks approval permission
```

This helps administrators diagnose permission issues and account eligibility problems.
