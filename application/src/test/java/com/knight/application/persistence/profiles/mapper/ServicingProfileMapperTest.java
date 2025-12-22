package com.knight.application.persistence.profiles.mapper;

import com.knight.application.persistence.profiles.entity.*;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.aggregate.Profile.*;
import com.knight.domain.serviceprofiles.types.AccountEnrollmentType;
import com.knight.domain.serviceprofiles.types.ProfileStatus;
import com.knight.domain.serviceprofiles.types.ProfileType;
import com.knight.platform.sharedkernel.*;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ServicingProfileMapper.
 * Tests domain to entity and entity to domain mapping.
 */
class ServicingProfileMapperTest {

    private ServicingProfileMapper mapper;

    private static final ClientId PRIMARY_CLIENT_ID = new SrfClientId("123456789");
    private static final ClientId SECONDARY_CLIENT_ID = new SrfClientId("987654321");
    private static final ClientAccountId ACCOUNT_ID_1 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000001");
    private static final ClientAccountId ACCOUNT_ID_2 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000002");

    @BeforeEach
    void setUp() {
        mapper = new ServicingProfileMapperImpl();
    }

    // ==================== Domain to Entity ====================

    @Nested
    @DisplayName("Domain to Entity Mapping")
    class DomainToEntityTests {

        @Test
        @DisplayName("should map domain to entity")
        void shouldMapDomainToEntity() {
            Profile profile = createProfile("Test Profile");

            ProfileEntity entity = mapper.toEntity(profile);

            assertThat(entity.getProfileId()).isEqualTo(profile.profileId().urn());
            assertThat(entity.getName()).isEqualTo("Test Profile");
            assertThat(entity.getProfileType()).isEqualTo("SERVICING");
            assertThat(entity.getStatus()).isEqualTo("PENDING");
            assertThat(entity.getCreatedBy()).isEqualTo("testUser");
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map client enrollments")
        void shouldMapClientEnrollments() {
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

            ProfileEntity entity = mapper.toEntity(profile);

            assertThat(entity.getClientEnrollments()).hasSize(2);

            ClientEnrollmentEntity primary = entity.getClientEnrollments().stream()
                .filter(ClientEnrollmentEntity::isPrimary)
                .findFirst()
                .orElseThrow();

            assertThat(primary.getClientId()).isEqualTo("srf:123456789");
            assertThat(primary.getAccountEnrollmentType()).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("should map account enrollments with null service enrollment ID")
        void shouldMapAccountEnrollmentsWithNullServiceEnrollmentId() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of(ACCOUNT_ID_1)
                )
            );

            Profile profile = Profile.createWithAccounts(
                ProfileType.SERVICING,
                "Profile With Accounts",
                requests,
                "testUser"
            );

            ProfileEntity entity = mapper.toEntity(profile);

            assertThat(entity.getAccountEnrollments()).hasSize(1);
            AccountEnrollmentEntity accountEntity = entity.getAccountEnrollments().get(0);
            assertThat(accountEntity.getServiceEnrollmentId()).isNull();  // Profile-level enrollment
            assertThat(accountEntity.getClientId()).isEqualTo("srf:123456789");
            assertThat(accountEntity.getAccountId()).isEqualTo("CAN_DDA:DDA:12345:000000000001");
        }

        @Test
        @DisplayName("should map service enrollments")
        void shouldMapServiceEnrollments() {
            Profile profile = createProfile("Profile With Services");
            profile.enrollService("PAYMENT", "{\"config\": \"value\"}");

            ProfileEntity entity = mapper.toEntity(profile);

            assertThat(entity.getServiceEnrollments()).hasSize(1);
            ServiceEnrollmentEntity serviceEntity = entity.getServiceEnrollments().get(0);
            assertThat(serviceEntity.getServiceType()).isEqualTo("PAYMENT");
            assertThat(serviceEntity.getConfiguration()).isEqualTo("{\"config\": \"value\"}");
            assertThat(serviceEntity.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should handle null domain")
        void shouldHandleNullDomain() {
            ProfileEntity entity = mapper.toEntity(null);

            assertThat(entity).isNull();
        }
    }

    // ==================== Entity to Domain ====================

    @Nested
    @DisplayName("Entity to Domain Mapping")
    class EntityToDomainTests {

        @Test
        @DisplayName("should map entity to domain")
        void shouldMapEntityToDomain() {
            ProfileEntity entity = createProfileEntity("Test Profile");

            Profile profile = mapper.toDomain(entity);

            assertThat(profile.profileId().urn()).isEqualTo(entity.getProfileId());
            assertThat(profile.name()).isEqualTo("Test Profile");
            assertThat(profile.profileType()).isEqualTo(ProfileType.SERVICING);
            assertThat(profile.status()).isEqualTo(ProfileStatus.PENDING);
            assertThat(profile.createdBy()).isEqualTo("testUser");
        }

        @Test
        @DisplayName("should reconstruct client enrollments")
        void shouldReconstructClientEnrollments() {
            ProfileEntity entity = createProfileEntity("Test Profile");

            // Add a secondary client enrollment
            ClientEnrollmentEntity secondaryEnrollment = new ClientEnrollmentEntity();
            secondaryEnrollment.setId(UUID.randomUUID());
            secondaryEnrollment.setProfile(entity);
            secondaryEnrollment.setClientId("srf:987654321");
            secondaryEnrollment.setPrimary(false);
            secondaryEnrollment.setAccountEnrollmentType("AUTOMATIC");
            secondaryEnrollment.setEnrolledAt(Instant.now());
            entity.getClientEnrollments().add(secondaryEnrollment);

            Profile profile = mapper.toDomain(entity);

            assertThat(profile.clientEnrollments()).hasSize(2);

            ClientEnrollment secondary = profile.clientEnrollments().stream()
                .filter(ce -> !ce.isPrimary())
                .findFirst()
                .orElseThrow();

            assertThat(secondary.clientId().urn()).isEqualTo("srf:987654321");
            assertThat(secondary.accountEnrollmentType()).isEqualTo(AccountEnrollmentType.AUTOMATIC);
        }

        @Test
        @DisplayName("should reconstruct account enrollments with null service enrollment ID")
        void shouldReconstructAccountEnrollmentsWithNullServiceEnrollmentId() {
            ProfileEntity entity = createProfileEntity("Test Profile");

            AccountEnrollmentEntity accountEntity = new AccountEnrollmentEntity();
            accountEntity.setEnrollmentId(UUID.randomUUID());
            accountEntity.setProfile(entity);
            accountEntity.setServiceEnrollmentId(null);  // Profile-level
            accountEntity.setClientId("srf:123456789");
            accountEntity.setAccountId("CAN_DDA:DDA:12345:000000000001");
            accountEntity.setStatus("ACTIVE");
            accountEntity.setEnrolledAt(Instant.now());
            entity.getAccountEnrollments().add(accountEntity);

            Profile profile = mapper.toDomain(entity);

            assertThat(profile.accountEnrollments()).hasSize(1);
            AccountEnrollment accountEnrollment = profile.accountEnrollments().get(0);
            assertThat(accountEnrollment.isProfileLevel()).isTrue();
            assertThat(accountEnrollment.serviceEnrollmentId()).isNull();
        }

        @Test
        @DisplayName("should reconstruct account enrollments with service enrollment ID")
        void shouldReconstructAccountEnrollmentsWithServiceEnrollmentId() {
            ProfileEntity entity = createProfileEntity("Test Profile");

            UUID serviceEnrollmentId = UUID.randomUUID();

            // Add service enrollment first
            ServiceEnrollmentEntity serviceEntity = new ServiceEnrollmentEntity();
            serviceEntity.setEnrollmentId(serviceEnrollmentId);
            serviceEntity.setProfile(entity);
            serviceEntity.setServiceType("PAYMENT");
            serviceEntity.setConfiguration("{}");
            serviceEntity.setStatus("ACTIVE");
            serviceEntity.setEnrolledAt(Instant.now());
            entity.getServiceEnrollments().add(serviceEntity);

            // Add account enrollment linked to service
            AccountEnrollmentEntity accountEntity = new AccountEnrollmentEntity();
            accountEntity.setEnrollmentId(UUID.randomUUID());
            accountEntity.setProfile(entity);
            accountEntity.setServiceEnrollmentId(serviceEnrollmentId);  // Service-level
            accountEntity.setClientId("srf:123456789");
            accountEntity.setAccountId("CAN_DDA:DDA:12345:000000000001");
            accountEntity.setStatus("ACTIVE");
            accountEntity.setEnrolledAt(Instant.now());
            entity.getAccountEnrollments().add(accountEntity);

            Profile profile = mapper.toDomain(entity);

            assertThat(profile.accountEnrollments()).hasSize(1);
            AccountEnrollment accountEnrollment = profile.accountEnrollments().get(0);
            assertThat(accountEnrollment.isServiceLevel()).isTrue();
            assertThat(accountEnrollment.serviceEnrollmentId()).isNotNull();
        }

        @Test
        @DisplayName("should handle null entity")
        void shouldHandleNullEntity() {
            Profile profile = mapper.toDomain(null);

            assertThat(profile).isNull();
        }
    }

    // ==================== Round-trip ====================

    @Nested
    @DisplayName("Round-trip Mapping")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve all data in round-trip")
        void shouldPreserveAllDataInRoundTrip() {
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of(ACCOUNT_ID_1, ACCOUNT_ID_2)
                )
            );

            Profile original = Profile.createWithAccounts(
                ProfileType.SERVICING,
                "Round-trip Test",
                requests,
                "testUser"
            );
            original.enrollService("PAYMENT", "{\"config\": true}");

            // Round-trip: Domain -> Entity -> Domain
            ProfileEntity entity = mapper.toEntity(original);
            Profile reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.profileId()).isEqualTo(original.profileId());
            assertThat(reconstructed.name()).isEqualTo(original.name());
            assertThat(reconstructed.profileType()).isEqualTo(original.profileType());
            assertThat(reconstructed.status()).isEqualTo(original.status());
            assertThat(reconstructed.clientEnrollments()).hasSize(original.clientEnrollments().size());
            assertThat(reconstructed.accountEnrollments()).hasSize(original.accountEnrollments().size());
            assertThat(reconstructed.serviceEnrollments()).hasSize(original.serviceEnrollments().size());
        }
    }

    // ==================== Update Entity Tests ====================

    @Nested
    @DisplayName("Update Entity Mapping")
    class UpdateEntityTests {

        @Test
        @DisplayName("should update existing entity with domain changes")
        void shouldUpdateExistingEntity() {
            // Create initial domain and entity
            Profile original = createProfile("Original Name");
            ProfileEntity entity = mapper.toEntity(original);

            // Modify the domain profile
            Profile updated = Profile.reconstitute(
                original.profileId(),
                original.profileType(),
                "Updated Name",
                original.createdBy(),
                ProfileStatus.ACTIVE,
                original.clientEnrollments(),
                original.serviceEnrollments(),
                original.accountEnrollments(),
                original.createdAt(),
                Instant.now()
            );

            // Update the entity
            mapper.updateEntity(entity, updated);

            assertThat(entity.getName()).isEqualTo("Updated Name");
            assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should sync client enrollments - add new enrollment")
        void shouldSyncClientEnrollmentsAddNew() {
            // Create initial profile with one client
            Profile original = createProfile("Test Profile");
            ProfileEntity entity = mapper.toEntity(original);
            assertThat(entity.getClientEnrollments()).hasSize(1);

            // Create updated profile with two clients
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(PRIMARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL, List.of()),
                new ClientEnrollmentRequest(SECONDARY_CLIENT_ID, false, AccountEnrollmentType.AUTOMATIC, List.of())
            );
            Profile updated = Profile.createWithAccounts(ProfileType.SERVICING, "Test Profile", requests, "testUser");

            // Update entity
            mapper.updateEntity(entity, updated);

            assertThat(entity.getClientEnrollments()).hasSize(2);
        }

        @Test
        @DisplayName("should sync client enrollments - remove enrollment")
        void shouldSyncClientEnrollmentsRemove() {
            // Create initial profile with two clients
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(PRIMARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL, List.of()),
                new ClientEnrollmentRequest(SECONDARY_CLIENT_ID, false, AccountEnrollmentType.AUTOMATIC, List.of())
            );
            Profile original = Profile.createWithAccounts(ProfileType.SERVICING, "Test Profile", requests, "testUser");
            ProfileEntity entity = mapper.toEntity(original);
            assertThat(entity.getClientEnrollments()).hasSize(2);

            // Create updated profile with one client
            List<ClientEnrollmentRequest> singleRequest = List.of(
                new ClientEnrollmentRequest(PRIMARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL, List.of())
            );
            Profile updated = Profile.createWithAccounts(ProfileType.SERVICING, "Test Profile", singleRequest, "testUser");

            // Update entity
            mapper.updateEntity(entity, updated);

            assertThat(entity.getClientEnrollments()).hasSize(1);
        }

        @Test
        @DisplayName("should sync service enrollments - add new service")
        void shouldSyncServiceEnrollmentsAddNew() {
            // Create initial profile without services
            Profile original = createProfile("Test Profile");
            ProfileEntity entity = mapper.toEntity(original);
            assertThat(entity.getServiceEnrollments()).isEmpty();

            // Add a service to domain
            original.enrollService("PAYMENT", "{\"config\": \"value\"}");

            // Update entity
            mapper.updateEntity(entity, original);

            assertThat(entity.getServiceEnrollments()).hasSize(1);
            assertThat(entity.getServiceEnrollments().get(0).getServiceType()).isEqualTo("PAYMENT");
        }

        @Test
        @DisplayName("should sync service enrollments - remove service")
        void shouldSyncServiceEnrollmentsRemove() {
            // Create profile with service
            Profile original = createProfile("Test Profile");
            original.enrollService("PAYMENT", "{\"config\": \"value\"}");
            ProfileEntity entity = mapper.toEntity(original);
            assertThat(entity.getServiceEnrollments()).hasSize(1);

            // Create new profile without services
            Profile updated = createProfile("Test Profile");

            // Update entity
            mapper.updateEntity(entity, updated);

            assertThat(entity.getServiceEnrollments()).isEmpty();
        }

        @Test
        @DisplayName("should sync account enrollments - add new account")
        void shouldSyncAccountEnrollmentsAddNew() {
            // Create profile without accounts
            Profile original = createProfile("Test Profile");
            ProfileEntity entity = mapper.toEntity(original);
            assertThat(entity.getAccountEnrollments()).isEmpty();

            // Create profile with accounts
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of(ACCOUNT_ID_1)
                )
            );
            Profile updated = Profile.createWithAccounts(ProfileType.SERVICING, "Test Profile", requests, "testUser");

            // Update entity
            mapper.updateEntity(entity, updated);

            assertThat(entity.getAccountEnrollments()).hasSize(1);
        }

        @Test
        @DisplayName("should sync account enrollments - remove account")
        void shouldSyncAccountEnrollmentsRemove() {
            // Create profile with accounts
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of(ACCOUNT_ID_1, ACCOUNT_ID_2)
                )
            );
            Profile original = Profile.createWithAccounts(ProfileType.SERVICING, "Test Profile", requests, "testUser");
            ProfileEntity entity = mapper.toEntity(original);
            assertThat(entity.getAccountEnrollments()).hasSize(2);

            // Create profile with one account
            List<ClientEnrollmentRequest> singleRequest = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of(ACCOUNT_ID_1)
                )
            );
            Profile updated = Profile.createWithAccounts(ProfileType.SERVICING, "Test Profile", singleRequest, "testUser");

            // Update entity
            mapper.updateEntity(entity, updated);

            assertThat(entity.getAccountEnrollments()).hasSize(1);
        }

        @Test
        @DisplayName("should update existing account enrollment fields")
        void shouldUpdateExistingAccountEnrollmentFields() {
            // Create profile with accounts
            List<ClientEnrollmentRequest> requests = List.of(
                new ClientEnrollmentRequest(
                    PRIMARY_CLIENT_ID,
                    true,
                    AccountEnrollmentType.MANUAL,
                    List.of(ACCOUNT_ID_1)
                )
            );
            Profile original = Profile.createWithAccounts(ProfileType.SERVICING, "Test Profile", requests, "testUser");
            ProfileEntity entity = mapper.toEntity(original);

            // Update using same profile (should update fields in place)
            mapper.updateEntity(entity, original);

            assertThat(entity.getAccountEnrollments()).hasSize(1);
        }
    }

    // ==================== Helper Methods ====================

    private Profile createProfile(String name) {
        List<ClientEnrollmentRequest> requests = List.of(
            new ClientEnrollmentRequest(PRIMARY_CLIENT_ID, true, AccountEnrollmentType.MANUAL, List.of())
        );

        return Profile.createWithAccounts(
            ProfileType.SERVICING,
            name,
            requests,
            "testUser"
        );
    }

    private ProfileEntity createProfileEntity(String name) {
        ProfileEntity entity = new ProfileEntity();
        entity.setProfileId("servicing:srf:123456789");
        entity.setName(name);
        entity.setProfileType("SERVICING");
        entity.setStatus("PENDING");
        entity.setCreatedBy("testUser");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        // Add primary client enrollment
        ClientEnrollmentEntity clientEnrollment = new ClientEnrollmentEntity();
        clientEnrollment.setId(UUID.randomUUID());
        clientEnrollment.setProfile(entity);
        clientEnrollment.setClientId("srf:123456789");
        clientEnrollment.setPrimary(true);
        clientEnrollment.setAccountEnrollmentType("MANUAL");
        clientEnrollment.setEnrolledAt(Instant.now());
        entity.getClientEnrollments().add(clientEnrollment);

        return entity;
    }
}
