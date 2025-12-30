# US-SA-003: Get Service Actions

## Story

**As a** service developer
**I want** each service to return its available actions as Action value objects
**So that** the system can dynamically generate permission URNs and display available service capabilities

## Acceptance Criteria

- [ ] Each service implements getActions() method returning List<Action>
- [ ] Action value object contains identifier, displayName, and description
- [ ] Action provides toURN(serviceIdentifier) method for permission URN generation
- [ ] PaymentService returns actions like "submit", "approve", "void"
- [ ] ReportingService returns actions like "view", "export", "schedule"
- [ ] Action identifiers follow consistent naming conventions (lowercase, hyphen-separated)
- [ ] Display names are human-readable (e.g., "Submit Payment", "View Reports")
- [ ] URN format follows pattern: urn:knight:service:{serviceId}:action:{actionId}
- [ ] Unit tests verify action lists for each service
- [ ] Unit tests verify URN generation produces correct format

## Technical Notes

### Action Value Object

```java
package com.knight.domain.services;

public record Action(
    String identifier,
    String displayName,
    String description
) {
    /**
     * Converts action to URN format for permission system
     * Format: urn:knight:service:{serviceId}:action:{actionId}
     */
    public String toURN(String serviceIdentifier) {
        return String.format("urn:knight:service:%s:action:%s",
            serviceIdentifier, identifier);
    }
}
```

### Example: PaymentService Actions

```java
@Component
public class PaymentService extends Service {

    private static final List<Action> ACTIONS = List.of(
        new Action(
            "submit",
            "Submit Payment",
            "Submit a new payment for processing"
        ),
        new Action(
            "approve",
            "Approve Payment",
            "Approve a pending payment transaction"
        ),
        new Action(
            "void",
            "Void Payment",
            "Cancel or void an existing payment"
        ),
        new Action(
            "view-details",
            "View Payment Details",
            "View detailed information about a payment"
        ),
        new Action(
            "view-history",
            "View Payment History",
            "Access historical payment records"
        )
    );

    @Override
    public List<Action> getActions() {
        return ACTIONS;
    }

    @Override
    public String getServiceIdentifier() {
        return "payment";
    }

    // Other Service methods...
}
```

### Example: ReportingService Actions

```java
@Component
public class ReportingService extends Service {

    private static final List<Action> ACTIONS = List.of(
        new Action(
            "view",
            "View Reports",
            "Access and view available reports"
        ),
        new Action(
            "export",
            "Export Reports",
            "Export report data to various formats"
        ),
        new Action(
            "schedule",
            "Schedule Reports",
            "Create scheduled report generation"
        ),
        new Action(
            "customize",
            "Customize Reports",
            "Modify report parameters and filters"
        )
    );

    @Override
    public List<Action> getActions() {
        return ACTIONS;
    }

    @Override
    public String getServiceIdentifier() {
        return "reporting";
    }

    // Other Service methods...
}
```

### URN Generation Examples

```java
// PaymentService actions to URNs
Action submitAction = new Action("submit", "Submit Payment", "...");
String urn = submitAction.toURN("payment");
// Result: "urn:knight:service:payment:action:submit"

// ReportingService actions to URNs
Action viewAction = new Action("view", "View Reports", "...");
String urn = viewAction.toURN("reporting");
// Result: "urn:knight:service:reporting:action:view"
```

### Action Naming Conventions

**Identifiers (technical):**
- Use lowercase letters
- Use hyphens for multi-word identifiers
- Be concise but descriptive
- Examples: "submit", "approve", "view-details", "export-pdf"

**Display Names (UI):**
- Use title case
- Include verb and noun
- Be user-friendly
- Examples: "Submit Payment", "Approve Transaction", "View Report Details"

**Descriptions:**
- Complete sentence explaining the action
- Describe what the action does
- Provide context for permission assignment

### Future Extensibility

Consider adding optional fields to Action in future iterations:
- `boolean requiresApproval` - Whether action needs approval workflow
- `SecurityLevel securityLevel` - Sensitivity level of the action
- `List<String> requiredRoles` - Base roles required for this action
- `Map<String, String> metadata` - Additional action metadata

## Dependencies

- US-SA-001: Service Registration (requires abstract Service class and Action value object)

## Test Cases

1. **Test PaymentService Actions**
   - Given PaymentService instance
   - When getActions() is called
   - Then returns list containing submit, approve, void, view-details, view-history actions

2. **Test ReportingService Actions**
   - Given ReportingService instance
   - When getActions() is called
   - Then returns list containing view, export, schedule, customize actions

3. **Test Action URN Generation**
   - Given Action with identifier "submit" and service identifier "payment"
   - When toURN("payment") is called
   - Then returns "urn:knight:service:payment:action:submit"

4. **Test Action Immutability**
   - Given an Action record
   - When attempting to modify fields
   - Then Action remains immutable (compile-time enforcement via record)

5. **Test Action List Immutability**
   - Given service returns action list
   - When attempting to modify the returned list
   - Then modification is prevented or creates new list

6. **Test All Services Return Actions**
   - Given ServiceRegistry with all registered services
   - When iterating through all services and calling getActions()
   - Then each service returns at least one action

7. **Test URN Format Compliance**
   - Given all actions from all services
   - When converting to URNs
   - Then all URNs match pattern: urn:knight:service:[a-z-]+:action:[a-z-]+

## UI/UX (if applicable)

Actions will be displayed in permission management UI:

### Permission Assignment Screen
```
Service: Payment Service
Available Actions:
☐ Submit Payment - Submit a new payment for processing
☐ Approve Payment - Approve a pending payment transaction
☐ Void Payment - Cancel or void an existing payment
☐ View Payment Details - View detailed information about a payment
☐ View Payment History - Access historical payment records
```

The display names and descriptions from Action objects will populate these UI elements dynamically.
