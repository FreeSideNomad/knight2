# US-SA-001: Service Registration

## Story

**As a** system architect
**I want** all services to implement a common abstract Service class and be automatically registered
**So that** the system can dynamically discover and manage all available services in a centralized registry

## Acceptance Criteria

- [ ] Abstract Service base class is created with all required methods
- [ ] Service class includes getServiceIdentifier() method returning unique service identifier
- [ ] Service class includes getServiceType() method returning service type enumeration
- [ ] Service class includes getDisplayName() method returning human-readable service name
- [ ] Service class includes getDescription() method returning service description
- [ ] Service class includes abstract isAccountEligible(ClientAccount) method
- [ ] Service class includes abstract getActions() method returning List<Action>
- [ ] ServiceRegistry component is created to collect all Service implementations
- [ ] ServiceRegistry uses Spring dependency injection to discover all @Component services
- [ ] ServiceRegistry provides methods to retrieve services by identifier or type
- [ ] All existing services (PaymentService, ReportingService, etc.) extend the Service class
- [ ] All service implementations are annotated with @Component
- [ ] Unit tests verify ServiceRegistry correctly discovers all registered services

## Technical Notes

### Abstract Service Class Structure

```java
package com.knight.domain.services;

import com.knight.domain.clients.aggregate.ClientAccount;
import java.util.List;

public abstract class Service {

    /**
     * Returns unique identifier for this service (e.g., "payment", "reporting")
     */
    public abstract String getServiceIdentifier();

    /**
     * Returns the type of service for categorization
     */
    public abstract ServiceType getServiceType();

    /**
     * Returns human-readable display name
     */
    public abstract String getDisplayName();

    /**
     * Returns description of service functionality
     */
    public abstract String getDescription();

    /**
     * Determines if a client account is eligible for this service
     */
    public abstract boolean isAccountEligible(ClientAccount account);

    /**
     * Returns list of actions available for this service
     */
    public abstract List<Action> getActions();
}
```

### ServiceRegistry Implementation

```java
package com.knight.domain.services;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ServiceRegistry {

    private final Map<String, Service> services;

    public ServiceRegistry(List<Service> services) {
        this.services = services.stream()
            .collect(Collectors.toMap(Service::getServiceIdentifier, s -> s));
    }

    public Optional<Service> getService(String identifier) {
        return Optional.ofNullable(services.get(identifier));
    }

    public List<Service> getAllServices() {
        return List.copyOf(services.values());
    }

    public List<Service> getServicesByType(ServiceType type) {
        return services.values().stream()
            .filter(s -> s.getServiceType() == type)
            .collect(Collectors.toList());
    }
}
```

### ServiceType Enumeration

```java
package com.knight.domain.services;

public enum ServiceType {
    PAYMENT,
    REPORTING,
    ADMINISTRATIVE,
    INTEGRATION,
    COMMUNICATION
}
```

### Action Value Object

```java
package com.knight.domain.services;

public record Action(
    String identifier,
    String displayName,
    String description
) {
    public String toURN(String serviceIdentifier) {
        return String.format("urn:knight:service:%s:action:%s",
            serviceIdentifier, identifier);
    }
}
```

### Package Structure

- Create new package: `com.knight.domain.services`
- Move existing service implementations to this package or have them extend Service
- ServiceRegistry should be in the same package

## Dependencies

- None (foundational story)

## Test Cases

1. **Test Service Registration**
   - Given multiple Service implementations annotated with @Component
   - When Spring context initializes
   - Then ServiceRegistry contains all service instances

2. **Test Get Service By Identifier**
   - Given ServiceRegistry with registered services
   - When getService("payment") is called
   - Then PaymentService instance is returned

3. **Test Get Services By Type**
   - Given ServiceRegistry with services of different types
   - When getServicesByType(ServiceType.PAYMENT) is called
   - Then only PAYMENT type services are returned

4. **Test Service Methods**
   - Given a concrete Service implementation
   - When calling getServiceIdentifier(), getDisplayName(), etc.
   - Then appropriate values are returned

5. **Test Action URN Conversion**
   - Given an Action with identifier "submit"
   - When toURN("payment") is called
   - Then returns "urn:knight:service:payment:action:submit"

## UI/UX (if applicable)

Not applicable - backend infrastructure only.
