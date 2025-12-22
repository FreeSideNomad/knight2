# Knight Platform Test Plan

## Overview

This test plan outlines a comprehensive testing strategy to achieve 80%+ test coverage across all Knight Platform modules. The approach prioritizes unit tests for domain aggregates and services, with E2E tests for API integration validation.

---

## Coverage Requirements

### Target Metrics

| Metric | Minimum | Desired |
|--------|---------|---------|
| Line Coverage | 80% | 90% |
| Branch Coverage | 80% | 90% |

### Modules with 80% Coverage Requirement

The following modules are required to maintain **80% line and 80% branch coverage**:

| Module | Line Target | Branch Target | Current Line | Current Branch | Status |
|--------|-------------|---------------|--------------|----------------|--------|
| `domain/profiles` | 80% | 80% | 99.6% | 89.4% | :white_check_mark: PASSING |
| `domain/users` | 80% | 80% | 98.4% | 92.2% | :white_check_mark: PASSING |
| `domain/batch` | 80% | 80% | 96.3% | 95.6% | :white_check_mark: PASSING |
| `domain/clients` | 80% | 80% | 75.6% | 60.2% | :x: NEEDS IMPROVEMENT |
| `domain/policy` | 80% | 80% | 60.9% | 60.9% | :x: NEEDS IMPROVEMENT |
| `kernel` | 80% | 80% | 64.0% | 54.1% | :x: NEEDS IMPROVEMENT |
| `application` | 80% | 80% | 70.8% | 53.4% | :x: NEEDS IMPROVEMENT |

### Modules Excluded from 80% Requirement

| Module | Current Line | Current Branch | Reason |
|--------|--------------|----------------|--------|
| `employee-portal` | 1.5% | 0.0% | Vaadin UI - tested via manual/integration testing |
| `domain/approvals` | 3.5% | 0.0% | Future phase - not yet implemented |
| `domain/auth0-identity` | 42.1% | 25.0% | External integration - mocked in tests |

### Enforced via Maven

Coverage is enforced by JaCoCo Maven plugin. Modules that require 80% coverage have the following configuration in their `pom.xml`:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>check</id>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

---

## Module Summary

**Modules with 80% Coverage Requirement:**
- `kernel` - Shared value objects and identifiers
- `domain/clients` - Client and IndirectClient aggregates
- `domain/profiles` - Profile management
- `domain/users` - User lifecycle management
- `domain/batch` - Batch processing
- `domain/policy` - Permission policies
- `application` - REST controllers, persistence adapters, mappers

**Modules Excluded from Coverage Requirement:**
- `employee-portal` - Vaadin UI (minimal testing)
- `domain/approvals` - Future phase
- `domain/auth0-identity` - External integration

---

## Test Infrastructure

### Base Test Configuration

```java
// For unit tests - pure domain logic
@ExtendWith(MockitoExtension.class)
class DomainUnitTest {
    // Mock repositories and external dependencies
}

// For E2E tests - full stack with H2
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MSSQLServer",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class E2ETest {
    @Autowired MockMvc mockMvc;
}
```

### Test Fixtures

```java
public class TestFixtures {
    public static final SrfClientId SRF_CLIENT = new SrfClientId("123456789");
    public static final CdrClientId CDR_CLIENT = new CdrClientId("000123");
    public static final ProfileId ONLINE_PROFILE = ProfileId.fromUrn("online:srf:123456789");
    public static final UserId TEST_USER = UserId.of(UUID.randomUUID().toString());

    public static Address defaultAddress() {
        return new Address("123 Main St", null, "Toronto", "ON", "M5V 1A1", "CA");
    }
}
```

---

## Module 1: kernel

**Location:** `kernel/src/main/java/com/knight/platform/sharedkernel/`

### Unit Tests Required

#### Test Class: `ClientIdTest`

| Test Case | Description |
|-----------|-------------|
| `SrfClientId_creation_success` | Create SrfClientId with valid 9-digit number |
| `SrfClientId_creation_invalidFormat_fails` | Reject non-9-digit input |
| `CdrClientId_creation_success` | Create CdrClientId with valid format |
| `BankClientId_srf_factoryMethod` | BankClientId.srf() creates SrfClientId |
| `BankClientId_cdr_factoryMethod` | BankClientId.cdr() creates CdrClientId |
| `ClientId_of_parsesUrn` | ClientId.of("srf:123") returns correct type |
| `ClientId_equality` | Same URN = equal, different URN = not equal |

#### Test Class: `IndirectClientIdTest`

| Test Case | Description |
|-----------|-------------|
| `of_createsWithSequence` | IndirectClientId.of(clientId, 1) creates correct URN |
| `of_nullClientId_throws` | Null clientId throws IllegalArgumentException |
| `of_nonPositiveSequence_throws` | Sequence <= 0 throws IllegalArgumentException |
| `fromUrn_validUrn_parses` | Parse "indirect:srf:123:5" correctly |
| `fromUrn_invalidFormat_throws` | Invalid URN throws exception |
| `urn_format` | URN format is "indirect:{clientUrn}:{seq}" |

#### Test Class: `ProfileIdTest`

| Test Case | Description |
|-----------|-------------|
| `of_createsWithType` | ProfileId.of("online", clientId) creates correct URN |
| `fromUrn_validUrn_parses` | Parse existing URN correctly |
| `fromUrn_invalidFormat_throws` | Invalid format throws exception |
| `profileType_extraction` | Extract profile type from URN |

#### Test Class: `AddressTest`

| Test Case | Description |
|-----------|-------------|
| `create_withAllFields` | Create address with all fields |
| `create_withRequiredFieldsOnly` | Create address with minimal fields |
| `equality` | Same values = equal |
| `toString_formatting` | Readable string representation |

#### Test Class: `GradsAccountValidatorTest`

| Test Case | Description |
|-----------|-------------|
| `validate_validAccount_returnsTrue` | Valid account returns true |
| `validate_invalidAccount_returnsFalse` | Invalid account returns false |
| `validate_nullAccount_returnsFalse` | Null input returns false |

---

## Module 2: domain/clients

**Location:** `domain/clients/src/main/java/com/knight/domain/clients/`

### 2.1 Client Aggregate Unit Tests

#### Test Class: `ClientTest`

| Test Case | Description |
|-----------|-------------|
| **Creation** |
| `create_validInput_createsClient` | Create client with valid data |
| `create_nullClientId_throws` | Null clientId throws NPE |
| `create_blankName_throws` | Blank name throws IllegalArgumentException |
| `create_nullClientType_throws` | Null clientType throws NPE |
| `create_nullAddress_throws` | Null address throws NPE |
| `create_setsActiveStatus` | New client is ACTIVE by default |
| **Name Update** |
| `updateName_validName_updates` | Update name successfully |
| `updateName_blankName_throws` | Blank name throws exception |
| `updateName_whenSuspended_throws` | Cannot update suspended client |
| **Address Update** |
| `updateAddress_validAddress_updates` | Update address successfully |
| `updateAddress_nullAddress_throws` | Null address throws NPE |
| `updateAddress_whenSuspended_throws` | Cannot update suspended client |
| **Status Changes** |
| `activate_fromInactive_activates` | Inactive -> Active |
| `activate_whenActive_noOp` | Already active is no-op |
| `activate_whenSuspended_throws` | Cannot activate suspended |
| `deactivate_fromActive_deactivates` | Active -> Inactive |
| `deactivate_whenInactive_noOp` | Already inactive is no-op |
| `deactivate_whenSuspended_throws` | Cannot deactivate suspended |
| `suspend_fromActive_suspends` | Active -> Suspended |
| `suspend_whenSuspended_throws` | Already suspended throws |
| `unsuspend_fromSuspended_activates` | Suspended -> Active |
| `unsuspend_whenNotSuspended_throws` | Not suspended throws |
| **Reconstitution** |
| `reconstitute_preservesAllFields` | Reconstitute from persistence |

### 2.2 IndirectClient Aggregate Unit Tests

#### Test Class: `IndirectClientTest`

| Test Case | Description |
|-----------|-------------|
| **Creation** |
| `create_validInput_createsClient` | Create with required fields |
| `create_withExternalReference` | Create with external reference |
| `create_blankBusinessName_throws` | Blank name throws exception |
| `create_setsPendingStatus` | New client is PENDING |
| **Related Persons** |
| `addRelatedPerson_success` | Add person to client |
| `addRelatedPerson_autoActivates` | Adding person activates BUSINESS client |
| `addRelatedPerson_whenSuspended_throws` | Cannot add to suspended client |
| `removeRelatedPerson_success` | Remove person from client |
| `removeRelatedPerson_lastPerson_throws` | Cannot remove last person from BUSINESS |
| `removeRelatedPerson_whenSuspended_throws` | Cannot remove from suspended client |
| `updateRelatedPerson_success` | Update person details |
| `updateRelatedPerson_notFound_throws` | Person not found throws |
| `updateRelatedPerson_whenSuspended_throws` | Cannot update on suspended client |
| **Status Changes** |
| `activate_withRelatedPerson_activates` | Activate when has persons |
| `activate_noRelatedPerson_throws` | Cannot activate BUSINESS without persons |
| `activate_whenActive_noOp` | Already active is no-op |
| `suspend_fromActive_suspends` | Active -> Suspended |
| `suspend_whenSuspended_throws` | Already suspended throws |
| **Reconstitution** |
| `reconstitute_preservesAllFields` | Reconstitute from persistence |
| `reconstitute_preservesRelatedPersons` | Persons preserved on reconstitute |

### 2.3 ClientAccount Unit Tests

#### Test Class: `ClientAccountTest`

| Test Case | Description |
|-----------|-------------|
| `createBankAccount_validInput_creates` | Create bank account |
| `createOfiAccount_validInput_creates` | Create OFI account |
| `createOfiAccount_withIndirectClient` | OFI with indirect client link |
| `status_transitions` | Test status change methods |
| `reconstitute_preservesFields` | Reconstitute from persistence |

---

## Module 3: domain/profiles (PASSING)

**Current Coverage:** 99.6% line, 89.4% branch :white_check_mark:

Existing tests cover:
- Profile creation (all types)
- Client enrollment management
- Account enrollment
- Service enrollment
- Profile lifecycle (suspend, reactivate)
- Secondary client management
- Reconstitution
- Profile detail queries
- Search methods

---

## Module 4: domain/users

**Location:** `domain/users/src/main/java/com/knight/domain/users/`

### Unit Tests Required

#### Test Class: `UserTest`

| Test Case | Description |
|-----------|-------------|
| **Creation** |
| `create_validInput_createsUser` | Create user with valid data |
| `create_invalidEmail_throws` | Invalid email throws exception |
| `create_noRoles_throws` | Empty roles throws exception |
| `create_setsPendingCreationStatus` | New user is PENDING_CREATION |
| **Provisioning** |
| `markProvisioned_fromPending_transitions` | PENDING_CREATION -> PENDING_VERIFICATION |
| `markProvisioned_notPending_throws` | Must be PENDING_CREATION |
| `markProvisioned_setsIdpUserId` | Sets identity provider user ID |
| **Onboarding Status** |
| `updateOnboardingStatus_passwordOnly_pendingMfa` | Password set -> PENDING_MFA |
| `updateOnboardingStatus_mfaOnly_staysPending` | MFA only stays PENDING_VERIFICATION |
| `updateOnboardingStatus_both_active` | Both set -> ACTIVE |
| **Activation/Deactivation** |
| `activate_success` | Activate user |
| `activate_whenLocked_throws` | Cannot activate locked user |
| `activate_whenActive_noOp` | Already active is no-op |
| `deactivate_success` | Deactivate with reason |
| `deactivate_whenDeactivated_noOp` | Already deactivated is no-op |
| **Lock/Unlock** |
| `lock_withReason_locks` | Lock user with reason |
| `lock_blankReason_throws` | Blank reason throws exception |
| `lock_whenLocked_noOp` | Already locked is no-op |
| `unlock_fromLocked_unlocks` | Unlock user |
| `unlock_notLocked_throws` | Not locked throws exception |
| **Role Management** |
| `addRole_success` | Add role to user |
| `addRole_duplicate_noOp` | Adding existing role is no-op |
| `removeRole_success` | Remove role from user |
| `removeRole_lastRole_throws` | Cannot remove last role |
| **Name Update** |
| `updateName_success` | Update first and last name |
| **Reconstitution** |
| `reconstitute_preservesAllFields` | Reconstitute from persistence |

#### Test Class: `UserApplicationServiceTest`

| Test Case | Description |
|-----------|-------------|
| `createUser_success` | Create user via service |
| `createUser_duplicateEmail_throws` | Duplicate email in profile throws |
| `provisionUser_success` | Provision to IdP |
| `provisionUser_notPending_throws` | User not in PENDING_CREATION throws |
| `activateUser_success` | Activate user |
| `deactivateUser_success` | Deactivate user |
| `lockUser_success` | Lock user |
| `unlockUser_success` | Unlock user |
| `addRole_success` | Add role via service |
| `removeRole_success` | Remove role via service |
| `getUsersByProfile_returnsUsers` | Query users by profile |
| `getUserById_returnsUser` | Query user by ID |

---

## Module 5: domain/batch

**Location:** `domain/batch/src/main/java/com/knight/domain/batch/`

### Unit Tests Required

#### Test Class: `BatchTest`

| Test Case | Description |
|-----------|-------------|
| **Creation** |
| `create_validInput_createsBatch` | Create batch with valid data |
| `create_setsPendingStatus` | New batch is PENDING |
| **Adding Items** |
| `addItem_success` | Add item to batch |
| `addItem_setsSequenceNumber` | Items get sequential numbers |
| `addItem_whenNotPending_throws` | Cannot add to started batch |
| **Starting Batch** |
| `start_success` | Start batch processing |
| `start_noItems_throws` | Cannot start empty batch |
| `start_notPending_throws` | Can only start PENDING batches |
| `start_setsStartedAt` | Sets startedAt timestamp |
| **Processing Items** |
| `incrementSuccess_success` | Increment success count |
| `incrementSuccess_notInProgress_throws` | Must be IN_PROGRESS |
| `incrementFailed_success` | Increment failed count |
| `incrementFailed_notInProgress_throws` | Must be IN_PROGRESS |
| **Completing Batch** |
| `complete_allSuccess_completed` | All success -> COMPLETED |
| `complete_someFailures_completedWithErrors` | Mixed -> COMPLETED_WITH_ERRORS |
| `complete_allFailed_failed` | All failed -> FAILED |
| `complete_notInProgress_throws` | Must be IN_PROGRESS |
| **Utility Methods** |
| `getNextPendingItem_returnsPending` | Get next pending item |
| `getNextPendingItem_noPending_returnsNull` | No pending returns null |
| `pendingCount_calculation` | Correct pending count |
| `fail_setsFailedStatus` | Fail batch with reason |
| **Reconstitution** |
| `reconstitute_preservesAllFields` | Reconstitute from persistence |

#### Test Class: `BatchItemTest`

| Test Case | Description |
|-----------|-------------|
| `create_setsPendingStatus` | New item is PENDING |
| `markInProgress_fromPending_transitions` | PENDING -> IN_PROGRESS |
| `markInProgress_notPending_throws` | Must be PENDING |
| `markSuccess_fromInProgress_transitions` | IN_PROGRESS -> SUCCESS |
| `markSuccess_setsResultData` | Result data stored |
| `markSuccess_notInProgress_throws` | Must be IN_PROGRESS |
| `markFailed_fromInProgress_transitions` | IN_PROGRESS -> FAILED |
| `markFailed_setsErrorMessage` | Error message stored |
| `markFailed_notInProgress_throws` | Must be IN_PROGRESS |

#### Test Class: `PayorEnrolmentServiceTest`

| Test Case | Description |
|-----------|-------------|
| `validatePayors_allValid_success` | All valid payors pass |
| `validatePayors_duplicateBusinessName_error` | Duplicate name detected |
| `validatePayors_missingRequiredFields_error` | Missing fields detected |
| `executeEnrolment_success` | Execute batch enrolment |
| `executeEnrolment_partialFailure_completesWithErrors` | Some items fail |

---

## Module 6: domain/policy

**Location:** `domain/policy/src/main/java/com/knight/domain/policy/`

### Unit Tests Required

#### Test Class: `PermissionPolicyTest`

| Test Case | Description |
|-----------|-------------|
| **Creation** |
| `create_validInput_createsPolicy` | Create custom policy |
| `create_nullProfileId_throws` | ProfileId required for custom |
| `create_nullSubject_throws` | Subject required |
| `create_nullAction_throws` | Action required |
| `create_defaultsResource` | Null resource defaults to all |
| `create_defaultsEffect` | Null effect defaults to ALLOW |
| **System Policies** |
| `forSecurityAdmin_createsSecurityPolicy` | Security admin policies |
| `forServiceAdmin_createsFullAccessPolicy` | Service admin has full access |
| `forReader_createsViewPolicy` | Reader has view only |
| `forCreator_createsCRUDPolicies` | Creator has create/update/delete |
| `forApprover_createsApprovePolicy` | Approver can approve |
| **Policy Matching** |
| `matches_exactAction_returnsTrue` | Exact action matches |
| `matches_wildcardAction_returnsTrue` | Wildcard action matches |
| `matches_noMatch_returnsFalse` | Non-matching returns false |
| `matchesAction_ignoresResource` | Action match ignores resource |
| `appliesTo_sameSubject_returnsTrue` | Same subject matches |
| `appliesTo_differentSubject_returnsFalse` | Different subject doesn't match |
| `appliesToAny_oneMatches_returnsTrue` | At least one subject matches |
| **Update** |
| `update_customPolicy_success` | Update custom policy |
| `update_systemPolicy_throws` | Cannot update system policy |
| **Reconstitution** |
| `reconstitute_preservesAllFields` | Reconstitute from persistence |

#### Test Class: `SubjectTest`

| Test Case | Description |
|-----------|-------------|
| `user_createsUserSubject` | Subject.user(userId) |
| `role_createsRoleSubject` | Subject.role(roleName) |
| `userGroup_createsGroupSubject` | Subject.userGroup(groupId) |
| `equality` | Same type and value = equal |

#### Test Class: `ActionTest`

| Test Case | Description |
|-----------|-------------|
| `of_createsAction` | Action.of("service.action") |
| `matches_exactMatch_true` | Exact action matches |
| `matches_wildcardAll_true` | "*" matches everything |
| `matches_wildcardSuffix_true` | "service.*" matches "service.view" |
| `matches_noMatch_false` | Non-matching returns false |

#### Test Class: `ResourceTest`

| Test Case | Description |
|-----------|-------------|
| `all_matchesEverything` | Resource.all() matches any resource |
| `of_specificResource` | Resource.of("account:123") |
| `matches_sameResource_true` | Same resource matches |
| `matches_differentResource_false` | Different resource doesn't match |

#### Test Class: `PermissionAuthorizationServiceTest`

| Test Case | Description |
|-----------|-------------|
| `authorize_allowed_returnsTrue` | User has permission |
| `authorize_denied_returnsFalse` | User lacks permission |
| `authorize_denyOverridesAllow` | DENY takes precedence |
| `getEffectivePermissions_returnsAll` | Returns all applicable policies |

---

## Module 7: application

**Location:** `application/src/main/java/com/knight/application/`

### 7.1 E2E Tests (Already Implemented)

- `UserControllerE2ETest` - 22 tests
- `IndirectClientControllerE2ETest` - 22 tests
- `BatchImportE2ETest` - 12 tests
- `ServiceEnrollmentE2ETest` - 11 tests
- `ProfileControllerE2ETest` - 39 tests

**Total E2E Tests:** 106 (all passing)

### 7.2 Mapper Unit Tests Required

#### Test Class: `ClientMapperTest`

| Test Case | Description |
|-----------|-------------|
| `toEntity_mapsAllFields` | Domain -> Entity |
| `toDomain_mapsAllFields` | Entity -> Domain |
| `toEntity_nullInput_returnsNull` | Null handling |
| `toDomain_nullInput_returnsNull` | Null handling |

#### Test Class: `UserMapperTest`

| Test Case | Description |
|-----------|-------------|
| `toEntity_mapsAllFields` | Domain -> Entity |
| `toDomain_mapsAllFields` | Entity -> Domain |
| `toEntity_mapsRoles` | Roles mapped correctly |
| `toDomain_mapsRoles` | Roles mapped correctly |

#### Test Class: `ServicingProfileMapperTest`

| Test Case | Description |
|-----------|-------------|
| `toEntity_mapsAllFields` | Domain -> Entity |
| `toDomain_mapsAllFields` | Entity -> Domain |
| `toEntity_mapsEnrollments` | All enrollments mapped |
| `toDomain_mapsEnrollments` | All enrollments mapped |

#### Test Class: `IndirectClientMapperTest`

| Test Case | Description |
|-----------|-------------|
| `toEntity_mapsAllFields` | Domain -> Entity |
| `toDomain_mapsAllFields` | Entity -> Domain |
| `toEntity_mapsRelatedPersons` | Persons mapped correctly |
| `toDomain_mapsRelatedPersons` | Persons mapped correctly |

#### Test Class: `BatchMapperTest`

| Test Case | Description |
|-----------|-------------|
| `toEntity_mapsAllFields` | Domain -> Entity |
| `toDomain_mapsAllFields` | Entity -> Domain |
| `toEntity_mapsItems` | Items mapped correctly |
| `toDomain_mapsItems` | Items mapped correctly |

### 7.3 Repository Adapter Tests

These are integration tests using H2:

#### Test Class: `ClientRepositoryAdapterTest`

| Test Case | Description |
|-----------|-------------|
| `save_newClient_persists` | Save new client |
| `save_existingClient_updates` | Update existing client |
| `findById_exists_returnsClient` | Find by ID |
| `findById_notExists_returnsEmpty` | Not found returns empty |
| `searchByName_findsMatches` | Search by name |

Similar tests for:
- `UserRepositoryAdapterTest`
- `ProfileRepositoryAdapterTest`
- `IndirectClientRepositoryAdapterTest`
- `BatchRepositoryAdapterTest`

---

## Module 8: employee-portal

**Location:** `employee-portal/src/main/java/com/knight/portal/`

### Minimal Testing Strategy

Employee portal is a Vaadin UI application. Testing focuses on:

1. **Service Layer Unit Tests** - Test service classes that call REST APIs
2. **Security Tests** - Test JWT validation and authentication

#### Test Class: `ClientServiceTest`

| Test Case | Description |
|-----------|-------------|
| `searchClients_callsApi` | Verify API call |
| `getClientDetail_callsApi` | Verify API call |
| `handleError_returnsEmpty` | Error handling |

#### Test Class: `JwtValidatorTest`

| Test Case | Description |
|-----------|-------------|
| `validate_validToken_returnsUser` | Valid JWT parsed |
| `validate_expiredToken_throws` | Expired token rejected |
| `validate_invalidSignature_throws` | Invalid signature rejected |

---

## Test Execution Plan

### Phase 1: Unit Tests (Current Sprint)

1. **kernel** - All value object tests
2. **domain/clients** - Client, IndirectClient, ClientAccount tests
3. **domain/users** - User aggregate and service tests
4. **domain/batch** - Batch aggregate and service tests
5. **domain/policy** - PermissionPolicy and service tests

### Phase 2: Integration Tests

1. **application** - Mapper tests
2. **application** - Repository adapter tests (with H2)

### Phase 3: Coverage Gap Analysis

After unit tests:
```bash
mvn clean verify -pl kernel,domain/clients,domain/users,domain/batch,domain/policy,application
mvn jacoco:report-aggregate
```

Review coverage reports and add targeted tests for uncovered branches.

---

## Coverage Verification Commands

```bash
# Run all tests with coverage
mvn clean verify

# Generate individual module reports
mvn jacoco:report -pl domain/clients
mvn jacoco:report -pl domain/users
mvn jacoco:report -pl domain/batch
mvn jacoco:report -pl domain/policy
mvn jacoco:report -pl kernel
mvn jacoco:report -pl application

# View reports at: {module}/target/site/jacoco/index.html
```

---

## Summary: Tests to Create

| Module | Test Classes | Estimated Tests |
|--------|--------------|-----------------|
| kernel | 5 | ~30 |
| domain/clients | 3 | ~45 |
| domain/users | 2 | ~30 |
| domain/batch | 3 | ~35 |
| domain/policy | 5 | ~40 |
| application (mappers) | 5 | ~25 |
| application (repos) | 5 | ~25 |
| employee-portal | 2 | ~10 |
| **Total** | **30** | **~240** |

Combined with existing tests:
- E2E tests: 106
- Profile unit tests: 93
- **New tests: ~240**
- **Grand total: ~439 tests**

---

## Current Status Summary

### Test Count (as of 2024-12-20)

| Module | Tests | Status |
|--------|-------|--------|
| kernel | 116 | :white_check_mark: |
| domain/clients | 105 | :white_check_mark: |
| domain/profiles | 93 | :white_check_mark: |
| domain/users | 67 | :white_check_mark: |
| domain/batch | 75 | :white_check_mark: |
| domain/policy | 72 | :white_check_mark: |
| application | 217 | :white_check_mark: |
| employee-portal | 19 | :white_check_mark: |
| **Total** | **764** | All passing |

### Coverage Gap Analysis

Modules needing improvement to reach 80% line and 80% branch coverage:

| Module | Line Gap | Branch Gap | Priority |
|--------|----------|------------|----------|
| `application` | +9.2% (~151 lines) | +26.6% (~87 branches) | HIGH |
| `domain/policy` | +19.1% (78 lines) | +19.1% (29 branches) | HIGH |
| `kernel` | +16.0% (53 lines) | +25.9% (58 branches) | HIGH |
| `domain/clients` | +4.4% (16 lines) | +19.8% (21 branches) | MEDIUM |

### Recommended Actions

1. **application** - Add tests for:
   - Mapper unit tests (ClientMapper, UserMapper, ProfileMapper, etc.)
   - Repository adapter tests
   - REST controller error handling paths
   - Service layer edge cases

2. **domain/policy** - Add tests for:
   - PermissionPolicy matching logic
   - Subject/Action/Resource value objects
   - PermissionAuthorizationService edge cases

3. **kernel** - Add tests for:
   - GradsAccountValidator edge cases
   - Address validation edge cases
   - ClientAccountId parsing

4. **domain/clients** - Add tests for:
   - IndirectClient aggregate status transitions
   - ClientAccount edge cases

---

## Coverage Report Location

Coverage reports are generated in:
```
coverage-report/target/site/jacoco-aggregate/index.html
```

To regenerate:
```bash
./mvnw clean test jacoco:report
```
