package com.knight.application.persistence.users.mapper;

import com.knight.application.persistence.users.entity.UserEntity;
import com.knight.application.persistence.users.entity.UserRoleEntity;
import com.knight.domain.users.aggregate.User;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UserMapper.
 * Tests domain to entity and entity to domain mapping, including role mappings.
 */
class UserMapperTest {

    private UserMapper mapper;

    private static final String EMAIL = "test.user@example.com";
    private static final String FIRST_NAME = "Test";
    private static final String LAST_NAME = "User";
    private static final ProfileId PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final String CREATED_BY = "admin@example.com";

    @BeforeEach
    void setUp() {
        mapper = new UserMapper();
    }

    // ==================== Domain to Entity ====================

    @Nested
    @DisplayName("Domain to Entity Mapping")
    class DomainToEntityTests {

        @Test
        @DisplayName("should map all user fields from domain to entity")
        void shouldMapAllUserFieldsFromDomainToEntity() {
            User user = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.CLIENT_USER,
                User.IdentityProvider.ANP,
                PROFILE_ID,
                Set.of(User.Role.READER, User.Role.CREATOR),
                CREATED_BY
            );

            UserEntity entity = mapper.toEntity(user);

            assertThat(entity).isNotNull();
            assertThat(entity.getUserId()).isNotNull();
            assertThat(entity.getEmail()).isEqualTo(EMAIL);
            assertThat(entity.getFirstName()).isEqualTo(FIRST_NAME);
            assertThat(entity.getLastName()).isEqualTo(LAST_NAME);
            assertThat(entity.getUserType()).isEqualTo("CLIENT_USER");
            assertThat(entity.getIdentityProvider()).isEqualTo("ANP");
            assertThat(entity.getProfileId()).isEqualTo(PROFILE_ID.urn());
            assertThat(entity.getStatus()).isEqualTo("PENDING_CREATION");
            assertThat(entity.isPasswordSet()).isFalse();
            assertThat(entity.isMfaEnrolled()).isFalse();
            assertThat(entity.getCreatedBy()).isEqualTo(CREATED_BY);
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map all roles from domain to entity")
        void shouldMapAllRolesFromDomainToEntity() {
            Set<User.Role> roles = Set.of(
                User.Role.SECURITY_ADMIN,
                User.Role.SERVICE_ADMIN,
                User.Role.APPROVER
            );

            User user = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.CLIENT_USER,
                User.IdentityProvider.ANP,
                PROFILE_ID,
                roles,
                CREATED_BY
            );

            UserEntity entity = mapper.toEntity(user);

            assertThat(entity.getRoles()).hasSize(3);
            assertThat(entity.getRoles())
                .extracting(UserRoleEntity::getRole)
                .containsExactlyInAnyOrder("SECURITY_ADMIN", "SERVICE_ADMIN", "APPROVER");
        }

        @Test
        @DisplayName("should map single role from domain to entity")
        void shouldMapSingleRoleFromDomainToEntity() {
            User user = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                PROFILE_ID,
                Set.of(User.Role.READER),
                CREATED_BY
            );

            UserEntity entity = mapper.toEntity(user);

            assertThat(entity.getRoles()).hasSize(1);
            assertThat(entity.getRoles().get(0).getRole()).isEqualTo("READER");
            assertThat(entity.getRoles().get(0).getUserId()).isEqualTo(entity.getUserId());
        }

        @Test
        @DisplayName("should map indirect user type")
        void shouldMapIndirectUserType() {
            User user = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                PROFILE_ID,
                Set.of(User.Role.CREATOR),
                CREATED_BY
            );

            UserEntity entity = mapper.toEntity(user);

            assertThat(entity.getUserType()).isEqualTo("INDIRECT_USER");
            assertThat(entity.getIdentityProvider()).isEqualTo("AUTH0");
        }

        @Test
        @DisplayName("should map provisioned user state")
        void shouldMapProvisionedUserState() {
            User user = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                PROFILE_ID,
                Set.of(User.Role.READER),
                CREATED_BY
            );
            user.markProvisioned("auth0|12345");

            UserEntity entity = mapper.toEntity(user);

            assertThat(entity.getIdentityProviderUserId()).isEqualTo("auth0|12345");
            assertThat(entity.getStatus()).isEqualTo("PENDING_VERIFICATION");
        }

        @Test
        @DisplayName("should map active user with MFA")
        void shouldMapActiveUserWithMfa() {
            User user = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                PROFILE_ID,
                Set.of(User.Role.READER),
                CREATED_BY
            );
            user.markProvisioned("auth0|12345");
            user.updateOnboardingStatus(true, true);

            UserEntity entity = mapper.toEntity(user);

            assertThat(entity.isPasswordSet()).isTrue();
            assertThat(entity.isMfaEnrolled()).isTrue();
            assertThat(entity.getStatus()).isEqualTo("ACTIVE");
            assertThat(entity.getLastSyncedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map locked user")
        void shouldMapLockedUser() {
            User user = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.CLIENT_USER,
                User.IdentityProvider.ANP,
                PROFILE_ID,
                Set.of(User.Role.READER),
                CREATED_BY
            );
            user.markProvisioned("anp|12345");
            user.updateOnboardingStatus(true, true);
            user.lock("Multiple failed login attempts");

            UserEntity entity = mapper.toEntity(user);

            assertThat(entity.getStatus()).isEqualTo("LOCKED");
            assertThat(entity.getLockReason()).isEqualTo("Multiple failed login attempts");
        }

        @Test
        @DisplayName("should map deactivated user")
        void shouldMapDeactivatedUser() {
            User user = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.CLIENT_USER,
                User.IdentityProvider.ANP,
                PROFILE_ID,
                Set.of(User.Role.READER),
                CREATED_BY
            );
            user.markProvisioned("anp|12345");
            user.updateOnboardingStatus(true, true);
            user.deactivate("User left organization");

            UserEntity entity = mapper.toEntity(user);

            assertThat(entity.getStatus()).isEqualTo("DEACTIVATED");
            assertThat(entity.getDeactivationReason()).isEqualTo("User left organization");
        }
    }

    // ==================== Entity to Domain ====================

    @Nested
    @DisplayName("Entity to Domain Mapping")
    class EntityToDomainTests {

        @Test
        @DisplayName("should map all user fields from entity to domain")
        void shouldMapAllUserFieldsFromEntityToDomain() {
            UserEntity entity = createUserEntity();

            User user = mapper.toDomain(entity);

            assertThat(user).isNotNull();
            assertThat(user.id().id()).isEqualTo(entity.getUserId().toString());
            assertThat(user.email()).isEqualTo(EMAIL);
            assertThat(user.firstName()).isEqualTo(FIRST_NAME);
            assertThat(user.lastName()).isEqualTo(LAST_NAME);
            assertThat(user.userType()).isEqualTo(User.UserType.CLIENT_USER);
            assertThat(user.identityProvider()).isEqualTo(User.IdentityProvider.ANP);
            assertThat(user.profileId().urn()).isEqualTo(PROFILE_ID.urn());
            assertThat(user.status()).isEqualTo(User.Status.PENDING_CREATION);
            assertThat(user.passwordSet()).isFalse();
            assertThat(user.mfaEnrolled()).isFalse();
            assertThat(user.createdBy()).isEqualTo(CREATED_BY);
        }

        @Test
        @DisplayName("should map all roles from entity to domain")
        void shouldMapAllRolesFromEntityToDomain() {
            UserEntity entity = createUserEntity();
            entity.getRoles().add(new UserRoleEntity(
                entity.getUserId(),
                "SECURITY_ADMIN",
                Instant.now(),
                CREATED_BY
            ));
            entity.getRoles().add(new UserRoleEntity(
                entity.getUserId(),
                "APPROVER",
                Instant.now(),
                CREATED_BY
            ));

            User user = mapper.toDomain(entity);

            assertThat(user.roles()).hasSize(3);  // READER + SECURITY_ADMIN + APPROVER
            assertThat(user.roles()).containsExactlyInAnyOrder(
                User.Role.READER,
                User.Role.SECURITY_ADMIN,
                User.Role.APPROVER
            );
        }

        @Test
        @DisplayName("should map single role from entity to domain")
        void shouldMapSingleRoleFromEntityToDomain() {
            UserEntity entity = createUserEntity();
            entity.getRoles().clear();
            entity.getRoles().add(new UserRoleEntity(
                entity.getUserId(),
                "CREATOR",
                Instant.now(),
                CREATED_BY
            ));

            User user = mapper.toDomain(entity);

            assertThat(user.roles()).hasSize(1);
            assertThat(user.roles()).containsExactly(User.Role.CREATOR);
        }

        @Test
        @DisplayName("should map all user statuses from entity to domain")
        void shouldMapAllUserStatusesFromEntityToDomain() {
            UserEntity entity = createUserEntity();

            entity.setStatus("PENDING_CREATION");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(User.Status.PENDING_CREATION);

            entity.setStatus("PENDING_VERIFICATION");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(User.Status.PENDING_VERIFICATION);

            entity.setStatus("PENDING_MFA");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(User.Status.PENDING_MFA);

            entity.setStatus("ACTIVE");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(User.Status.ACTIVE);

            entity.setStatus("LOCKED");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(User.Status.LOCKED);

            entity.setStatus("DEACTIVATED");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(User.Status.DEACTIVATED);
        }

        @Test
        @DisplayName("should map provisioned user state from entity")
        void shouldMapProvisionedUserStateFromEntity() {
            UserEntity entity = createUserEntity();
            entity.setIdentityProviderUserId("auth0|12345");
            entity.setStatus("PENDING_VERIFICATION");

            User user = mapper.toDomain(entity);

            assertThat(user.identityProviderUserId()).isEqualTo("auth0|12345");
            assertThat(user.status()).isEqualTo(User.Status.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("should map active user with MFA from entity")
        void shouldMapActiveUserWithMfaFromEntity() {
            UserEntity entity = createUserEntity();
            entity.setPasswordSet(true);
            entity.setMfaEnrolled(true);
            entity.setStatus("ACTIVE");
            entity.setLastSyncedAt(Instant.now());

            User user = mapper.toDomain(entity);

            assertThat(user.passwordSet()).isTrue();
            assertThat(user.mfaEnrolled()).isTrue();
            assertThat(user.status()).isEqualTo(User.Status.ACTIVE);
            assertThat(user.lastSyncedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map locked user from entity")
        void shouldMapLockedUserFromEntity() {
            UserEntity entity = createUserEntity();
            entity.setStatus("LOCKED");
            entity.setLockReason("Security violation");

            User user = mapper.toDomain(entity);

            assertThat(user.status()).isEqualTo(User.Status.LOCKED);
            assertThat(user.lockReason()).isEqualTo("Security violation");
        }

        @Test
        @DisplayName("should map deactivated user from entity")
        void shouldMapDeactivatedUserFromEntity() {
            UserEntity entity = createUserEntity();
            entity.setStatus("DEACTIVATED");
            entity.setDeactivationReason("Account closed");

            User user = mapper.toDomain(entity);

            assertThat(user.status()).isEqualTo(User.Status.DEACTIVATED);
            assertThat(user.deactivationReason()).isEqualTo("Account closed");
        }
    }

    // ==================== Round-trip ====================

    @Nested
    @DisplayName("Round-trip Mapping")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve all user data in round-trip conversion")
        void shouldPreserveAllUserDataInRoundTrip() {
            User original = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.CLIENT_USER,
                User.IdentityProvider.ANP,
                PROFILE_ID,
                Set.of(User.Role.READER, User.Role.CREATOR, User.Role.APPROVER),
                CREATED_BY
            );

            // Round-trip: Domain -> Entity -> Domain
            UserEntity entity = mapper.toEntity(original);
            User reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.email()).isEqualTo(original.email());
            assertThat(reconstructed.firstName()).isEqualTo(original.firstName());
            assertThat(reconstructed.lastName()).isEqualTo(original.lastName());
            assertThat(reconstructed.userType()).isEqualTo(original.userType());
            assertThat(reconstructed.identityProvider()).isEqualTo(original.identityProvider());
            assertThat(reconstructed.profileId().urn()).isEqualTo(original.profileId().urn());
            assertThat(reconstructed.roles()).containsExactlyInAnyOrderElementsOf(original.roles());
            assertThat(reconstructed.status()).isEqualTo(original.status());
        }

        @Test
        @DisplayName("should preserve all roles in round-trip conversion")
        void shouldPreserveAllRolesInRoundTrip() {
            Set<User.Role> allRoles = Set.of(
                User.Role.SECURITY_ADMIN,
                User.Role.SERVICE_ADMIN,
                User.Role.READER,
                User.Role.CREATOR,
                User.Role.APPROVER
            );

            User original = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.CLIENT_USER,
                User.IdentityProvider.ANP,
                PROFILE_ID,
                allRoles,
                CREATED_BY
            );

            UserEntity entity = mapper.toEntity(original);
            User reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.roles()).hasSize(5);
            assertThat(reconstructed.roles()).containsExactlyInAnyOrderElementsOf(allRoles);
        }

        @Test
        @DisplayName("should preserve provisioned state in round-trip")
        void shouldPreserveProvisionedStateInRoundTrip() {
            User original = User.create(
                EMAIL,
                FIRST_NAME,
                LAST_NAME,
                User.UserType.INDIRECT_USER,
                User.IdentityProvider.AUTH0,
                PROFILE_ID,
                Set.of(User.Role.READER),
                CREATED_BY
            );
            original.markProvisioned("auth0|67890");
            original.updateOnboardingStatus(true, false);

            UserEntity entity = mapper.toEntity(original);
            User reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.identityProviderUserId()).isEqualTo("auth0|67890");
            assertThat(reconstructed.passwordSet()).isTrue();
            assertThat(reconstructed.mfaEnrolled()).isFalse();
            assertThat(reconstructed.status()).isEqualTo(User.Status.PENDING_MFA);
        }
    }

    // ==================== Helper Methods ====================

    private UserEntity createUserEntity() {
        UserEntity entity = new UserEntity();
        entity.setUserId(UUID.randomUUID());
        entity.setEmail(EMAIL);
        entity.setFirstName(FIRST_NAME);
        entity.setLastName(LAST_NAME);
        entity.setUserType("CLIENT_USER");
        entity.setIdentityProvider("ANP");
        entity.setProfileId(PROFILE_ID.urn());
        entity.setStatus("PENDING_CREATION");
        entity.setPasswordSet(false);
        entity.setMfaEnrolled(false);
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(CREATED_BY);
        entity.setUpdatedAt(Instant.now());

        // Add default role
        entity.getRoles().add(new UserRoleEntity(
            entity.getUserId(),
            "READER",
            Instant.now(),
            CREATED_BY
        ));

        return entity;
    }
}
