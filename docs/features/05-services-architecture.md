# Services Architecture

## Overview

Services represent the different functional areas of the platform. Each service has a defined set of actions and can filter which client accounts it applies to. The Service abstraction provides a consistent way to define permissions and account visibility across the platform.

## Service Design

### Abstract Service Base Class

```java
public abstract class Service {

    /**
     * Unique identifier for this service.
     * Used in action URNs: [service_type]:[service]:[resource]:[action]
     */
    public abstract String getServiceIdentifier();

    /**
     * The service type category.
     */
    public abstract ServiceType getServiceType();

    /**
     * Display name for UI.
     */
    public abstract String getDisplayName();

    /**
     * Description of the service.
     */
    public abstract String getDescription();

    /**
     * Filter expression to determine which accounts can use this service.
     * Returns true if the account is eligible for this service.
     */
    public abstract boolean isAccountEligible(ClientAccount account);

    /**
     * Get all actions supported by this service.
     */
    public abstract List<Action> getActions();

    /**
     * Get filtered list of eligible accounts for this service.
     */
    public List<ClientAccount> getEligibleAccounts(List<ClientAccount> accounts) {
        return accounts.stream()
            .filter(this::isAccountEligible)
            .collect(Collectors.toList());
    }
}
```

### Service Type Enum

```java
public enum ServiceType {
    REPORTING("reporting", "Reporting & Analytics"),
    PAYMENTS("payments", "Payment Services"),
    SECURITY("security", "Security & Administration");

    private final String identifier;
    private final String displayName;

    // Constructor and getters
}
```

### Action Type Enum

```java
public enum ActionType {
    VIEW("view", "View"),
    CREATE("create", "Create"),
    UPDATE("update", "Update"),
    DELETE("delete", "Delete"),
    APPROVE("approve", "Approve");

    private final String identifier;
    private final String displayName;

    // Constructor and getters
}
```

### Action Value Object

```java
public record Action(
    ServiceType serviceType,
    String service,
    String resourceType,  // Optional, may be null
    ActionType actionType
) {
    /**
     * Convert to URN string representation.
     */
    public String toUrn() {
        if (resourceType == null || resourceType.isBlank()) {
            return "%s:%s:%s".formatted(
                serviceType.getIdentifier(),
                service,
                actionType.getIdentifier()
            );
        }
        return "%s:%s:%s:%s".formatted(
            serviceType.getIdentifier(),
            service,
            resourceType,
            actionType.getIdentifier()
        );
    }

    /**
     * Parse Action from URN string.
     */
    public static Action fromUrn(String urn) {
        String[] parts = urn.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid URN: " + urn);
        }

        ServiceType serviceType = ServiceType.fromIdentifier(parts[0]);
        String service = parts[1];
        String resourceType = parts.length == 4 ? parts[2] : null;
        ActionType actionType = ActionType.fromIdentifier(parts[parts.length - 1]);

        return new Action(serviceType, service, resourceType, actionType);
    }

    /**
     * Check if this action matches a pattern (supports wildcards).
     */
    public boolean matches(String pattern) {
        if (pattern.equals("*")) return true;

        String[] patternParts = pattern.split(":");
        String[] urnParts = toUrn().split(":");

        if (patternParts.length > urnParts.length) return false;

        for (int i = 0; i < patternParts.length; i++) {
            if (!patternParts[i].equals("*") && !patternParts[i].equals(urnParts[i])) {
                return false;
            }
        }
        return true;
    }
}
```

---

## Service Implementations

### ACH Payments Service

```java
@Component
public class AchPaymentsService extends Service {

    @Override
    public String getServiceIdentifier() {
        return "ach";
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.PAYMENTS;
    }

    @Override
    public String getDisplayName() {
        return "ACH Payments";
    }

    @Override
    public String getDescription() {
        return "Automated Clearing House payment processing";
    }

    @Override
    public boolean isAccountEligible(ClientAccount account) {
        // ACH requires accounts with ACH capability enabled
        return account.hasCapability(AccountCapability.ACH)
            && account.getStatus() == AccountStatus.ACTIVE;
    }

    @Override
    public List<Action> getActions() {
        return List.of(
            new Action(ServiceType.PAYMENTS, "ach", "payment", ActionType.CREATE),
            new Action(ServiceType.PAYMENTS, "ach", "payment", ActionType.UPDATE),
            new Action(ServiceType.PAYMENTS, "ach", "payment", ActionType.DELETE),
            new Action(ServiceType.PAYMENTS, "ach", "payment", ActionType.VIEW),
            new Action(ServiceType.PAYMENTS, "ach", "payment", ActionType.APPROVE),
            new Action(ServiceType.PAYMENTS, "ach", "template", ActionType.CREATE),
            new Action(ServiceType.PAYMENTS, "ach", "template", ActionType.UPDATE),
            new Action(ServiceType.PAYMENTS, "ach", "template", ActionType.DELETE),
            new Action(ServiceType.PAYMENTS, "ach", "template", ActionType.VIEW)
        );
    }
}
```

### BNT Reporting Service

```java
@Component
public class BntReportingService extends Service {

    @Override
    public String getServiceIdentifier() {
        return "bnt";
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.REPORTING;
    }

    @Override
    public String getDisplayName() {
        return "Balance & Transaction Reporting";
    }

    @Override
    public String getDescription() {
        return "Real-time balance and transaction reporting";
    }

    @Override
    public boolean isAccountEligible(ClientAccount account) {
        // All active accounts can use BNT reporting
        return account.getStatus() == AccountStatus.ACTIVE;
    }

    @Override
    public List<Action> getActions() {
        return List.of(
            new Action(ServiceType.REPORTING, "bnt", "balances", ActionType.VIEW),
            new Action(ServiceType.REPORTING, "bnt", "transactions", ActionType.VIEW)
        );
    }
}
```

### Receivables Service

```java
@Component
public class ReceivablesService extends Service {

    @Override
    public String getServiceIdentifier() {
        return "receivables";
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.PAYMENTS;
    }

    @Override
    public String getDisplayName() {
        return "Receivables Management";
    }

    @Override
    public String getDescription() {
        return "Manage payors, invoices, and collections";
    }

    @Override
    public boolean isAccountEligible(ClientAccount account) {
        return account.hasCapability(AccountCapability.RECEIVABLES)
            && account.getStatus() == AccountStatus.ACTIVE;
    }

    @Override
    public List<Action> getActions() {
        return List.of(
            new Action(ServiceType.PAYMENTS, "receivables", "payors", ActionType.CREATE),
            new Action(ServiceType.PAYMENTS, "receivables", "payors", ActionType.VIEW),
            new Action(ServiceType.PAYMENTS, "receivables", "invoices", ActionType.CREATE),
            new Action(ServiceType.PAYMENTS, "receivables", "invoices", ActionType.VIEW)
        );
    }
}
```

### User Management Service

```java
@Component
public class UserManagementService extends Service {

    @Override
    public String getServiceIdentifier() {
        return "users";
    }

    @Override
    public ServiceType getServiceType() {
        return ServiceType.SECURITY;
    }

    @Override
    public String getDisplayName() {
        return "User Management";
    }

    @Override
    public String getDescription() {
        return "Create, manage, and configure user access";
    }

    @Override
    public boolean isAccountEligible(ClientAccount account) {
        // User management is not account-specific
        return true;
    }

    @Override
    public List<Action> getActions() {
        return List.of(
            new Action(ServiceType.SECURITY, "users", null, ActionType.CREATE),
            new Action(ServiceType.SECURITY, "users", null, ActionType.VIEW),
            new Action(ServiceType.SECURITY, "users", null, ActionType.UPDATE),
            new Action(ServiceType.SECURITY, "users", null, ActionType.DELETE),
            new Action(ServiceType.SECURITY, "users", null, ActionType.APPROVE)
        );
    }
}
```

---

## User Stories

### US-SA-001: Service Registration

**As a** developer
**I want** services to be auto-discovered
**So that** new services are automatically available

#### Acceptance Criteria

- [ ] Services implement `Service` abstract class
- [ ] Services annotated with `@Component`
- [ ] Service registry collects all service beans
- [ ] Services accessible via `ServiceRegistry.getService(id)`

---

### US-SA-002: Account Eligibility Check

**As a** system
**I want** to filter accounts by service eligibility
**So that** users only see accounts they can use

#### Acceptance Criteria

- [ ] Each service implements `isAccountEligible()`
- [ ] Filter based on account capabilities, status, etc.
- [ ] UI shows only eligible accounts per service
- [ ] API returns only eligible accounts

---

### US-SA-003: Get Service Actions

**As a** developer
**I want** to enumerate all actions for a service
**So that** I can build permission UIs dynamically

#### Acceptance Criteria

- [ ] `getActions()` returns all service actions
- [ ] Actions include service type, service, resource, action type
- [ ] Actions can be converted to/from URN strings

---

### US-SA-004: Dynamic Permission UI

**As a** Client Administrator
**I want** permission options generated from services
**So that** I always see current available actions

#### Acceptance Criteria

- [ ] Permission UI calls `ServiceRegistry.getAllActions()`
- [ ] Actions grouped by service type
- [ ] New services automatically appear
- [ ] Deprecated actions hidden from new assignments

---

### US-SA-005: Account Filtering in Permission Check

**As a** system
**I want** permission checks to respect service eligibility
**So that** users can't access ineligible accounts

#### Acceptance Criteria

- [ ] Permission check includes service eligibility filter
- [ ] Even if permission grants access, ineligible accounts excluded
- [ ] Clear error message if trying to access ineligible account

---

## Service Registry

```java
@Component
public class ServiceRegistry {

    private final Map<String, Service> services;

    public ServiceRegistry(List<Service> serviceList) {
        this.services = serviceList.stream()
            .collect(Collectors.toMap(
                Service::getServiceIdentifier,
                Function.identity()
            ));
    }

    public Optional<Service> getService(String identifier) {
        return Optional.ofNullable(services.get(identifier));
    }

    public List<Service> getAllServices() {
        return new ArrayList<>(services.values());
    }

    public List<Service> getServicesByType(ServiceType type) {
        return services.values().stream()
            .filter(s -> s.getServiceType() == type)
            .collect(Collectors.toList());
    }

    public List<Action> getAllActions() {
        return services.values().stream()
            .flatMap(s -> s.getActions().stream())
            .collect(Collectors.toList());
    }

    public List<ClientAccount> getEligibleAccounts(
        String serviceId,
        List<ClientAccount> accounts
    ) {
        return getService(serviceId)
            .map(s -> s.getEligibleAccounts(accounts))
            .orElse(List.of());
    }
}
```

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/services` | List all services |
| `GET` | `/api/services/{id}` | Get service details |
| `GET` | `/api/services/{id}/actions` | Get service actions |
| `GET` | `/api/services/{id}/accounts` | Get eligible accounts |
| `GET` | `/api/actions` | List all available actions |

---

## Integration with Permissions

```java
@Service
public class PermissionService {

    private final ServiceRegistry serviceRegistry;

    public boolean isAllowed(User user, String actionUrn, UUID accountId) {
        Action action = Action.fromUrn(actionUrn);

        // 1. Check service eligibility (if account specified)
        if (accountId != null) {
            Service service = serviceRegistry.getService(action.service())
                .orElseThrow(() -> new UnknownServiceException(action.service()));

            ClientAccount account = getAccount(accountId);
            if (!service.isAccountEligible(account)) {
                return false;  // Account not eligible for this service
            }
        }

        // 2. Check user permissions
        return checkUserPermissions(user, action, accountId);
    }

    public List<ClientAccount> getAllowedAccounts(User user, String actionUrn) {
        Action action = Action.fromUrn(actionUrn);

        // 1. Get all profile accounts
        List<ClientAccount> allAccounts = getProfileAccounts(user.getProfileId());

        // 2. Filter by service eligibility
        Service service = serviceRegistry.getService(action.service())
            .orElseThrow(() -> new UnknownServiceException(action.service()));
        List<ClientAccount> eligible = service.getEligibleAccounts(allAccounts);

        // 3. Filter by user permissions
        return filterByPermissions(user, action, eligible);
    }
}
```

---

## Future Considerations

1. **Service Versioning**: Support for multiple versions of a service
2. **Service Dependencies**: Services that require other services
3. **Service Limits**: Rate limits and quotas per service
4. **Service Pricing**: Different pricing tiers per service
5. **Service Audit**: Detailed logging per service usage
