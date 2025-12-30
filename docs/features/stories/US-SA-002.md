# US-SA-002: Account Eligibility Check

## Story

**As a** service administrator
**I want** each service to implement eligibility rules for client accounts
**So that** only qualified accounts can access specific services based on their configuration and status

## Acceptance Criteria

- [ ] Each service implements isAccountEligible(ClientAccount) method
- [ ] PaymentService checks for active account status and payment configuration
- [ ] ReportingService checks for reporting permissions and data access rights
- [ ] Eligibility logic considers account type (direct vs indirect)
- [ ] Eligibility logic considers account status (active, suspended, closed)
- [ ] Eligibility logic considers service-specific configurations
- [ ] Method returns clear boolean result without exceptions
- [ ] Unit tests verify eligibility rules for each service
- [ ] Integration tests verify eligibility checks work with real ClientAccount objects

## Technical Notes

### Implementation Guidelines

Each service must implement the abstract `isAccountEligible(ClientAccount account)` method with service-specific business rules.

### Example: PaymentService Eligibility

```java
@Component
public class PaymentService extends Service {

    @Override
    public boolean isAccountEligible(ClientAccount account) {
        // Account must be active
        if (!account.isActive()) {
            return false;
        }

        // Account must have payment configuration enabled
        if (!account.hasPaymentConfiguration()) {
            return false;
        }

        // Check for specific payment features based on account type
        if (account.isIndirectAccount() && !account.hasIndirectPaymentEnabled()) {
            return false;
        }

        return true;
    }

    // Other Service methods...
}
```

### Example: ReportingService Eligibility

```java
@Component
public class ReportingService extends Service {

    @Override
    public boolean isAccountEligible(ClientAccount account) {
        // Account must be active or suspended (can still view reports)
        if (account.isClosed()) {
            return false;
        }

        // Account must have reporting access configured
        if (!account.hasReportingAccess()) {
            return false;
        }

        // Check data retention policy
        if (!account.hasValidDataRetentionPeriod()) {
            return false;
        }

        return true;
    }

    // Other Service methods...
}
```

### ClientAccount Extensions

May need to add helper methods to ClientAccount aggregate:

```java
public class ClientAccount {
    // Existing fields and methods...

    public boolean hasPaymentConfiguration() {
        // Check if payment config exists
    }

    public boolean hasReportingAccess() {
        // Check reporting permissions
    }

    public boolean hasIndirectPaymentEnabled() {
        // Check indirect payment feature flag
    }

    public boolean hasValidDataRetentionPeriod() {
        // Validate data retention policy
    }
}
```

### Common Eligibility Patterns

- Account status check (active, suspended, closed)
- Feature flag validation
- Configuration presence verification
- Account type-specific rules (direct vs indirect)
- Date/time-based eligibility (trial period, expiration)
- Compliance and regulatory checks

## Dependencies

- US-SA-001: Service Registration (requires abstract Service class)

## Test Cases

1. **Test PaymentService Eligibility - Active Account**
   - Given a ClientAccount with active status and payment configuration
   - When isAccountEligible() is called
   - Then returns true

2. **Test PaymentService Eligibility - Inactive Account**
   - Given a ClientAccount with inactive status
   - When isAccountEligible() is called
   - Then returns false

3. **Test PaymentService Eligibility - Missing Configuration**
   - Given an active ClientAccount without payment configuration
   - When isAccountEligible() is called
   - Then returns false

4. **Test ReportingService Eligibility - Closed Account**
   - Given a closed ClientAccount
   - When isAccountEligible() is called on ReportingService
   - Then returns false

5. **Test ReportingService Eligibility - Suspended Account**
   - Given a suspended ClientAccount with reporting access
   - When isAccountEligible() is called on ReportingService
   - Then returns true (can still view historical reports)

6. **Test Indirect Account Eligibility**
   - Given an indirect ClientAccount without indirect payment enabled
   - When isAccountEligible() is called on PaymentService
   - Then returns false

7. **Test Multiple Services Eligibility**
   - Given a ClientAccount eligible for some services but not others
   - When checking eligibility across all services
   - Then each service returns appropriate eligibility result

## UI/UX (if applicable)

Not applicable - backend business logic only. However, eligibility results will be used by US-SA-004 to filter available actions in the UI.
