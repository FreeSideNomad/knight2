package com.knight.domain.users.aggregate;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for UserGroup aggregate.
 */
class UserGroupTest {

    private static final ProfileId PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final String GROUP_NAME = "Administrators";
    private static final String GROUP_DESCRIPTION = "Admin user group";
    private static final String CREATED_BY = "admin@example.com";
    private static final UserId USER_ID_1 = UserId.of("user-001");
    private static final UserId USER_ID_2 = UserId.of("user-002");

    // ==================== Creation Tests ====================

    @Nested
    @DisplayName("UserGroup.create()")
    class CreateTests {

        @Test
        @DisplayName("should create user group with valid parameters")
        void shouldCreateUserGroupWithValidParameters() {
            UserGroup group = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);

            assertThat(group.id()).isNotNull();
            assertThat(group.profileId()).isEqualTo(PROFILE_ID);
            assertThat(group.name()).isEqualTo(GROUP_NAME);
            assertThat(group.description()).isEqualTo(GROUP_DESCRIPTION);
            assertThat(group.createdBy()).isEqualTo(CREATED_BY);
            assertThat(group.createdAt()).isNotNull();
            assertThat(group.updatedAt()).isNotNull();
            assertThat(group.members()).isEmpty();
        }

        @Test
        @DisplayName("should generate unique IDs for each group")
        void shouldGenerateUniqueIdsForEachGroup() {
            UserGroup group1 = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            UserGroup group2 = UserGroup.create(PROFILE_ID, "Other Group", GROUP_DESCRIPTION, CREATED_BY);

            assertThat(group1.id()).isNotEqualTo(group2.id());
        }

        @Test
        @DisplayName("should throw exception for null name")
        void shouldThrowExceptionForNullName() {
            assertThatThrownBy(() -> UserGroup.create(PROFILE_ID, null, GROUP_DESCRIPTION, CREATED_BY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User group name is required");
        }

        @Test
        @DisplayName("should throw exception for blank name")
        void shouldThrowExceptionForBlankName() {
            assertThatThrownBy(() -> UserGroup.create(PROFILE_ID, "   ", GROUP_DESCRIPTION, CREATED_BY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User group name is required");
        }

        @Test
        @DisplayName("should throw exception for name exceeding 100 characters")
        void shouldThrowExceptionForNameExceeding100Characters() {
            String longName = "A".repeat(101);
            assertThatThrownBy(() -> UserGroup.create(PROFILE_ID, longName, GROUP_DESCRIPTION, CREATED_BY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User group name cannot exceed 100 characters");
        }

        @Test
        @DisplayName("should accept name with exactly 100 characters")
        void shouldAcceptNameWithExactly100Characters() {
            String exactName = "A".repeat(100);
            UserGroup group = UserGroup.create(PROFILE_ID, exactName, GROUP_DESCRIPTION, CREATED_BY);
            assertThat(group.name()).hasSize(100);
        }
    }

    // ==================== Reconstitute Tests ====================

    @Nested
    @DisplayName("UserGroup.reconstitute()")
    class ReconstituteTests {

        @Test
        @DisplayName("should reconstitute user group with all fields")
        void shouldReconstituteUserGroupWithAllFields() {
            UserGroupId id = UserGroupId.generate();
            Instant createdAt = Instant.parse("2024-01-15T10:00:00Z");
            Instant updatedAt = Instant.parse("2024-01-15T15:00:00Z");
            Set<UserGroupMember> members = Set.of(
                new UserGroupMember(USER_ID_1, Instant.now(), CREATED_BY)
            );

            UserGroup group = UserGroup.reconstitute(
                id, PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION,
                members, createdAt, CREATED_BY, updatedAt
            );

            assertThat(group.id()).isEqualTo(id);
            assertThat(group.profileId()).isEqualTo(PROFILE_ID);
            assertThat(group.name()).isEqualTo(GROUP_NAME);
            assertThat(group.description()).isEqualTo(GROUP_DESCRIPTION);
            assertThat(group.members()).hasSize(1);
            assertThat(group.createdAt()).isEqualTo(createdAt);
            assertThat(group.createdBy()).isEqualTo(CREATED_BY);
            assertThat(group.updatedAt()).isEqualTo(updatedAt);
        }
    }

    // ==================== Update Tests ====================

    @Nested
    @DisplayName("UserGroup.update()")
    class UpdateTests {

        private UserGroup group;

        @BeforeEach
        void setUp() {
            group = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
        }

        @Test
        @DisplayName("should update name and description")
        void shouldUpdateNameAndDescription() {
            Instant beforeUpdate = group.updatedAt();
            String newName = "Updated Group";
            String newDescription = "Updated description";

            // Small delay to ensure updatedAt changes
            try { Thread.sleep(1); } catch (InterruptedException ignored) {}

            group.update(newName, newDescription);

            assertThat(group.name()).isEqualTo(newName);
            assertThat(group.description()).isEqualTo(newDescription);
            assertThat(group.updatedAt()).isAfterOrEqualTo(beforeUpdate);
        }

        @Test
        @DisplayName("should throw exception for null name on update")
        void shouldThrowExceptionForNullNameOnUpdate() {
            assertThatThrownBy(() -> group.update(null, GROUP_DESCRIPTION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User group name is required");
        }

        @Test
        @DisplayName("should throw exception for blank name on update")
        void shouldThrowExceptionForBlankNameOnUpdate() {
            assertThatThrownBy(() -> group.update("", GROUP_DESCRIPTION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User group name is required");
        }
    }

    // ==================== Member Management Tests ====================

    @Nested
    @DisplayName("Member Management")
    class MemberManagementTests {

        private UserGroup group;

        @BeforeEach
        void setUp() {
            group = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
        }

        @Test
        @DisplayName("should add member to group")
        void shouldAddMemberToGroup() {
            group.addMember(USER_ID_1, CREATED_BY);

            assertThat(group.members()).hasSize(1);
            assertThat(group.containsMember(USER_ID_1)).isTrue();
        }

        @Test
        @DisplayName("should add multiple members to group")
        void shouldAddMultipleMembersToGroup() {
            group.addMember(USER_ID_1, CREATED_BY);
            group.addMember(USER_ID_2, CREATED_BY);

            assertThat(group.members()).hasSize(2);
            assertThat(group.memberUserIds()).containsExactlyInAnyOrder(USER_ID_1, USER_ID_2);
        }

        @Test
        @DisplayName("should throw exception when adding null user ID")
        void shouldThrowExceptionWhenAddingNullUserId() {
            assertThatThrownBy(() -> group.addMember(null, CREATED_BY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User ID cannot be null");
        }

        @Test
        @DisplayName("should throw exception when adding duplicate member")
        void shouldThrowExceptionWhenAddingDuplicateMember() {
            group.addMember(USER_ID_1, CREATED_BY);

            assertThatThrownBy(() -> group.addMember(USER_ID_1, CREATED_BY))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User already in group: user-001");
        }

        @Test
        @DisplayName("should remove member from group")
        void shouldRemoveMemberFromGroup() {
            group.addMember(USER_ID_1, CREATED_BY);
            group.addMember(USER_ID_2, CREATED_BY);

            group.removeMember(USER_ID_1);

            assertThat(group.members()).hasSize(1);
            assertThat(group.containsMember(USER_ID_1)).isFalse();
            assertThat(group.containsMember(USER_ID_2)).isTrue();
        }

        @Test
        @DisplayName("should throw exception when removing null user ID")
        void shouldThrowExceptionWhenRemovingNullUserId() {
            assertThatThrownBy(() -> group.removeMember(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User ID cannot be null");
        }

        @Test
        @DisplayName("should throw exception when removing non-existent member")
        void shouldThrowExceptionWhenRemovingNonExistentMember() {
            assertThatThrownBy(() -> group.removeMember(USER_ID_1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User not in group: user-001");
        }

        @Test
        @DisplayName("should clear all members")
        void shouldClearAllMembers() {
            group.addMember(USER_ID_1, CREATED_BY);
            group.addMember(USER_ID_2, CREATED_BY);

            group.clearMembers();

            assertThat(group.members()).isEmpty();
            assertThat(group.memberCount()).isZero();
        }

        @Test
        @DisplayName("should return correct member count")
        void shouldReturnCorrectMemberCount() {
            assertThat(group.memberCount()).isZero();

            group.addMember(USER_ID_1, CREATED_BY);
            assertThat(group.memberCount()).isEqualTo(1);

            group.addMember(USER_ID_2, CREATED_BY);
            assertThat(group.memberCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return unmodifiable members set")
        void shouldReturnUnmodifiableMembersSet() {
            group.addMember(USER_ID_1, CREATED_BY);

            assertThatThrownBy(() -> group.members().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should return unmodifiable member user IDs set")
        void shouldReturnUnmodifiableMemberUserIdsSet() {
            group.addMember(USER_ID_1, CREATED_BY);

            assertThatThrownBy(() -> group.memberUserIds().clear())
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ==================== UserGroupMember Value Object Tests ====================

    @Nested
    @DisplayName("UserGroupMember Value Object")
    class UserGroupMemberTests {

        @Test
        @DisplayName("should create member with all required fields")
        void shouldCreateMemberWithAllRequiredFields() {
            Instant addedAt = Instant.now();
            UserGroupMember member = new UserGroupMember(USER_ID_1, addedAt, CREATED_BY);

            assertThat(member.userId()).isEqualTo(USER_ID_1);
            assertThat(member.addedAt()).isEqualTo(addedAt);
            assertThat(member.addedBy()).isEqualTo(CREATED_BY);
        }

        @Test
        @DisplayName("should throw exception for null userId")
        void shouldThrowExceptionForNullUserId() {
            assertThatThrownBy(() -> new UserGroupMember(null, Instant.now(), CREATED_BY))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("User ID cannot be null");
        }

        @Test
        @DisplayName("should throw exception for null addedAt")
        void shouldThrowExceptionForNullAddedAt() {
            assertThatThrownBy(() -> new UserGroupMember(USER_ID_1, null, CREATED_BY))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Added at cannot be null");
        }

        @Test
        @DisplayName("should throw exception for null addedBy")
        void shouldThrowExceptionForNullAddedBy() {
            assertThatThrownBy(() -> new UserGroupMember(USER_ID_1, Instant.now(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("Added by cannot be null");
        }
    }
}
