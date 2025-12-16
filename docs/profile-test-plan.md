# Profile Feature Test Plan

## Overview

This document outlines the comprehensive testing strategy for the Profile feature, targeting **80% line coverage and 80% branch coverage** using JaCoCo. The plan is organized in phases, prioritizing functional/integration tests before filling coverage gaps with unit tests.

## Test Environment Setup

### Existing Infrastructure
- **E2E Tests**: `@SpringBootTest` with `MockMvc` and H2 in-memory database
- **Test Profiles**: `application-test.yml`, `application-h2.yml`, `application-e2e.yml`
- **Dependencies**: JUnit 5, AssertJ, Testcontainers, H2, Awaitility
- **Coverage Tool**: JaCoCo (configured in parent pom.xml)

### H2 Database Configuration
```java
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MSSQLServer",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "spring.kafka.listener.auto-startup=false"
})
```

---

## Phase 1: REST API E2E Tests (Functional)

### Objective
Test complete request/response flows through ProfileController with real H2 repositories and domain services.

### Test Class: `ProfileControllerE2ETest.java`
Location: `application/src/test/java/com/knight/application/rest/serviceprofiles/`

### Test Cases

#### 1.1 Create Profile - Happy Path

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldCreateServicingProfileWithManualAccountEnrollment` | Create SERVICING profile with specific accounts | 201 Created, profileId returned |
| `shouldCreateServicingProfileWithAutomaticAccountEnrollment` | Create SERVICING profile with AUTOMATIC enrollment | 201 Created, all active accounts enrolled |
| `shouldCreateOnlineProfile` | Create ONLINE profile | 201 Created, profile type = ONLINE |
| `shouldCreateProfileWithDefaultName` | Create profile without name, should use client name | 201 Created, name = primary client name |
| `shouldCreateProfileWithCustomName` | Create profile with custom name | 201 Created, name = provided name |
| `shouldCreateProfileWithMultipleClients` | Primary + secondary clients | 201 Created, both clients enrolled |

#### 1.2 Create Profile - Validation Errors

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldRejectProfileWithNoPrimaryClient` | No client marked as primary | 400 Bad Request |
| `shouldRejectProfileWithMultiplePrimaryClients` | Two clients marked as primary | 400 Bad Request |
| `shouldRejectServicingProfileWhenPrimaryAlreadyExists` | Primary client already in another servicing profile | 400 Bad Request |
| `shouldAllowOnlineProfileWithSamePrimaryClient` | Same client can be primary in multiple ONLINE profiles | 201 Created |
| `shouldRejectInvalidProfileType` | Invalid profile type enum | 400 Bad Request |
| `shouldRejectInvalidEnrollmentType` | Invalid enrollment type enum | 400 Bad Request |

#### 1.3 Get Profile by ID

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldGetProfileById` | Fetch existing profile | 200 OK, full profile summary |
| `shouldReturn404ForNonExistingProfile` | Fetch non-existing profile | 404 Not Found |

#### 1.4 Get Profiles by Client (Client-centric endpoints)

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldGetAllProfilesForClient` | Client enrolled in multiple profiles | List of all profiles |
| `shouldGetPrimaryProfilesForClient` | Client is primary in some profiles | List of primary profiles |
| `shouldGetSecondaryProfilesForClient` | Client is secondary in some profiles | List of secondary profiles |
| `shouldGetServicingProfilesForClient` | Filter by SERVICING type | Only SERVICING profiles |
| `shouldGetOnlineProfilesForClient` | Filter by ONLINE type | Only ONLINE profiles |
| `shouldReturnEmptyListForClientWithNoProfiles` | Client not in any profile | Empty list |

---

## Phase 2: Domain Layer Tests

### Objective
Test Profile aggregate business logic and invariants.

### Test Class: `ProfileTest.java`
Location: `domain/profiles/src/test/java/com/knight/domain/serviceprofiles/aggregate/`

### Test Cases

#### 2.1 Profile Creation

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldCreateProfileWithAccounts` | Valid creation with accounts | Profile created with PENDING status |
| `shouldGenerateProfileIdFromPrimaryClient` | Profile ID derived from primary client | profileId contains primary clientId |
| `shouldRejectNullProfileType` | Null profile type | NullPointerException |
| `shouldRejectNullName` | Null name | NullPointerException |
| `shouldRejectNullCreatedBy` | Null createdBy | NullPointerException |

#### 2.2 Client Enrollment Validation

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldRequireExactlyOnePrimaryClient` | Zero primary clients | IllegalArgumentException |
| `shouldRejectMultiplePrimaryClients` | Two primary clients | IllegalArgumentException |
| `shouldEnrollMultipleSecondaryClients` | One primary + multiple secondary | All enrolled |

#### 2.3 Account Enrollment

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldEnrollAccountToProfile` | Profile-level enrollment | Account enrolled, isProfileLevel = true |
| `shouldEnrollAccountToService` | Service-level enrollment | Account enrolled, isServiceLevel = true |
| `shouldRejectDuplicateProfileLevelEnrollment` | Same account enrolled twice to profile | IllegalArgumentException |
| `shouldRejectServiceEnrollmentWithoutProfileEnrollment` | Account not enrolled to profile first | IllegalArgumentException |
| `shouldRejectEnrollmentWhenProfileSuspended` | Enroll to suspended profile | IllegalStateException |

#### 2.4 Service Enrollment

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldEnrollService` | Valid service enrollment | Service enrolled, ACTIVE status |
| `shouldActivateProfileOnFirstServiceEnrollment` | Profile pending, first service enrolled | Profile status = ACTIVE |
| `shouldRejectServiceEnrollmentWhenClosed` | Enroll service to closed profile | IllegalStateException |

#### 2.5 Profile Lifecycle

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldSuspendProfile` | Suspend active profile | Status = SUSPENDED |
| `shouldReactivateSuspendedProfile` | Reactivate suspended profile | Status = ACTIVE |
| `shouldRejectSuspendClosedProfile` | Suspend closed profile | IllegalStateException |
| `shouldRejectReactivateNonSuspendedProfile` | Reactivate active profile | IllegalStateException |

#### 2.6 Derived Properties

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldReturnPrimaryClientId` | Get primary client from enrollments | Correct clientId returned |
| `shouldThrowIfNoPrimaryClient` | Profile somehow has no primary | IllegalStateException |

---

## Phase 3: Persistence Layer Tests

### Objective
Test JPA entities, mappers, and repository queries.

### Test Class: `ProfileRepositoryAdapterTest.java`
Location: `application/src/test/java/com/knight/application/persistence/profiles/repository/`

### Test Cases

#### 3.1 Save and Retrieve

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldSaveAndRetrieveProfile` | Round-trip save/load | All fields preserved |
| `shouldPersistClientEnrollments` | Save profile with client enrollments | Client enrollments persisted |
| `shouldPersistAccountEnrollments` | Save profile with account enrollments | Account enrollments persisted |
| `shouldPersistServiceEnrollments` | Save profile with service enrollments | Service enrollments persisted |

#### 3.2 Query Methods

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldFindByPrimaryClient` | Query by primary client | Correct profiles returned |
| `shouldFindBySecondaryClient` | Query by secondary client | Correct profiles returned |
| `shouldFindByClient` | Query by any client enrollment | All related profiles returned |
| `shouldCheckExistsServicingProfileWithPrimaryClient` | Uniqueness check | true/false correctly |

### Test Class: `ServicingProfileMapperTest.java`
Location: `application/src/test/java/com/knight/application/persistence/profiles/mapper/`

### Test Cases

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldMapDomainToEntity` | Profile → ProfileEntity | All fields mapped |
| `shouldMapEntityToDomain` | ProfileEntity → Profile | Domain object reconstructed |
| `shouldMapClientEnrollments` | ClientEnrollment mapping | Bidirectional mapping works |
| `shouldMapAccountEnrollmentsWithNullServiceEnrollmentId` | Profile-level accounts | serviceEnrollmentId = null preserved |
| `shouldMapAccountEnrollmentsWithServiceEnrollmentId` | Service-level accounts | serviceEnrollmentId preserved |

---

## Phase 4: Application Service Tests

### Objective
Test ProfileApplicationService orchestration and validation logic.

### Test Class: `ProfileApplicationServiceTest.java`
Location: `domain/profiles/src/test/java/com/knight/domain/serviceprofiles/service/`

### Test Cases

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldCreateProfileWithAccountsCommand` | Execute create command | Profile saved, event published |
| `shouldResolveAccountsForAutomaticEnrollment` | AUTOMATIC type fetches all active accounts | All active accounts enrolled |
| `shouldUseClientNameWhenProfileNameBlank` | Empty name defaults to client name | Profile name = client name |
| `shouldValidatePrimaryClientUniquenessForServicing` | Check uniqueness constraint | Exception if duplicate |
| `shouldPublishProfileCreatedEvent` | Event publishing | ProfileCreated event published |

---

## Phase 5: Vaadin UI Testing Strategy

### Testing Approach for Vaadin Components

Vaadin testing can be done at multiple levels:

#### 5.1 Unit Tests with Vaadin TestBench Lite (Recommended for MVP)

**No additional dependencies needed** - Test view logic by:
- Mocking services
- Instantiating views directly
- Asserting component state

```java
@ExtendWith(MockitoExtension.class)
class CreateProfileViewTest {
    @Mock private ClientService clientService;
    @Mock private ProfileService profileService;

    @Test
    void shouldToggleAccountsSectionVisibility() {
        CreateProfileView view = new CreateProfileView(clientService, profileService);
        // Test component logic
    }
}
```

#### 5.2 Integration Tests with Karibu-Testing

**Add to employee-portal/pom.xml:**
```xml
<dependency>
    <groupId>com.github.mvysny.kaributesting</groupId>
    <artifactId>karibu-testing-v24</artifactId>
    <version>2.1.8</version>
    <scope>test</scope>
</dependency>
```

Allows testing Vaadin components without a browser:
```java
@KaribuTest
class CreateProfileViewKaribuTest {
    @Test
    void shouldNavigateBackOnCancel() {
        UI.getCurrent().navigate(CreateProfileView.class, "srf_123/service");
        _click(_get(Button.class, spec -> spec.withCaption("Cancel")));
        // Assert navigation
    }
}
```

#### 5.3 E2E Browser Tests with TestBench (Future)

Full browser automation - typically for critical user flows.

### Portal Test Cases

#### Test Class: `CreateProfileViewTest.java`
Location: `employee-portal/src/test/java/com/knight/portal/views/`

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldParseClientIdFromUrl` | URL parameter extraction | clientId parsed correctly |
| `shouldLoadClientAccounts` | Accounts displayed in grid | Grid populated with active accounts |
| `shouldToggleAccountsSectionOnEnrollmentTypeChange` | MANUAL shows accounts grid | Section visibility toggled |
| `shouldCreateProfileOnSubmit` | Submit valid form | ProfileService.createProfile called |
| `shouldShowErrorNotificationOnFailure` | API error | Error notification shown |
| `shouldNavigateBackOnCancel` | Cancel button | Navigation to client detail |

#### Test Class: `ProfileServiceTest.java`
Location: `employee-portal/src/test/java/com/knight/portal/services/`

| Test Case | Description | Expected Result |
|-----------|-------------|-----------------|
| `shouldGetClientProfiles` | REST client call | Profiles returned |
| `shouldCreateProfile` | POST to /api/profiles | CreateProfileResponse returned |
| `shouldHandleApiErrors` | Server error | RuntimeException thrown |

---

## Phase 6: JaCoCo Configuration and Coverage Analysis

### Step 1: Configure JaCoCo in application/pom.xml

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
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

### Step 2: Run Coverage Analysis

```bash
./mvnw clean verify -DskipTests=false
```

Reports generated at: `target/site/jacoco/index.html`

### Step 3: Identify Coverage Gaps

After running functional tests, analyze JaCoCo report for:
- Uncovered lines in Profile aggregate
- Uncovered branches in validation logic
- Missed edge cases in mapper

---

## Phase 7: Unit Tests for Coverage Gaps

After functional tests, create targeted unit tests for remaining gaps.

### Potential Gap Areas

| Component | Likely Gaps | Unit Test Approach |
|-----------|-------------|-------------------|
| `Profile.java` | Edge cases in validation | Test each validation branch |
| `ProfileApplicationService` | Error handling paths | Mock failures |
| `ServicingProfileMapper` | Null handling, reconstruction | Edge case inputs |
| `ProfileController` | Exception handling | MockMvc with error scenarios |
| `ClientEnrollment` | Constructor edge cases | Direct instantiation tests |
| `AccountEnrollment` | Status transitions | State machine tests |

---

## Execution Order

### Stage 1: Infrastructure Setup (0.5 day)
1. Configure JaCoCo in pom.xml files
2. Add Karibu-Testing dependency to employee-portal
3. Create base test classes and test utilities

### Stage 2: E2E REST API Tests (1 day)
1. Create `ProfileControllerE2ETest.java`
2. Implement all happy path tests
3. Implement all error handling tests
4. Run and verify tests pass

### Stage 3: Domain Layer Tests (0.5 day)
1. Create `ProfileTest.java`
2. Test all aggregate behavior
3. Test all invariants

### Stage 4: Persistence Layer Tests (0.5 day)
1. Create `ProfileRepositoryAdapterTest.java`
2. Create `ServicingProfileMapperTest.java`
3. Test queries and mappings

### Stage 5: Initial Coverage Analysis (0.25 day)
1. Run `./mvnw clean verify`
2. Review JaCoCo report
3. Document coverage gaps

### Stage 6: Vaadin Tests (0.5 day)
1. Create `CreateProfileViewTest.java`
2. Create `ProfileServiceTest.java`
3. Test critical UI logic

### Stage 7: Gap-filling Unit Tests (0.5 day)
1. Target uncovered lines
2. Target uncovered branches
3. Re-run coverage to verify 80%

---

## Test File Structure

```
application/
└── src/test/java/com/knight/application/
    ├── rest/serviceprofiles/
    │   └── ProfileControllerE2ETest.java
    └── persistence/profiles/
        ├── repository/ProfileRepositoryAdapterTest.java
        └── mapper/ServicingProfileMapperTest.java

domain/profiles/
└── src/test/java/com/knight/domain/serviceprofiles/
    ├── aggregate/ProfileTest.java
    └── service/ProfileApplicationServiceTest.java

employee-portal/
└── src/test/java/com/knight/portal/
    ├── views/CreateProfileViewTest.java
    └── services/ProfileServiceTest.java
```

---

## Success Criteria

- [ ] All E2E tests pass
- [ ] All domain tests pass
- [ ] All persistence tests pass
- [ ] All Vaadin tests pass
- [ ] Line coverage ≥ 80%
- [ ] Branch coverage ≥ 80%
- [ ] No critical bugs found
- [ ] Tests run in CI pipeline

---

## Appendix: Test Data Fixtures

### Sample Test Clients

```java
private Client createTestClient(String id, String name) {
    return Client.create(
        new SrfClientId(id),
        name,
        Client.ClientType.BUSINESS,
        Address.of("123 Main St", null, "Toronto", "ON", "M1A 1A1", "CA")
    );
}
```

### Sample Test Accounts

```java
private ClientAccount createTestAccount(ClientId clientId, String suffix) {
    return ClientAccount.create(
        ClientAccountId.of("acc:" + suffix),
        clientId,
        Currency.CAD
    );
}
```

### Sample Create Profile Request

```json
{
    "profileType": "SERVICING",
    "name": "Test Profile",
    "clients": [
        {
            "clientId": "srf:123456789",
            "isPrimary": true,
            "accountEnrollmentType": "MANUAL",
            "accountIds": ["acc:001", "acc:002"]
        }
    ]
}
```
