# US-SA-004: Dynamic Permission UI

## Story

**As a** administrator
**I want** permission options to be automatically generated from registered services and their actions
**So that** I don't need to manually maintain permission lists and new services are automatically available

## Acceptance Criteria

- [ ] Permission management UI retrieves available services from ServiceRegistry
- [ ] UI displays services grouped by ServiceType
- [ ] Each service shows its display name and description
- [ ] Actions for each service are dynamically retrieved via getActions()
- [ ] UI displays action display names with descriptions as tooltips/help text
- [ ] Selected permissions are stored as URNs (urn:knight:service:{serviceId}:action:{actionId})
- [ ] UI supports searching/filtering services and actions
- [ ] UI indicates when new services/actions are added (version tracking)
- [ ] Backend API endpoint provides service and action metadata
- [ ] Frontend components render permission checkboxes dynamically
- [ ] Permission assignment saves URN format to database
- [ ] Permission display shows human-readable names (resolves URNs to display names)

## Technical Notes

### Backend API Endpoint

```java
package com.knight.application.rest.services;

@RestController
@RequestMapping("/api/services")
public class ServiceMetadataController {

    private final ServiceRegistry serviceRegistry;

    @GetMapping
    public ServiceMetadataResponse getAllServices() {
        return ServiceMetadataResponse.from(serviceRegistry.getAllServices());
    }

    @GetMapping("/{serviceId}/actions")
    public List<ActionDTO> getServiceActions(@PathVariable String serviceId) {
        return serviceRegistry.getService(serviceId)
            .map(service -> service.getActions().stream()
                .map(action -> ActionDTO.from(action, serviceId))
                .toList())
            .orElseThrow(() -> new ServiceNotFoundException(serviceId));
    }
}
```

### Response DTOs

```java
public record ServiceMetadataResponse(
    List<ServiceDTO> services,
    Map<ServiceType, List<ServiceDTO>> servicesByType
) {
    public static ServiceMetadataResponse from(List<Service> services) {
        List<ServiceDTO> serviceDTOs = services.stream()
            .map(ServiceDTO::from)
            .toList();

        Map<ServiceType, List<ServiceDTO>> grouped = serviceDTOs.stream()
            .collect(Collectors.groupingBy(ServiceDTO::type));

        return new ServiceMetadataResponse(serviceDTOs, grouped);
    }
}

public record ServiceDTO(
    String identifier,
    ServiceType type,
    String displayName,
    String description,
    List<ActionDTO> actions
) {
    public static ServiceDTO from(Service service) {
        return new ServiceDTO(
            service.getServiceIdentifier(),
            service.getServiceType(),
            service.getDisplayName(),
            service.getDescription(),
            service.getActions().stream()
                .map(action -> ActionDTO.from(action, service.getServiceIdentifier()))
                .toList()
        );
    }
}

public record ActionDTO(
    String identifier,
    String displayName,
    String description,
    String urn
) {
    public static ActionDTO from(Action action, String serviceIdentifier) {
        return new ActionDTO(
            action.identifier(),
            action.displayName(),
            action.description(),
            action.toURN(serviceIdentifier)
        );
    }
}
```

### Frontend Component (React/Vaadin)

```typescript
// React example
interface ServiceMetadata {
  identifier: string;
  type: string;
  displayName: string;
  description: string;
  actions: ActionMetadata[];
}

interface ActionMetadata {
  identifier: string;
  displayName: string;
  description: string;
  urn: string;
}

const PermissionSelector: React.FC<Props> = ({ selectedPermissions, onChange }) => {
  const [services, setServices] = useState<ServiceMetadata[]>([]);

  useEffect(() => {
    fetch('/api/services')
      .then(res => res.json())
      .then(data => setServices(data.services));
  }, []);

  return (
    <div className="permission-selector">
      {services.map(service => (
        <ServiceSection key={service.identifier}>
          <h3>{service.displayName}</h3>
          <p className="description">{service.description}</p>
          <div className="actions">
            {service.actions.map(action => (
              <Checkbox
                key={action.urn}
                label={action.displayName}
                tooltip={action.description}
                checked={selectedPermissions.includes(action.urn)}
                onChange={(checked) => handlePermissionChange(action.urn, checked)}
              />
            ))}
          </div>
        </ServiceSection>
      ))}
    </div>
  );
};
```

### Database Schema

Permissions stored as URNs in existing permission tables:

```sql
-- Assuming existing permission_policy table
-- URNs stored in permission_identifier column
INSERT INTO permission_policy (profile_id, permission_identifier)
VALUES
  ('profile-123', 'urn:knight:service:payment:action:submit'),
  ('profile-123', 'urn:knight:service:payment:action:approve'),
  ('profile-123', 'urn:knight:service:reporting:action:view');
```

### Permission Resolution Service

```java
@Component
public class PermissionResolver {

    private final ServiceRegistry serviceRegistry;

    public Optional<ActionMetadata> resolvePermission(String urn) {
        // Parse URN: urn:knight:service:{serviceId}:action:{actionId}
        String[] parts = urn.split(":");
        if (parts.length != 6) return Optional.empty();

        String serviceId = parts[3];
        String actionId = parts[5];

        return serviceRegistry.getService(serviceId)
            .flatMap(service -> service.getActions().stream()
                .filter(action -> action.identifier().equals(actionId))
                .findFirst()
                .map(action -> new ActionMetadata(
                    service.getDisplayName(),
                    action.displayName(),
                    action.description()
                )));
    }
}
```

### UI Features

1. **Grouping by Service Type**
   - Payment Services
   - Reporting Services
   - Administrative Services
   - etc.

2. **Search/Filter**
   - Filter by service name
   - Filter by action name
   - Filter by service type

3. **Bulk Selection**
   - Select all actions for a service
   - Select all services of a type
   - Clear all selections

4. **Visual Indicators**
   - Show count of selected permissions
   - Highlight recently added services/actions
   - Show required vs optional permissions

## Dependencies

- US-SA-001: Service Registration (requires ServiceRegistry)
- US-SA-003: Get Service Actions (requires Action objects with URN conversion)

## Test Cases

1. **Test API Returns All Services**
   - Given ServiceRegistry with multiple registered services
   - When GET /api/services is called
   - Then response contains all services with actions

2. **Test Services Grouped By Type**
   - Given services of different types
   - When GET /api/services is called
   - Then response includes servicesByType map with proper grouping

3. **Test Get Service Actions Endpoint**
   - Given service ID "payment"
   - When GET /api/services/payment/actions is called
   - Then returns all PaymentService actions with URNs

4. **Test Service Not Found**
   - Given invalid service ID
   - When GET /api/services/invalid/actions is called
   - Then returns 404 with appropriate error message

5. **Test Permission URN Format**
   - Given action selections in UI
   - When permissions are saved
   - Then database contains URNs in correct format

6. **Test Permission Resolution**
   - Given stored URN "urn:knight:service:payment:action:submit"
   - When permission is displayed in UI
   - Then shows "Payment Service - Submit Payment"

7. **Test UI Renders Dynamically**
   - Given new service added to system
   - When permission UI is loaded
   - Then new service and its actions appear automatically

8. **Test Search Functionality**
   - Given permission UI with search term "payment"
   - When filtering is applied
   - Then only payment-related services and actions are shown

## UI/UX (if applicable)

### Permission Management Screen Mockup

```
┌─────────────────────────────────────────────────────────┐
│ Assign Permissions to Profile                          │
├─────────────────────────────────────────────────────────┤
│ Search: [_________________] 🔍                          │
│                                                          │
│ ┌─ PAYMENT SERVICES ────────────────────────────────┐  │
│ │                                                     │  │
│ │ Payment Service                                     │  │
│ │ Process and manage payment transactions            │  │
│ │   ☐ Submit Payment                                 │  │
│ │   ☐ Approve Payment                                │  │
│ │   ☐ Void Payment                                   │  │
│ │   ☐ View Payment Details                          │  │
│ │   ☐ View Payment History                          │  │
│ │                                                     │  │
│ └─────────────────────────────────────────────────────┘  │
│                                                          │
│ ┌─ REPORTING SERVICES ──────────────────────────────┐  │
│ │                                                     │  │
│ │ Reporting Service                                   │  │
│ │ Generate and manage reports                        │  │
│ │   ☐ View Reports                                   │  │
│ │   ☐ Export Reports                                 │  │
│ │   ☐ Schedule Reports                               │  │
│ │   ☐ Customize Reports                              │  │
│ │                                                     │  │
│ └─────────────────────────────────────────────────────┘  │
│                                                          │
│ [Cancel]                                  [Save]         │
└─────────────────────────────────────────────────────────┘
```

### Permission Display (View Mode)

```
Profile: Customer Service Representative
Permissions (8):

Payment Service:
  • Submit Payment
  • View Payment Details
  • View Payment History

Reporting Service:
  • View Reports
  • Export Reports
```

The UI automatically updates when new services or actions are added to the system.
