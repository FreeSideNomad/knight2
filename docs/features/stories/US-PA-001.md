# US-PA-001: Define Action URN Structure

## Story

**As a** system architect
**I want** a standardized Action URN structure for permission actions
**So that** permissions can be consistently defined and managed across all services

## Acceptance Criteria

- [ ] Action URN follows the format: `[service_type]:[service]:[resource_type]:[action_type]`
- [ ] Action value object is implemented with validation rules
- [ ] ServiceType enum includes: DIRECT, INDIRECT, BANK, ADMIN
- [ ] ActionType enum includes: VIEW, CREATE, UPDATE, DELETE, APPROVE, MANAGE
- [ ] Action URN parsing validates each segment
- [ ] Invalid URN formats throw appropriate validation exceptions
- [ ] Action URN can be serialized/deserialized for storage
- [ ] Documentation includes examples of valid Action URNs

## Technical Notes

**Domain Model:**
- Create `Action` value object in shared kernel
- Implement `ServiceType` enum with values: DIRECT, INDIRECT, BANK, ADMIN
- Implement `ActionType` enum with values: VIEW, CREATE, UPDATE, DELETE, APPROVE, MANAGE
- Action should be immutable with factory method: `Action.of(String urn)`

**Validation Rules:**
- URN must have exactly 4 segments separated by colons
- Each segment must be non-empty
- ServiceType must match enum values
- ActionType must match enum values
- Service name must match pattern: `[a-z][a-z0-9-]*`
- Resource type must match pattern: `[a-z][a-z0-9-]*`

**Example URNs:**
```
direct:client-portal:profile:view
direct:client-portal:account:create
indirect:indirect-portal:client:update
bank:payor-enrolment:enrolment:approve
admin:user-management:user:manage
```

**Database Schema:**
No direct database changes, but Action URN will be stored as VARCHAR(255) in permission tables.

## Dependencies

None - This is a foundational story

## Test Cases

1. **Valid URN Creation**
   - Given: URN string "direct:client-portal:profile:view"
   - When: Action.of() is called
   - Then: Action object is created successfully

2. **Invalid URN - Wrong Segment Count**
   - Given: URN string "direct:client-portal:view"
   - When: Action.of() is called
   - Then: ValidationException is thrown

3. **Invalid URN - Unknown Service Type**
   - Given: URN string "unknown:client-portal:profile:view"
   - When: Action.of() is called
   - Then: ValidationException is thrown

4. **Invalid URN - Unknown Action Type**
   - Given: URN string "direct:client-portal:profile:execute"
   - When: Action.of() is called
   - Then: ValidationException is thrown

5. **URN Serialization**
   - Given: Valid Action object
   - When: toString() is called
   - Then: Original URN string is returned

6. **URN Equality**
   - Given: Two Action objects with same URN
   - When: equals() is called
   - Then: Returns true

## UI/UX (if applicable)

Not applicable - This is a domain model story
