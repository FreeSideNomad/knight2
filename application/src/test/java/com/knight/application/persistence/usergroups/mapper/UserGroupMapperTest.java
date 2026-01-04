package com.knight.application.persistence.usergroups.mapper;

import com.knight.application.persistence.usergroups.entity.UserGroupEntity;
import com.knight.application.persistence.usergroups.entity.UserGroupMemberEntity;
import com.knight.domain.users.aggregate.UserGroup;
import com.knight.domain.users.aggregate.UserGroup.UserGroupMember;
import com.knight.domain.users.types.UserGroupId;
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
 * Unit tests for UserGroupMapper.
 */
class UserGroupMapperTest {

    private UserGroupMapper mapper;

    private static final ProfileId PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final String GROUP_NAME = "Test Group";
    private static final String GROUP_DESCRIPTION = "Test group description";
    private static final String CREATED_BY = "admin@example.com";
    private static final String USER_ID_1 = "user-123";
    private static final String USER_ID_2 = "user-456";

    @BeforeEach
    void setUp() {
        mapper = new UserGroupMapper();
    }

    // ==================== Entity to Domain ====================

    @Nested
    @DisplayName("Entity to Domain Mapping")
    class EntityToDomainTests {

        @Test
        @DisplayName("should map all group fields from entity to domain")
        void shouldMapAllGroupFieldsFromEntityToDomain() {
            UserGroupEntity entity = createUserGroupEntity();

            UserGroup group = mapper.toDomain(entity);

            assertThat(group).isNotNull();
            assertThat(group.id().value()).isEqualTo(entity.getGroupId());
            assertThat(group.profileId().urn()).isEqualTo(PROFILE_ID.urn());
            assertThat(group.name()).isEqualTo(GROUP_NAME);
            assertThat(group.description()).isEqualTo(GROUP_DESCRIPTION);
            assertThat(group.createdAt()).isEqualTo(entity.getCreatedAt());
            assertThat(group.createdBy()).isEqualTo(CREATED_BY);
            assertThat(group.updatedAt()).isEqualTo(entity.getUpdatedAt());
        }

        @Test
        @DisplayName("should map members from entity to domain")
        void shouldMapMembersFromEntityToDomain() {
            UserGroupEntity entity = createUserGroupEntity();
            addMemberToEntity(entity, USER_ID_1, Instant.now().minusSeconds(100), CREATED_BY);
            addMemberToEntity(entity, USER_ID_2, Instant.now(), CREATED_BY);

            UserGroup group = mapper.toDomain(entity);

            assertThat(group.members()).hasSize(2);
            assertThat(group.memberUserIds())
                .extracting(UserId::id)
                .containsExactlyInAnyOrder(USER_ID_1, USER_ID_2);
        }

        @Test
        @DisplayName("should map empty members from entity to domain")
        void shouldMapEmptyMembersFromEntityToDomain() {
            UserGroupEntity entity = createUserGroupEntity();

            UserGroup group = mapper.toDomain(entity);

            assertThat(group.members()).isEmpty();
        }

        @Test
        @DisplayName("should preserve member metadata")
        void shouldPreserveMemberMetadata() {
            UserGroupEntity entity = createUserGroupEntity();
            Instant addedAt = Instant.parse("2024-01-15T10:00:00Z");
            String addedBy = "different-admin@example.com";
            addMemberToEntity(entity, USER_ID_1, addedAt, addedBy);

            UserGroup group = mapper.toDomain(entity);

            UserGroupMember member = group.members().iterator().next();
            assertThat(member.userId().id()).isEqualTo(USER_ID_1);
            assertThat(member.addedAt()).isEqualTo(addedAt);
            assertThat(member.addedBy()).isEqualTo(addedBy);
        }
    }

    // ==================== Domain to Entity ====================

    @Nested
    @DisplayName("Domain to Entity Mapping")
    class DomainToEntityTests {

        @Test
        @DisplayName("should map all group fields from domain to entity")
        void shouldMapAllGroupFieldsFromDomainToEntity() {
            UserGroup group = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);

            UserGroupEntity entity = mapper.toEntity(group);

            assertThat(entity).isNotNull();
            assertThat(entity.getGroupId()).isEqualTo(group.id().value());
            assertThat(entity.getProfileId()).isEqualTo(PROFILE_ID.urn());
            assertThat(entity.getName()).isEqualTo(GROUP_NAME);
            assertThat(entity.getDescription()).isEqualTo(GROUP_DESCRIPTION);
            assertThat(entity.getCreatedBy()).isEqualTo(CREATED_BY);
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map members from domain to entity")
        void shouldMapMembersFromDomainToEntity() {
            UserGroup group = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            group.addMember(UserId.of(USER_ID_1), CREATED_BY);
            group.addMember(UserId.of(USER_ID_2), "other-admin");

            UserGroupEntity entity = mapper.toEntity(group);

            assertThat(entity.getMembers()).hasSize(2);
            assertThat(entity.getMembers())
                .extracting(UserGroupMemberEntity::getUserId)
                .containsExactlyInAnyOrder(USER_ID_1, USER_ID_2);
        }

        @Test
        @DisplayName("should map empty members from domain to entity")
        void shouldMapEmptyMembersFromDomainToEntity() {
            UserGroup group = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);

            UserGroupEntity entity = mapper.toEntity(group);

            assertThat(entity.getMembers()).isEmpty();
        }

        @Test
        @DisplayName("should preserve member metadata in entity")
        void shouldPreserveMemberMetadataInEntity() {
            UserGroup group = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            group.addMember(UserId.of(USER_ID_1), "specific-admin");

            UserGroupEntity entity = mapper.toEntity(group);

            UserGroupMemberEntity memberEntity = entity.getMembers().iterator().next();
            assertThat(memberEntity.getUserId()).isEqualTo(USER_ID_1);
            assertThat(memberEntity.getGroupId()).isEqualTo(group.id().value());
            assertThat(memberEntity.getAddedBy()).isEqualTo("specific-admin");
            assertThat(memberEntity.getAddedAt()).isNotNull();
        }
    }

    // ==================== Round-trip ====================

    @Nested
    @DisplayName("Round-trip Mapping")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve all group data in round-trip conversion")
        void shouldPreserveAllGroupDataInRoundTrip() {
            UserGroup original = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            original.addMember(UserId.of(USER_ID_1), CREATED_BY);
            original.addMember(UserId.of(USER_ID_2), CREATED_BY);

            // Round-trip: Domain -> Entity -> Domain
            UserGroupEntity entity = mapper.toEntity(original);
            UserGroup reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.id().value()).isEqualTo(original.id().value());
            assertThat(reconstructed.profileId().urn()).isEqualTo(original.profileId().urn());
            assertThat(reconstructed.name()).isEqualTo(original.name());
            assertThat(reconstructed.description()).isEqualTo(original.description());
            assertThat(reconstructed.memberCount()).isEqualTo(original.memberCount());
            assertThat(reconstructed.memberUserIds())
                .containsExactlyInAnyOrderElementsOf(original.memberUserIds());
        }

        @Test
        @DisplayName("should preserve empty members in round-trip")
        void shouldPreserveEmptyMembersInRoundTrip() {
            UserGroup original = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);

            UserGroupEntity entity = mapper.toEntity(original);
            UserGroup reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.members()).isEmpty();
        }
    }

    // ==================== Helper Methods ====================

    private UserGroupEntity createUserGroupEntity() {
        UserGroupEntity entity = new UserGroupEntity();
        entity.setGroupId(UUID.randomUUID());
        entity.setProfileId(PROFILE_ID.urn());
        entity.setName(GROUP_NAME);
        entity.setDescription(GROUP_DESCRIPTION);
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(CREATED_BY);
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private void addMemberToEntity(UserGroupEntity entity, String userId, Instant addedAt, String addedBy) {
        UserGroupMemberEntity member = new UserGroupMemberEntity(
            entity.getGroupId(),
            userId,
            addedAt,
            addedBy
        );
        entity.addMember(member);
    }
}
