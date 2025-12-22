package com.knight.domain.serviceprofiles.aggregate;

import com.knight.domain.serviceprofiles.aggregate.Profile.*;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileStatus;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.platform.sharedkernel.*;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Profile aggregate.
 * Tests business logic and invariants without Spring context.
 */
class ProfileTest {

    private static final ClientId PRIMARY_CLIENT_ID = new SrfClientId("123456789");
    private static final ClientId SECONDARY_CLIENT_ID = new SrfClientId("987654321");
    private static final ClientAccountId ACCOUNT_ID_1 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000001");
    private static final ClientAccountId ACCOUNT_ID_2 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000002");

    // ==================== Profile Creation ====================

    @Nested
    @DisplayName("Profile Creation")
    class ProfileCreationTests {

        @Test
        @DisplayName("should create profile with accounts")
        void shouldCreateProfileWithAccounts() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of(ACCOUNT_ID_1, ACCOUNT_ID_2)
                )
            );

            Profile profile = Profile.createWithAccounts(
                ProfileType.SERVICING,
                "Test Profile",
                requests,
                "testUser"
            );

            assertThat(profile.profileId()).isNotNull();
            assertThat(profile.name()).isEqualTo("Test Profile");
            assertThat(profile.profileType()).isEqualTo(ProfileType.SERVICING);
            assertThat(profile.status()).isEqualTo(ProfileStatus.PENDING);
            assertThat(profile.createdBy()).isEqualTo("testUser");
            assertThat(profile.clientEnrollments()).hasSize(1);
            assertThat(profile.accountEnrollments()).hasSize(2);
        }

        @Test
        @DisplayName("should generate profile ID from primary client")
        void shouldGenerateProfileIdFromPrimaryClient() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )
            );

            Profile profile = Profile.createWithAccounts(
                ProfileType.SERVICING,
                "Test Profile",
                requests,
                "testUser"
            );

            assertThat(profile.profileId().urn()).contains("srf:123456789");
        }

        @Test
        @DisplayName("should reject null profile type")
        void shouldRejectNullProfileType() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )
            );

            assertThatNullPointerException()
                .isThrownBy(() -> Profile.createWithAccounts(null, "Test", requests, "user"));
        }

        @Test
        @DisplayName("should reject null name")
        void shouldRejectNullName() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )
            );

            assertThatNullPointerException()
                .isThrownBy(() -> Profile.createWithAccounts(ProfileType.SERVICING, null, requests, "user"));
        }

        @Test
        @DisplayName("should reject null createdBy")
        void shouldRejectNullCreatedBy() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )
            );

            assertThatNullPointerException()
                .isThrownBy(() -> Profile.createWithAccounts(ProfileType.SERVICING, "Test", requests, null));
        }
    }

    // ==================== Client Enrollment Validation ====================

    @Nested
    @DisplayName("Client Enrollment Validation")
    class ClientEnrollmentValidationTests {

        @Test
        @DisplayName("should require exactly one primary client")
        void shouldRequireExactlyOnePrimaryClient() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    false,  // Not primary
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )
            );

            assertThatIllegalArgumentException()
                .isThrownBy(() -> Profile.createWithAccounts(
                    ProfileType.SERVICING,
                    "Test",
                    requests,
                    "user"
                ))
                .withMessageContaining("primary");
        }

        @Test
        @DisplayName("should reject multiple primary clients")
        void shouldRejectMultiplePrimaryClients() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                ),
                new ClientEnrollmentRequest(
                    SECONDARY_CLIENT_ID,
                    true,  // Also primary - invalid
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )
            );

            assertThatIllegalArgumentException()
                .isThrownBy(() -> Profile.createWithAccounts(
                    ProfileType.SERVICING,
                    "Test",
                    requests,
                    "user"
                ))
                .withMessageContaining("primary");
        }

        @Test
        @DisplayName("should enroll multiple secondary clients")
        void shouldEnrollMultipleSecondaryClients() {
            ClientId thirdClient = new SrfClientId("111111111");

            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                ),
                new ClientEnrollmentRequest(
                    SECONDARY_CLIENT_ID,
                    false,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                ),
                new ClientEnrollmentRequest(
                    thirdClient,
                    false,
                    AccountEnrollmentType.MANUAL,
                    List.of()
                )
            );

            Profile profile = Profile.createWithAccounts(
                ProfileType.SERVICING,
                "Test",
                requests,
                "user"
            );

            assertThat(profile.clientEnrollments()).hasSize(3);
            assertThat(profile.clientEnrollments().stream()
                .filter(ClientEnrollment::isPrimary)
                .count()).isEqualTo(1);
        }

        @Test
        @DisplayName("should return client enrollment properties")
        void shouldReturnClientEnrollmentProperties() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.AUTOMATIC,
                    List.of()
                )
            );

            Profile profile = Profile.createWithAccounts(
                ProfileType.SERVICING,
                "Test",
                requests,
                "user"
            );

            ClientEnrollment enrollment = profile.clientEnrollments().get(0);
            assertThat(enrollment.clientId()).isEqualTo(PRIMARY_CLIENT_ID);
            assertThat(enrollment.isPrimary()).isTrue();
            assertThat(enrollment.accountEnrollmentType()).isEqualTo(AccountEnrollmentType.AUTOMATIC);
            assertThat(enrollment.enrolledAt()).isNotNull();
        }
    }

    // ==================== Account Enrollment ====================

    @Nested
    @DisplayName("Account Enrollment")
    class AccountEnrollmentTests {

        @Test
        @DisplayName("should enroll account to profile (profile-level)")
        void shouldEnrollAccountToProfile() {
            Profile profile = createBasicProfile();

            AccountEnrollment enrollment = profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);

            assertThat(enrollment.isProfileLevel()).isTrue();
            assertThat(enrollment.isServiceLevel()).isFalse();
            assertThat(enrollment.accountId()).isEqualTo(ACCOUNT_ID_1);
            assertThat(enrollment.clientId()).isEqualTo(PRIMARY_CLIENT_ID);
        }

        @Test
        @DisplayName("should enroll account to service (service-level)")
        void shouldEnrollAccountToService() {
            Profile profile = createBasicProfile();
            profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);
            ServiceEnrollment service = profile.enrollService("PAYMENT", "{}");

            AccountEnrollment enrollment = profile.enrollAccountToService(
                service.enrollmentId(),
                PRIMARY_CLIENT_ID,
                ACCOUNT_ID_1
            );

            assertThat(enrollment.isServiceLevel()).isTrue();
            assertThat(enrollment.serviceEnrollmentId()).isEqualTo(service.enrollmentId());
        }

        @Test
        @DisplayName("should reject duplicate profile-level enrollment")
        void shouldRejectDuplicateProfileLevelEnrollment() {
            Profile profile = createBasicProfile();
            profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1))
                .withMessageContaining("already enrolled");
        }

        @Test
        @DisplayName("should return account enrollment properties")
        void shouldReturnAccountEnrollmentProperties() {
            Profile profile = createBasicProfile();

            AccountEnrollment enrollment = profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);

            assertThat(enrollment.enrollmentId()).isNotNull();
            assertThat(enrollment.enrolledAt()).isNotNull();
            assertThat(enrollment.status()).isEqualTo(ProfileStatus.ACTIVE);
        }

        @Test
        @DisplayName("should suspend account enrollment")
        void shouldSuspendAccountEnrollment() {
            Profile profile = createBasicProfile();
            AccountEnrollment enrollment = profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);

            enrollment.suspend();

            assertThat(enrollment.status()).isEqualTo(ProfileStatus.SUSPENDED);
        }

        @Test
        @DisplayName("should reject service enrollment without profile enrollment")
        void shouldRejectServiceEnrollmentWithoutProfileEnrollment() {
            Profile profile = createBasicProfile();
            ServiceEnrollment service = profile.enrollService("PAYMENT", "{}");

            assertThatIllegalArgumentException()
                .isThrownBy(() -> profile.enrollAccountToService(
                    service.enrollmentId(),
                    PRIMARY_CLIENT_ID,
                    ACCOUNT_ID_1
                ))
                .withMessageContaining("enrolled to profile first");
        }
    }

    // ==================== Service Enrollment ====================

    @Nested
    @DisplayName("Service Enrollment")
    class ServiceEnrollmentTests {

        @Test
        @DisplayName("should enroll service")
        void shouldEnrollService() {
            Profile profile = createBasicProfile();

            ServiceEnrollment enrollment = profile.enrollService("PAYMENT", "{\"config\": \"value\"}");

            assertThat(enrollment.enrollmentId()).isNotNull();
            assertThat(enrollment.serviceType()).isEqualTo("PAYMENT");
            assertThat(enrollment.configuration()).isEqualTo("{\"config\": \"value\"}");
            assertThat(enrollment.status()).isEqualTo(ProfileStatus.ACTIVE);
        }

        @Test
        @DisplayName("should activate profile on first service enrollment")
        void shouldActivateProfileOnFirstServiceEnrollment() {
            Profile profile = createBasicProfile();
            assertThat(profile.status()).isEqualTo(ProfileStatus.PENDING);

            profile.enrollService("PAYMENT", "{}");

            assertThat(profile.status()).isEqualTo(ProfileStatus.ACTIVE);
        }

        @Test
        @DisplayName("should reject service enrollment when profile is suspended")
        void shouldRejectServiceEnrollmentWhenProfileSuspended() {
            Profile profile = createActiveProfile();
            profile.suspend("Test reason");

            assertThatIllegalStateException()
                .isThrownBy(() -> profile.enrollService("ANOTHER", "{}"))
                .withMessageContaining("Cannot enroll service to profile in status");
        }

        @Test
        @DisplayName("should return enrollment id and enrolled at")
        void shouldReturnServiceEnrollmentProperties() {
            Profile profile = createBasicProfile();

            ServiceEnrollment enrollment = profile.enrollService("PAYMENT", "{}");

            assertThat(enrollment.enrollmentId()).isNotNull();
            assertThat(enrollment.enrolledAt()).isNotNull();
        }

        @Test
        @DisplayName("should suspend service enrollment")
        void shouldSuspendServiceEnrollment() {
            Profile profile = createBasicProfile();
            ServiceEnrollment enrollment = profile.enrollService("PAYMENT", "{}");

            enrollment.suspend();

            assertThat(enrollment.status()).isEqualTo(ProfileStatus.SUSPENDED);
        }
    }

    // ==================== Profile Lifecycle ====================

    @Nested
    @DisplayName("Profile Lifecycle")
    class ProfileLifecycleTests {

        @Test
        @DisplayName("should suspend profile")
        void shouldSuspendProfile() {
            Profile profile = createActiveProfile();

            profile.suspend("Test reason");

            assertThat(profile.status()).isEqualTo(ProfileStatus.SUSPENDED);
        }

        @Test
        @DisplayName("should reactivate suspended profile")
        void shouldReactivateSuspendedProfile() {
            Profile profile = createActiveProfile();
            profile.suspend("Test reason");

            profile.reactivate();

            assertThat(profile.status()).isEqualTo(ProfileStatus.ACTIVE);
        }

        @Test
        @DisplayName("should reject reactivate non-suspended profile")
        void shouldRejectReactivateNonSuspendedProfile() {
            Profile profile = createActiveProfile();

            assertThatIllegalStateException()
                .isThrownBy(() -> profile.reactivate())
                .withMessageContaining("suspended");
        }

        @Test
        @DisplayName("should reject enrollment when profile suspended")
        void shouldRejectEnrollmentWhenProfileSuspended() {
            Profile profile = createActiveProfile();
            profile.suspend("Test reason");

            assertThatIllegalStateException()
                .isThrownBy(() -> profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1))
                .withMessageContaining("status");
        }

        @Test
        @DisplayName("should reject suspending closed profile")
        void shouldRejectSuspendingClosedProfile() {
            // Create a profile and use reflection to set it to CLOSED status
            Profile profile = createActiveProfile();
            // Set status to CLOSED via reflection
            try {
                java.lang.reflect.Field statusField = Profile.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(profile, ProfileStatus.CLOSED);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            assertThatIllegalStateException()
                .isThrownBy(() -> profile.suspend("Test"))
                .withMessageContaining("Cannot suspend a closed profile");
        }

        @Test
        @DisplayName("should reject enrollAccountToService when profile not active")
        void shouldRejectEnrollAccountToServiceWhenNotActive() {
            Profile profile = createActiveProfile();
            profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);
            ServiceEnrollment service = profile.enrollService("PAYMENT", "{}");
            profile.suspend("Test");

            assertThatIllegalStateException()
                .isThrownBy(() -> profile.enrollAccountToService(service.enrollmentId(), PRIMARY_CLIENT_ID, ACCOUNT_ID_1))
                .withMessageContaining("Cannot enroll account to service in status");
        }

        @Test
        @DisplayName("should reject enrollAccountToService with invalid service enrollment")
        void shouldRejectEnrollAccountToServiceWithInvalidService() {
            Profile profile = createActiveProfile();
            profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);
            // Note: Do not create a service enrollment
            EnrollmentId fakeServiceId = EnrollmentId.generate();

            assertThatIllegalArgumentException()
                .isThrownBy(() -> profile.enrollAccountToService(fakeServiceId, PRIMARY_CLIENT_ID, ACCOUNT_ID_1))
                .withMessageContaining("Service enrollment not found or not active");
        }

        @Test
        @DisplayName("should reject duplicate account enrollment to service")
        void shouldRejectDuplicateAccountEnrollmentToService() {
            Profile profile = createActiveProfile();
            profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);
            ServiceEnrollment service = profile.enrollService("PAYMENT", "{}");
            profile.enrollAccountToService(service.enrollmentId(), PRIMARY_CLIENT_ID, ACCOUNT_ID_1);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> profile.enrollAccountToService(service.enrollmentId(), PRIMARY_CLIENT_ID, ACCOUNT_ID_1))
                .withMessageContaining("Account already enrolled to service");
        }

        @Test
        @DisplayName("should return profile timestamps")
        void shouldReturnProfileTimestamps() {
            Profile profile = createBasicProfile();

            assertThat(profile.createdAt()).isNotNull();
            assertThat(profile.updatedAt()).isNotNull();
        }
    }

    // ==================== Derived Properties ====================

    @Nested
    @DisplayName("Derived Properties")
    class DerivedPropertiesTests {

        @Test
        @DisplayName("should return primary client ID")
        void shouldReturnPrimaryClientId() {
            Profile profile = createBasicProfile();

            ClientId primaryClientId = profile.primaryClientId();

            assertThat(primaryClientId).isEqualTo(PRIMARY_CLIENT_ID);
        }
    }

    // ==================== Indirect Profile Creation ====================

    @Nested
    @DisplayName("Indirect Profile Creation")
    class IndirectProfileCreationTests {

        @Test
        @DisplayName("should create indirect profile with correct ID format")
        void shouldCreateIndirectProfileWithCorrectIdFormat() {
            IndirectClientId indirectClientId = IndirectClientId.of(PRIMARY_CLIENT_ID, 1);

            Profile profile = Profile.createIndirectProfile(indirectClientId, "Payor Profile", "admin");

            assertThat(profile.profileId().urn()).isEqualTo("indirect:indirect:srf:123456789:1");
            assertThat(profile.profileType()).isEqualTo(ProfileType.INDIRECT);
            assertThat(profile.name()).isEqualTo("Payor Profile");
            assertThat(profile.createdBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("should enroll indirect client as primary")
        void shouldEnrollIndirectClientAsPrimary() {
            IndirectClientId indirectClientId = IndirectClientId.of(PRIMARY_CLIENT_ID, 5);

            Profile profile = Profile.createIndirectProfile(indirectClientId, "Test", "admin");

            assertThat(profile.clientEnrollments()).hasSize(1);
            ClientEnrollment enrollment = profile.clientEnrollments().get(0);
            assertThat(enrollment.clientId().urn()).isEqualTo("indirect:srf:123456789:5");
            assertThat(enrollment.isPrimary()).isTrue();
            assertThat(enrollment.accountEnrollmentType()).isEqualTo(AccountEnrollmentType.MANUAL);
        }

        @Test
        @DisplayName("should return indirect client as primary client ID")
        void shouldReturnIndirectClientAsPrimaryClientId() {
            IndirectClientId indirectClientId = IndirectClientId.of(PRIMARY_CLIENT_ID, 3);

            Profile profile = Profile.createIndirectProfile(indirectClientId, "Test", "admin");

            assertThat(profile.primaryClientId().urn()).isEqualTo("indirect:srf:123456789:3");
        }
    }

    // ==================== Secondary Client Management ====================

    @Nested
    @DisplayName("Secondary Client Management")
    class SecondaryClientManagementTests {

        @Test
        @DisplayName("should add secondary client with accounts")
        void shouldAddSecondaryClientWithAccounts() {
            Profile profile = createActiveProfile();

            profile.addSecondaryClient(SECONDARY_CLIENT_ID, AccountEnrollmentType.MANUAL, List.of(ACCOUNT_ID_1, ACCOUNT_ID_2));

            assertThat(profile.clientEnrollments()).hasSize(2);
            ClientEnrollment secondary = profile.clientEnrollments().stream()
                .filter(ce -> ce.clientId().equals(SECONDARY_CLIENT_ID))
                .findFirst()
                .orElseThrow();
            assertThat(secondary.isPrimary()).isFalse();
            assertThat(secondary.accountEnrollmentType()).isEqualTo(AccountEnrollmentType.MANUAL);
            assertThat(profile.accountEnrollments()).hasSize(2);
        }

        @Test
        @DisplayName("should reject duplicate secondary client")
        void shouldRejectDuplicateSecondaryClient() {
            Profile profile = createActiveProfile();
            profile.addSecondaryClient(SECONDARY_CLIENT_ID, AccountEnrollmentType.MANUAL, List.of());

            assertThatIllegalArgumentException()
                .isThrownBy(() -> profile.addSecondaryClient(SECONDARY_CLIENT_ID, AccountEnrollmentType.MANUAL, List.of()))
                .withMessageContaining("already enrolled");
        }

        @Test
        @DisplayName("should reject adding secondary client when profile suspended")
        void shouldRejectAddingSecondaryClientWhenSuspended() {
            Profile profile = createActiveProfile();
            profile.suspend("Test");

            assertThatIllegalStateException()
                .isThrownBy(() -> profile.addSecondaryClient(SECONDARY_CLIENT_ID, AccountEnrollmentType.MANUAL, List.of()))
                .withMessageContaining("Cannot add client to profile in status");
        }

        @Test
        @DisplayName("should remove secondary client and their accounts")
        void shouldRemoveSecondaryClientAndAccounts() {
            Profile profile = createActiveProfile();
            profile.addSecondaryClient(SECONDARY_CLIENT_ID, AccountEnrollmentType.MANUAL, List.of(ACCOUNT_ID_1));

            profile.removeSecondaryClient(SECONDARY_CLIENT_ID);

            assertThat(profile.clientEnrollments()).hasSize(1);
            assertThat(profile.clientEnrollments().get(0).clientId()).isEqualTo(PRIMARY_CLIENT_ID);
            assertThat(profile.accountEnrollments()).isEmpty();
        }

        @Test
        @DisplayName("should reject removing primary client")
        void shouldRejectRemovingPrimaryClient() {
            Profile profile = createActiveProfile();

            assertThatIllegalArgumentException()
                .isThrownBy(() -> profile.removeSecondaryClient(PRIMARY_CLIENT_ID))
                .withMessageContaining("Cannot remove primary client");
        }

        @Test
        @DisplayName("should reject removing non-enrolled client")
        void shouldRejectRemovingNonEnrolledClient() {
            Profile profile = createActiveProfile();

            assertThatIllegalArgumentException()
                .isThrownBy(() -> profile.removeSecondaryClient(SECONDARY_CLIENT_ID))
                .withMessageContaining("Client not enrolled in profile");
        }

        @Test
        @DisplayName("should reject removing secondary client when profile suspended")
        void shouldRejectRemovingSecondaryClientWhenSuspended() {
            Profile profile = createActiveProfile();
            profile.addSecondaryClient(SECONDARY_CLIENT_ID, AccountEnrollmentType.MANUAL, List.of());
            profile.suspend("Test");

            assertThatIllegalStateException()
                .isThrownBy(() -> profile.removeSecondaryClient(SECONDARY_CLIENT_ID))
                .withMessageContaining("Cannot remove client from profile in status");
        }
    }

    // ==================== Profile Reconstitution ====================

    @Nested
    @DisplayName("Profile Reconstitution")
    class ProfileReconstitutionTests {

        @Test
        @DisplayName("should reconstitute profile from persistence")
        void shouldReconstituteProfileFromPersistence() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:123456789");
            java.time.Instant createdAt = java.time.Instant.now().minusSeconds(3600);
            java.time.Instant updatedAt = java.time.Instant.now();

            Profile profile = Profile.reconstitute(
                profileId,
                ProfileType.SERVICING,
                "Reconstituted Profile",
                "originalUser",
                ProfileStatus.ACTIVE,
                List.of(),
                List.of(),
                List.of(),
                createdAt,
                updatedAt
            );

            assertThat(profile.profileId()).isEqualTo(profileId);
            assertThat(profile.profileType()).isEqualTo(ProfileType.SERVICING);
            assertThat(profile.name()).isEqualTo("Reconstituted Profile");
            assertThat(profile.createdBy()).isEqualTo("originalUser");
            assertThat(profile.status()).isEqualTo(ProfileStatus.ACTIVE);
            assertThat(profile.createdAt()).isEqualTo(createdAt);
            assertThat(profile.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("should reconstitute with enrollments")
        void shouldReconstituteWithEnrollments() {
            ProfileId profileId = ProfileId.fromUrn("servicing:srf:123456789");

            // Create existing enrollments using the private constructor via reflection
            ClientEnrollment clientEnrollment = new ClientEnrollment(PRIMARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL);
            ServiceEnrollment serviceEnrollment = new ServiceEnrollment("PAYMENT", "{}");
            AccountEnrollment accountEnrollment = new AccountEnrollment(null, PRIMARY_CLIENT_ID, ACCOUNT_ID_1);

            Profile profile = Profile.reconstitute(
                profileId,
                ProfileType.SERVICING,
                "Test",
                "user",
                ProfileStatus.ACTIVE,
                List.of(clientEnrollment),
                List.of(serviceEnrollment),
                List.of(accountEnrollment),
                java.time.Instant.now(),
                java.time.Instant.now()
            );

            assertThat(profile.clientEnrollments()).hasSize(1);
            assertThat(profile.serviceEnrollments()).hasSize(1);
            assertThat(profile.accountEnrollments()).hasSize(1);
        }
    }

    // ==================== Legacy Factory Methods ====================

    @Nested
    @DisplayName("Legacy Factory Methods")
    class LegacyFactoryMethodsTests {

        @Test
        @DisplayName("should create profile using deprecated create method")
        @SuppressWarnings("deprecation")
        void shouldCreateProfileUsingDeprecatedCreateMethod() {
            Profile profile = Profile.create(PRIMARY_CLIENT_ID, ProfileType.SERVICING, "user");

            assertThat(profile.profileId()).isNotNull();
            assertThat(profile.profileType()).isEqualTo(ProfileType.SERVICING);
            assertThat(profile.name()).contains("srf:123456789");
            assertThat(profile.clientEnrollments()).hasSize(1);
            assertThat(profile.clientEnrollments().get(0).isPrimary()).isTrue();
        }

        @Test
        @DisplayName("should create servicing profile using deprecated method")
        @SuppressWarnings("deprecation")
        void shouldCreateServicingProfileDeprecated() {
            Profile profile = Profile.createServicing(PRIMARY_CLIENT_ID, "user");

            assertThat(profile.profileType()).isEqualTo(ProfileType.SERVICING);
        }

        @Test
        @DisplayName("should create online profile using deprecated method")
        @SuppressWarnings("deprecation")
        void shouldCreateOnlineProfileDeprecated() {
            Profile profile = Profile.createOnline(PRIMARY_CLIENT_ID, "user");

            assertThat(profile.profileType()).isEqualTo(ProfileType.ONLINE);
        }
    }

    // ==================== Activate on Account Enrollment ====================

    @Nested
    @DisplayName("Profile Activation on Account Enrollment")
    class ProfileActivationOnAccountEnrollmentTests {

        @Test
        @DisplayName("should activate profile on first account enrollment")
        void shouldActivateProfileOnFirstAccountEnrollment() {
            Profile profile = createBasicProfile();
            assertThat(profile.status()).isEqualTo(ProfileStatus.PENDING);

            profile.enrollAccount(PRIMARY_CLIENT_ID, ACCOUNT_ID_1);

            assertThat(profile.status()).isEqualTo(ProfileStatus.ACTIVE);
        }
    }

    // ==================== Helper Methods ====================

    private Profile createBasicProfile() {
        List<ClientEnrollmentRequest> requests = List.of(
            new ClientEnrollmentRequest(
                PRIMARY_CLIENT_ID,
                true,
                AccountEnrollmentType.MANUAL,
                List.of()
            )
        );

        return Profile.createWithAccounts(
            ProfileType.SERVICING,
            "Test Profile",
            requests,
            "testUser"
        );
    }

    private Profile createActiveProfile() {
        Profile profile = createBasicProfile();
        profile.enrollService("PAYMENT", "{}"); // This activates the profile
        return profile;
    }
}
