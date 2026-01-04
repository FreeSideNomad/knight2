package com.knight.application.persistence.usergroups.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UserGroupEntity and UserGroupMemberEntity.
 */
class UserGroupEntityTest {

    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final String PROFILE_ID = "servicing:srf:123456789";
    private static final String GROUP_NAME = "Test Group";
    private static final String DESCRIPTION = "Test description";
    private static final String CREATED_BY = "admin@example.com";
    private static final String USER_ID_1 = "user-123";
    private static final String USER_ID_2 = "user-456";

    // ==================== UserGroupEntity Tests ====================

    @Nested
    @DisplayName("UserGroupEntity Tests")
    class UserGroupEntityTests {

        private UserGroupEntity entity;

        @BeforeEach
        void setUp() {
            entity = new UserGroupEntity();
            entity.setGroupId(GROUP_ID);
            entity.setProfileId(PROFILE_ID);
            entity.setName(GROUP_NAME);
            entity.setDescription(DESCRIPTION);
            entity.setCreatedAt(Instant.now());
            entity.setCreatedBy(CREATED_BY);
            entity.setUpdatedAt(Instant.now());
        }

        @Test
        @DisplayName("should get and set all fields")
        void shouldGetAndSetAllFields() {
            assertThat(entity.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(entity.getProfileId()).isEqualTo(PROFILE_ID);
            assertThat(entity.getName()).isEqualTo(GROUP_NAME);
            assertThat(entity.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(entity.getCreatedBy()).isEqualTo(CREATED_BY);
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getUpdatedAt()).isNotNull();
            assertThat(entity.getMembers()).isEmpty();
        }

        @Test
        @DisplayName("should add member and set bidirectional relationship")
        void shouldAddMemberAndSetBidirectionalRelationship() {
            UserGroupMemberEntity member = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );

            entity.addMember(member);

            assertThat(entity.getMembers()).hasSize(1);
            assertThat(entity.getMembers()).contains(member);
            assertThat(member.getGroup()).isEqualTo(entity);
        }

        @Test
        @DisplayName("should add multiple members")
        void shouldAddMultipleMembers() {
            UserGroupMemberEntity member1 = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );
            UserGroupMemberEntity member2 = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_2, Instant.now(), CREATED_BY
            );

            entity.addMember(member1);
            entity.addMember(member2);

            assertThat(entity.getMembers()).hasSize(2);
            assertThat(entity.getMembers()).contains(member1, member2);
        }

        @Test
        @DisplayName("should remove member and clear bidirectional relationship")
        void shouldRemoveMemberAndClearBidirectionalRelationship() {
            UserGroupMemberEntity member = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );
            entity.addMember(member);

            entity.removeMember(member);

            assertThat(entity.getMembers()).isEmpty();
            assertThat(member.getGroup()).isNull();
        }

        @Test
        @DisplayName("should clear all members")
        void shouldClearAllMembers() {
            UserGroupMemberEntity member1 = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );
            UserGroupMemberEntity member2 = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_2, Instant.now(), CREATED_BY
            );
            entity.addMember(member1);
            entity.addMember(member2);

            entity.clearMembers();

            assertThat(entity.getMembers()).isEmpty();
            assertThat(member1.getGroup()).isNull();
            assertThat(member2.getGroup()).isNull();
        }

        @Test
        @DisplayName("should update name and description")
        void shouldUpdateNameAndDescription() {
            String newName = "Updated Group";
            String newDescription = "Updated description";

            entity.setName(newName);
            entity.setDescription(newDescription);

            assertThat(entity.getName()).isEqualTo(newName);
            assertThat(entity.getDescription()).isEqualTo(newDescription);
        }

        @Test
        @DisplayName("should set members collection directly")
        void shouldSetMembersCollectionDirectly() {
            UserGroupMemberEntity member = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );
            java.util.Set<UserGroupMemberEntity> members = new java.util.HashSet<>();
            members.add(member);

            entity.setMembers(members);

            assertThat(entity.getMembers()).hasSize(1);
            assertThat(entity.getMembers()).contains(member);
        }
    }

    // ==================== UserGroupMemberEntity Tests ====================

    @Nested
    @DisplayName("UserGroupMemberEntity Tests")
    class UserGroupMemberEntityTests {

        @Test
        @DisplayName("should create member with constructor")
        void shouldCreateMemberWithConstructor() {
            Instant addedAt = Instant.now();
            UserGroupMemberEntity member = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, addedAt, CREATED_BY
            );

            assertThat(member.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(member.getUserId()).isEqualTo(USER_ID_1);
            assertThat(member.getAddedAt()).isEqualTo(addedAt);
            assertThat(member.getAddedBy()).isEqualTo(CREATED_BY);
        }

        @Test
        @DisplayName("should create member with default constructor")
        void shouldCreateMemberWithDefaultConstructor() {
            UserGroupMemberEntity member = new UserGroupMemberEntity();

            assertThat(member.getGroupId()).isNull();
            assertThat(member.getUserId()).isNull();
            assertThat(member.getAddedAt()).isNull();
            assertThat(member.getAddedBy()).isNull();
        }

        @Test
        @DisplayName("should set all fields via setters")
        void shouldSetAllFieldsViaSetters() {
            UserGroupMemberEntity member = new UserGroupMemberEntity();
            Instant addedAt = Instant.now();

            member.setGroupId(GROUP_ID);
            member.setUserId(USER_ID_1);
            member.setAddedAt(addedAt);
            member.setAddedBy(CREATED_BY);

            assertThat(member.getGroupId()).isEqualTo(GROUP_ID);
            assertThat(member.getUserId()).isEqualTo(USER_ID_1);
            assertThat(member.getAddedAt()).isEqualTo(addedAt);
            assertThat(member.getAddedBy()).isEqualTo(CREATED_BY);
        }

        @Test
        @DisplayName("should set group and update groupId")
        void shouldSetGroupAndUpdateGroupId() {
            UserGroupEntity group = new UserGroupEntity();
            group.setGroupId(GROUP_ID);
            UserGroupMemberEntity member = new UserGroupMemberEntity();

            member.setGroup(group);

            assertThat(member.getGroup()).isEqualTo(group);
            assertThat(member.getGroupId()).isEqualTo(GROUP_ID);
        }

        @Test
        @DisplayName("should handle null group in setGroup")
        void shouldHandleNullGroupInSetGroup() {
            UserGroupMemberEntity member = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );

            member.setGroup(null);

            assertThat(member.getGroup()).isNull();
            // Note: groupId is not cleared when setting null group
        }

        @Test
        @DisplayName("equals should return true for same groupId and userId")
        void equalsShouldReturnTrueForSameGroupIdAndUserId() {
            UserGroupMemberEntity member1 = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );
            UserGroupMemberEntity member2 = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now().plusSeconds(100), "other-admin"
            );

            assertThat(member1).isEqualTo(member2);
            assertThat(member1.hashCode()).isEqualTo(member2.hashCode());
        }

        @Test
        @DisplayName("equals should return false for different userId")
        void equalsShouldReturnFalseForDifferentUserId() {
            UserGroupMemberEntity member1 = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );
            UserGroupMemberEntity member2 = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_2, Instant.now(), CREATED_BY
            );

            assertThat(member1).isNotEqualTo(member2);
        }

        @Test
        @DisplayName("equals should return false for different groupId")
        void equalsShouldReturnFalseForDifferentGroupId() {
            UserGroupMemberEntity member1 = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );
            UserGroupMemberEntity member2 = new UserGroupMemberEntity(
                UUID.randomUUID(), USER_ID_1, Instant.now(), CREATED_BY
            );

            assertThat(member1).isNotEqualTo(member2);
        }

        @Test
        @DisplayName("equals should return true for same instance")
        void equalsShouldReturnTrueForSameInstance() {
            UserGroupMemberEntity member = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );

            assertThat(member).isEqualTo(member);
        }

        @Test
        @DisplayName("equals should return false for null")
        void equalsShouldReturnFalseForNull() {
            UserGroupMemberEntity member = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );

            assertThat(member).isNotEqualTo(null);
        }

        @Test
        @DisplayName("equals should return false for different class")
        void equalsShouldReturnFalseForDifferentClass() {
            UserGroupMemberEntity member = new UserGroupMemberEntity(
                GROUP_ID, USER_ID_1, Instant.now(), CREATED_BY
            );

            assertThat(member).isNotEqualTo("not a member");
        }
    }

    // ==================== UserGroupMemberId Tests ====================

    @Nested
    @DisplayName("UserGroupMemberId Tests")
    class UserGroupMemberIdTests {

        @Test
        @DisplayName("should create with default constructor")
        void shouldCreateWithDefaultConstructor() {
            UserGroupMemberEntity.UserGroupMemberId id = new UserGroupMemberEntity.UserGroupMemberId();

            assertThat(id).isNotNull();
        }

        @Test
        @DisplayName("should create with parameterized constructor")
        void shouldCreateWithParameterizedConstructor() {
            UserGroupMemberEntity.UserGroupMemberId id =
                new UserGroupMemberEntity.UserGroupMemberId(GROUP_ID, USER_ID_1);

            assertThat(id).isNotNull();
        }

        @Test
        @DisplayName("equals should return true for same values")
        void equalsShouldReturnTrueForSameValues() {
            UserGroupMemberEntity.UserGroupMemberId id1 =
                new UserGroupMemberEntity.UserGroupMemberId(GROUP_ID, USER_ID_1);
            UserGroupMemberEntity.UserGroupMemberId id2 =
                new UserGroupMemberEntity.UserGroupMemberId(GROUP_ID, USER_ID_1);

            assertThat(id1).isEqualTo(id2);
            assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
        }

        @Test
        @DisplayName("equals should return false for different values")
        void equalsShouldReturnFalseForDifferentValues() {
            UserGroupMemberEntity.UserGroupMemberId id1 =
                new UserGroupMemberEntity.UserGroupMemberId(GROUP_ID, USER_ID_1);
            UserGroupMemberEntity.UserGroupMemberId id2 =
                new UserGroupMemberEntity.UserGroupMemberId(GROUP_ID, USER_ID_2);

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("equals should return true for same instance")
        void equalsShouldReturnTrueForSameInstance() {
            UserGroupMemberEntity.UserGroupMemberId id =
                new UserGroupMemberEntity.UserGroupMemberId(GROUP_ID, USER_ID_1);

            assertThat(id).isEqualTo(id);
        }

        @Test
        @DisplayName("equals should return false for null")
        void equalsShouldReturnFalseForNull() {
            UserGroupMemberEntity.UserGroupMemberId id =
                new UserGroupMemberEntity.UserGroupMemberId(GROUP_ID, USER_ID_1);

            assertThat(id).isNotEqualTo(null);
        }

        @Test
        @DisplayName("equals should return false for different class")
        void equalsShouldReturnFalseForDifferentClass() {
            UserGroupMemberEntity.UserGroupMemberId id =
                new UserGroupMemberEntity.UserGroupMemberId(GROUP_ID, USER_ID_1);

            assertThat(id).isNotEqualTo("not an id");
        }
    }
}
