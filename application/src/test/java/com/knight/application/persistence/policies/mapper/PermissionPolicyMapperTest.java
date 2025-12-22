package com.knight.application.persistence.policies.mapper;

import com.knight.application.persistence.policies.entity.PermissionPolicyEntity;
import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PermissionPolicyMapper.
 */
@DisplayName("PermissionPolicyMapper Tests")
class PermissionPolicyMapperTest {

    private PermissionPolicyMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PermissionPolicyMapper();
    }

    @Nested
    @DisplayName("toEntity()")
    class ToEntity {

        @Test
        @DisplayName("should map all fields from domain to entity")
        void shouldMapAllFieldsFromDomainToEntity() {
            // Given
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));
            Subject subject = Subject.role("ADMIN");
            Action action = Action.of("payments.*");
            Resource resource = Resource.of("*");

            PermissionPolicy policy = PermissionPolicy.create(
                profileId,
                subject,
                action,
                resource,
                PermissionPolicy.Effect.ALLOW,
                "Admin access to payments",
                "test-user"
            );

            // When
            PermissionPolicyEntity entity = mapper.toEntity(policy);

            // Then
            assertThat(entity.getId()).isNotNull();
            assertThat(entity.getProfileId()).isEqualTo(profileId.urn());
            assertThat(entity.getSubjectType()).isEqualTo("ROLE");
            assertThat(entity.getSubjectIdentifier()).isEqualTo("ADMIN");
            assertThat(entity.getActionPattern()).isEqualTo("payments.*");
            assertThat(entity.getResourcePattern()).isEqualTo("*");
            assertThat(entity.getEffect()).isEqualTo("ALLOW");
            assertThat(entity.getDescription()).isEqualTo("Admin access to payments");
            assertThat(entity.getCreatedBy()).isEqualTo("test-user");
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map USER subject type")
        void shouldMapUserSubjectType() {
            // Given
            ProfileId profileId = ProfileId.of("online", new SrfClientId("123456789"));
            String userId = UUID.randomUUID().toString();
            Subject subject = Subject.user(userId);

            PermissionPolicy policy = PermissionPolicy.create(
                profileId,
                subject,
                Action.of("*"),
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "User full access",
                "system"
            );

            // When
            PermissionPolicyEntity entity = mapper.toEntity(policy);

            // Then
            assertThat(entity.getSubjectType()).isEqualTo("USER");
            assertThat(entity.getSubjectIdentifier()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should map DENY effect")
        void shouldMapDenyEffect() {
            // Given
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("123456789"));

            PermissionPolicy policy = PermissionPolicy.create(
                profileId,
                Subject.role("RESTRICTED"),
                Action.of("transfers.*"),
                Resource.all(),
                PermissionPolicy.Effect.DENY,
                "Restricted users cannot transfer",
                "admin"
            );

            // When
            PermissionPolicyEntity entity = mapper.toEntity(policy);

            // Then
            assertThat(entity.getEffect()).isEqualTo("DENY");
        }
    }

    @Nested
    @DisplayName("toDomain()")
    class ToDomain {

        @Test
        @DisplayName("should map all fields from entity to domain")
        void shouldMapAllFieldsFromEntityToDomain() {
            // Given
            UUID policyId = UUID.randomUUID();
            Instant now = Instant.now();

            PermissionPolicyEntity entity = new PermissionPolicyEntity();
            entity.setId(policyId);
            entity.setProfileId("servicing:srf:123456789");
            entity.setSubjectType("ROLE");
            entity.setSubjectIdentifier("MANAGER");
            entity.setActionPattern("approvals.*");
            entity.setResourcePattern("high-value:*");
            entity.setEffect("ALLOW");
            entity.setDescription("Managers can approve");
            entity.setCreatedAt(now);
            entity.setCreatedBy("admin");
            entity.setUpdatedAt(now);

            // When
            PermissionPolicy policy = mapper.toDomain(entity);

            // Then
            assertThat(policy.id()).isEqualTo(policyId.toString());
            assertThat(policy.profileId().urn()).isEqualTo("servicing:srf:123456789");
            assertThat(policy.subject().type()).isEqualTo(Subject.SubjectType.ROLE);
            assertThat(policy.subject().identifier()).isEqualTo("MANAGER");
            assertThat(policy.action().value()).isEqualTo("approvals.*");
            assertThat(policy.resource().value()).isEqualTo("high-value:*");
            assertThat(policy.effect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
            assertThat(policy.description()).isEqualTo("Managers can approve");
            assertThat(policy.createdBy()).isEqualTo("admin");
            assertThat(policy.createdAt()).isEqualTo(now);
            assertThat(policy.updatedAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("should map USER subject type from entity")
        void shouldMapUserSubjectTypeFromEntity() {
            // Given
            UUID userId = UUID.randomUUID();
            PermissionPolicyEntity entity = createTestEntity();
            entity.setSubjectType("USER");
            entity.setSubjectIdentifier(userId.toString());

            // When
            PermissionPolicy policy = mapper.toDomain(entity);

            // Then
            assertThat(policy.subject().type()).isEqualTo(Subject.SubjectType.USER);
            assertThat(policy.subject().identifier()).isEqualTo(userId.toString());
        }

        @Test
        @DisplayName("should map GROUP subject type from entity")
        void shouldMapGroupSubjectTypeFromEntity() {
            // Given
            UUID groupId = UUID.randomUUID();
            PermissionPolicyEntity entity = createTestEntity();
            entity.setSubjectType("GROUP");
            entity.setSubjectIdentifier(groupId.toString());

            // When
            PermissionPolicy policy = mapper.toDomain(entity);

            // Then
            assertThat(policy.subject().type()).isEqualTo(Subject.SubjectType.GROUP);
            assertThat(policy.subject().identifier()).isEqualTo(groupId.toString());
        }

        @Test
        @DisplayName("should map DENY effect from entity")
        void shouldMapDenyEffectFromEntity() {
            // Given
            PermissionPolicyEntity entity = createTestEntity();
            entity.setEffect("DENY");

            // When
            PermissionPolicy policy = mapper.toDomain(entity);

            // Then
            assertThat(policy.effect()).isEqualTo(PermissionPolicy.Effect.DENY);
        }

        private PermissionPolicyEntity createTestEntity() {
            Instant now = Instant.now();
            PermissionPolicyEntity entity = new PermissionPolicyEntity();
            entity.setId(UUID.randomUUID());
            entity.setProfileId("servicing:srf:123456789");
            entity.setSubjectType("ROLE");
            entity.setSubjectIdentifier("TEST");
            entity.setActionPattern("*");
            entity.setResourcePattern("*");
            entity.setEffect("ALLOW");
            entity.setDescription("Test policy");
            entity.setCreatedAt(now);
            entity.setCreatedBy("test");
            entity.setUpdatedAt(now);
            return entity;
        }
    }

    @Nested
    @DisplayName("Round-trip conversion")
    class RoundTripConversion {

        @Test
        @DisplayName("should preserve all fields through round-trip")
        void shouldPreserveAllFieldsThroughRoundTrip() {
            // Given
            ProfileId profileId = ProfileId.of("servicing", new SrfClientId("987654321"));
            String userId = UUID.randomUUID().toString();

            PermissionPolicy originalPolicy = PermissionPolicy.create(
                profileId,
                Subject.user(userId),
                Action.of("accounts.create"),
                Resource.of("CAN_DDA:*"),
                PermissionPolicy.Effect.ALLOW,
                "Create DDA accounts",
                "test-creator"
            );

            // When - convert to entity and back
            PermissionPolicyEntity entity = mapper.toEntity(originalPolicy);
            PermissionPolicy reconstituted = mapper.toDomain(entity);

            // Then
            assertThat(reconstituted.id()).isEqualTo(originalPolicy.id());
            assertThat(reconstituted.profileId().urn()).isEqualTo(originalPolicy.profileId().urn());
            assertThat(reconstituted.subject().type()).isEqualTo(originalPolicy.subject().type());
            assertThat(reconstituted.subject().identifier()).isEqualTo(originalPolicy.subject().identifier());
            assertThat(reconstituted.action().value()).isEqualTo(originalPolicy.action().value());
            assertThat(reconstituted.resource().value()).isEqualTo(originalPolicy.resource().value());
            assertThat(reconstituted.effect()).isEqualTo(originalPolicy.effect());
            assertThat(reconstituted.description()).isEqualTo(originalPolicy.description());
            assertThat(reconstituted.createdBy()).isEqualTo(originalPolicy.createdBy());
        }
    }
}
