package com.knight.application.persistence.profiles.repository;

import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.aggregate.Profile.*;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileStatus;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.platform.sharedkernel.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ProfileRepositoryAdapter.
 * Tests persistence operations with H2 database.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MSSQLServer",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true",
    "spring.flyway.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
    "spring.kafka.listener.auto-startup=false",
    "spring.kafka.consumer.auto-startup=false"
})
class ProfileRepositoryAdapterTest {

    @Autowired
    private ServicingProfileRepository repository;

    @Autowired
    private ProfileJpaRepository jpaRepository;

    private static final ClientId PRIMARY_CLIENT_ID = new SrfClientId("123456789");
    private static final ClientId SECONDARY_CLIENT_ID = new SrfClientId("987654321");
    private static final ClientAccountId ACCOUNT_ID_1 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000001");
    private static final ClientAccountId ACCOUNT_ID_2 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000002");

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
    }

    // ==================== Save and Retrieve ====================

    @Nested
    @DisplayName("Save and Retrieve")
    class SaveAndRetrieveTests {

        @Test
        @DisplayName("should save and retrieve profile")
        void shouldSaveAndRetrieveProfile() {
            Profile profile = createProfile("Test Profile", PRIMARY_CLIENT_ID);

            repository.save(profile);
            Optional<Profile> retrieved = repository.findById(profile.profileId());

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().profileId()).isEqualTo(profile.profileId());
            assertThat(retrieved.get().name()).isEqualTo("Test Profile");
            assertThat(retrieved.get().profileType()).isEqualTo(ProfileType.SERVICING);
            assertThat(retrieved.get().status()).isEqualTo(ProfileStatus.PENDING);
            assertThat(retrieved.get().createdBy()).isEqualTo("testUser");
        }

        @Test
        @DisplayName("should persist client enrollments")
        void shouldPersistClientEnrollments() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(PRIMARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL, List.of()),
                new ClientEnrollmentRequest(SECONDARY_CLIENT_ID, false, AccountEnrollmentType.AUTOMATIC, List.of())
            );

            Profile profile = Profile.createWithAccounts(
                ProfileType.SERVICING,
                "Multi-Client Profile",
                requests,
                "testUser"
            );

            repository.save(profile);
            Optional<Profile> retrieved = repository.findById(profile.profileId());

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().clientEnrollments()).hasSize(2);

            ClientEnrollment primary = retrieved.get().clientEnrollments().stream()
                .filter(ClientEnrollment::isPrimary)
                .findFirst()
                .orElseThrow();

            assertThat(primary.clientId()).isEqualTo(PRIMARY_CLIENT_ID);
            assertThat(primary.accountEnrollmentType()).isEqualTo(AccountEnrollmentType.MANUAL);
        }

        @Test
        @DisplayName("should persist account enrollments")
        void shouldPersistAccountEnrollments() {
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
                "Profile With Accounts",
                requests,
                "testUser"
            );

            repository.save(profile);
            Optional<Profile> retrieved = repository.findById(profile.profileId());

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().accountEnrollments()).hasSize(2);

            assertThat(retrieved.get().accountEnrollments().stream()
                .map(AccountEnrollment::accountId)
                .toList())
                .containsExactlyInAnyOrder(ACCOUNT_ID_1, ACCOUNT_ID_2);
        }

        @Test
        @DisplayName("should persist service enrollments")
        void shouldPersistServiceEnrollments() {
            Profile profile = createProfile("Profile With Services", PRIMARY_CLIENT_ID);
            profile.enrollService("PAYMENT", "{\"config\": \"value\"}");
            profile.enrollService("REPORTING", "{\"format\": \"CSV\"}");

            repository.save(profile);
            Optional<Profile> retrieved = repository.findById(profile.profileId());

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().serviceEnrollments()).hasSize(2);
            assertThat(retrieved.get().serviceEnrollments().stream()
                .map(ServiceEnrollment::serviceType)
                .toList())
                .containsExactlyInAnyOrder("PAYMENT", "REPORTING");
        }

        @Test
        @DisplayName("should return empty for non-existing profile")
        void shouldReturnEmptyForNonExistingProfile() {
            Optional<Profile> result = repository.findById(ProfileId.fromUrn("servicing:srf:999999999"));

            assertThat(result).isEmpty();
        }
    }

    // ==================== Query Methods ====================

    @Nested
    @DisplayName("Query Methods")
    class QueryMethodsTests {

        @Test
        @DisplayName("should find by primary client")
        void shouldFindByPrimaryClient() {
            Profile profile1 = createProfile("Profile 1", PRIMARY_CLIENT_ID);
            Profile profile2 = createProfile("Profile 2", SECONDARY_CLIENT_ID);

            repository.save(profile1);
            repository.save(profile2);

            List<Profile> results = repository.findByPrimaryClient(PRIMARY_CLIENT_ID);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).name()).isEqualTo("Profile 1");
        }

        @Test
        @DisplayName("should find by secondary client")
        void shouldFindBySecondaryClient() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(PRIMARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL, List.of()),
                new ClientEnrollmentRequest(SECONDARY_CLIENT_ID, false, AccountEnrollmentType.MANUAL, List.of())
            );

            Profile profile = Profile.createWithAccounts(
                ProfileType.SERVICING,
                "Multi-Client Profile",
                requests,
                "testUser"
            );

            repository.save(profile);

            List<Profile> results = repository.findBySecondaryClient(SECONDARY_CLIENT_ID);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).name()).isEqualTo("Multi-Client Profile");
        }

        @Test
        @DisplayName("should find by client (any enrollment)")
        void shouldFindByClient() {
            // Profile 1: PRIMARY_CLIENT_ID is primary
            Profile profile1 = createProfile("Profile 1", PRIMARY_CLIENT_ID);

            // Profile 2: SECONDARY_CLIENT_ID is primary, PRIMARY_CLIENT_ID is secondary
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(SECONDARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL, List.of()),
                new ClientEnrollmentRequest(PRIMARY_CLIENT_ID, false, AccountEnrollmentType.MANUAL, List.of())
            );
            Profile profile2 = Profile.createWithAccounts(
                ProfileType.ONLINE,
                "Profile 2",
                requests,
                "testUser"
            );

            repository.save(profile1);
            repository.save(profile2);

            List<Profile> results = repository.findByClient(PRIMARY_CLIENT_ID);

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("should check exists servicing profile with primary client")
        void shouldCheckExistsServicingProfileWithPrimaryClient() {
            Profile profile = createProfile("Servicing Profile", PRIMARY_CLIENT_ID);
            repository.save(profile);

            boolean exists = repository.existsServicingProfileWithPrimaryClient(PRIMARY_CLIENT_ID);
            boolean notExists = repository.existsServicingProfileWithPrimaryClient(SECONDARY_CLIENT_ID);

            assertThat(exists).isTrue();
            assertThat(notExists).isFalse();
        }

        @Test
        @DisplayName("should not find online profile in servicing uniqueness check")
        void shouldNotFindOnlineProfileInServicingUniquenessCheck() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(PRIMARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL, List.of())
            );

            Profile onlineProfile = Profile.createWithAccounts(
                ProfileType.ONLINE,  // Not SERVICING
                "Online Profile",
                requests,
                "testUser"
            );
            repository.save(onlineProfile);

            boolean exists = repository.existsServicingProfileWithPrimaryClient(PRIMARY_CLIENT_ID);

            assertThat(exists).isFalse();
        }
    }

    // ==================== Paginated Search Methods ====================

    @Nested
    @DisplayName("Paginated Search Methods")
    class PaginatedSearchTests {

        @Test
        @DisplayName("should search by primary client with no type filter")
        void shouldSearchByPrimaryClientWithNoTypeFilter() {
            Profile profile = createProfile("Profile 1", PRIMARY_CLIENT_ID);
            repository.save(profile);

            var result = repository.searchByPrimaryClient(PRIMARY_CLIENT_ID, null, 0, 10);

            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("should search by primary client with empty type filter")
        void shouldSearchByPrimaryClientWithEmptyTypeFilter() {
            Profile profile = createProfile("Profile 1", PRIMARY_CLIENT_ID);
            repository.save(profile);

            var result = repository.searchByPrimaryClient(PRIMARY_CLIENT_ID, java.util.Set.of(), 0, 10);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("should search by primary client with type filter")
        void shouldSearchByPrimaryClientWithTypeFilter() {
            Profile servicing = createProfile("Servicing", PRIMARY_CLIENT_ID);
            repository.save(servicing);

            var result = repository.searchByPrimaryClient(PRIMARY_CLIENT_ID, java.util.Set.of("SERVICING"), 0, 10);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("should search by client with no type filter")
        void shouldSearchByClientWithNoTypeFilter() {
            Profile profile = createProfile("Profile", PRIMARY_CLIENT_ID);
            repository.save(profile);

            var result = repository.searchByClient(PRIMARY_CLIENT_ID, null, 0, 10);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("should search by client with type filter")
        void shouldSearchByClientWithTypeFilter() {
            Profile profile = createProfile("Profile", PRIMARY_CLIENT_ID);
            repository.save(profile);

            var result = repository.searchByClient(PRIMARY_CLIENT_ID, java.util.Set.of("SERVICING"), 0, 10);

            assertThat(result.content()).hasSize(1);
        }

        @Test
        @DisplayName("should search by primary client name with no type filter")
        void shouldSearchByPrimaryClientNameWithNoTypeFilter() {
            Profile profile = createProfile("Profile", PRIMARY_CLIENT_ID);
            repository.save(profile);

            // Uses wildcard search
            var result = repository.searchByPrimaryClientName("srf:123", null, 0, 10);

            assertThat(result.content()).isNotNull();
        }

        @Test
        @DisplayName("should search by primary client name with type filter")
        void shouldSearchByPrimaryClientNameWithTypeFilter() {
            Profile profile = createProfile("Profile", PRIMARY_CLIENT_ID);
            repository.save(profile);

            var result = repository.searchByPrimaryClientName("srf:123", java.util.Set.of("SERVICING"), 0, 10);

            assertThat(result.content()).isNotNull();
        }

        @Test
        @DisplayName("should search by client name with no type filter")
        void shouldSearchByClientNameWithNoTypeFilter() {
            Profile profile = createProfile("Profile", PRIMARY_CLIENT_ID);
            repository.save(profile);

            var result = repository.searchByClientName("srf:123", null, 0, 10);

            assertThat(result.content()).isNotNull();
        }

        @Test
        @DisplayName("should search by client name with type filter")
        void shouldSearchByClientNameWithTypeFilter() {
            Profile profile = createProfile("Profile", PRIMARY_CLIENT_ID);
            repository.save(profile);

            var result = repository.searchByClientName("srf:123", java.util.Set.of("SERVICING"), 0, 10);

            assertThat(result.content()).isNotNull();
        }

        @Test
        @DisplayName("should paginate results correctly")
        void shouldPaginateResultsCorrectly() {
            // Create 1 profile and verify pagination structure
            Profile profile = createProfile("Profile 1", PRIMARY_CLIENT_ID);
            repository.save(profile);

            var page = repository.searchByPrimaryClient(PRIMARY_CLIENT_ID, null, 0, 2);

            assertThat(page.content()).hasSize(1);
            assertThat(page.page()).isEqualTo(0);
            assertThat(page.size()).isEqualTo(2);
            assertThat(page.totalElements()).isEqualTo(1);
        }
    }

    // ==================== Update Existing Profile ====================

    @Nested
    @DisplayName("Update Existing Profile")
    class UpdateTests {

        @Test
        @DisplayName("should update existing profile instead of creating new")
        void shouldUpdateExistingProfile() {
            // Save initial profile
            Profile original = createProfile("Original Name", PRIMARY_CLIENT_ID);
            repository.save(original);

            // Retrieve, modify, and save again
            Profile retrieved = repository.findById(original.profileId()).orElseThrow();
            retrieved.enrollService("NEW_SERVICE", "{}");
            repository.save(retrieved);

            // Verify only one profile exists
            List<Profile> all = repository.findByPrimaryClient(PRIMARY_CLIENT_ID);
            assertThat(all).hasSize(1);
            assertThat(all.get(0).serviceEnrollments()).hasSize(1);
        }
    }

    // ==================== Helper Methods ====================

    private Profile createProfile(String name, ClientId primaryClientId) {
        List<ClientEnrollmentRequest> requests = List.of(
            new ClientEnrollmentRequest(primaryClientId, true, AccountEnrollmentType.MANUAL, List.of())
        );

        return Profile.createWithAccounts(
            ProfileType.SERVICING,
            name,
            requests,
            "testUser"
        );
    }
}
