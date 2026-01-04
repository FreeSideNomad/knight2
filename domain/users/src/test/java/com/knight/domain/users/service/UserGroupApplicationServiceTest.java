package com.knight.domain.users.service;

import com.knight.domain.users.aggregate.UserGroup;
import com.knight.domain.users.api.commands.UserGroupCommands.*;
import com.knight.domain.users.api.queries.UserGroupQueries.*;
import com.knight.domain.users.repository.UserGroupRepository;
import com.knight.domain.users.types.UserGroupId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserGroupApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class UserGroupApplicationServiceTest {

    @Mock
    private UserGroupRepository repository;

    @Captor
    private ArgumentCaptor<UserGroup> groupCaptor;

    private UserGroupApplicationService service;

    private static final ProfileId PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final String GROUP_NAME = "Administrators";
    private static final String GROUP_DESCRIPTION = "Admin user group";
    private static final String CREATED_BY = "admin@example.com";
    private static final UserId USER_ID_1 = UserId.of("user-001");
    private static final UserId USER_ID_2 = UserId.of("user-002");

    @BeforeEach
    void setUp() {
        service = new UserGroupApplicationService(repository);
    }

    // ==================== Create Group Tests ====================

    @Nested
    @DisplayName("createGroup()")
    class CreateGroupTests {

        @Test
        @DisplayName("should create group and return ID")
        void shouldCreateGroupAndReturnId() {
            when(repository.existsByProfileIdAndName(PROFILE_ID, GROUP_NAME)).thenReturn(false);

            CreateGroupCmd cmd = new CreateGroupCmd(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            UserGroupId result = service.createGroup(cmd);

            assertThat(result).isNotNull();
            verify(repository).save(groupCaptor.capture());
            UserGroup savedGroup = groupCaptor.getValue();
            assertThat(savedGroup.name()).isEqualTo(GROUP_NAME);
            assertThat(savedGroup.description()).isEqualTo(GROUP_DESCRIPTION);
            assertThat(savedGroup.profileId()).isEqualTo(PROFILE_ID);
        }

        @Test
        @DisplayName("should throw exception for duplicate group name")
        void shouldThrowExceptionForDuplicateGroupName() {
            when(repository.existsByProfileIdAndName(PROFILE_ID, GROUP_NAME)).thenReturn(true);

            CreateGroupCmd cmd = new CreateGroupCmd(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);

            assertThatThrownBy(() -> service.createGroup(cmd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User group with name already exists: " + GROUP_NAME);

            verify(repository, never()).save(any());
        }
    }

    // ==================== Update Group Tests ====================

    @Nested
    @DisplayName("updateGroup()")
    class UpdateGroupTests {

        @Test
        @DisplayName("should update group name and description")
        void shouldUpdateGroupNameAndDescription() {
            UserGroup existingGroup = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            when(repository.findById(existingGroup.id())).thenReturn(Optional.of(existingGroup));
            when(repository.existsByProfileIdAndName(PROFILE_ID, "New Name")).thenReturn(false);

            UpdateGroupCmd cmd = new UpdateGroupCmd(existingGroup.id(), "New Name", "New Description");
            service.updateGroup(cmd);

            verify(repository).save(groupCaptor.capture());
            UserGroup savedGroup = groupCaptor.getValue();
            assertThat(savedGroup.name()).isEqualTo("New Name");
            assertThat(savedGroup.description()).isEqualTo("New Description");
        }

        @Test
        @DisplayName("should allow update with same name")
        void shouldAllowUpdateWithSameName() {
            UserGroup existingGroup = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            when(repository.findById(existingGroup.id())).thenReturn(Optional.of(existingGroup));

            UpdateGroupCmd cmd = new UpdateGroupCmd(existingGroup.id(), GROUP_NAME, "New Description");
            service.updateGroup(cmd);

            verify(repository).save(any());
            verify(repository, never()).existsByProfileIdAndName(any(), any());
        }

        @Test
        @DisplayName("should throw exception when group not found")
        void shouldThrowExceptionWhenGroupNotFound() {
            UserGroupId nonExistentId = UserGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            UpdateGroupCmd cmd = new UpdateGroupCmd(nonExistentId, "New Name", "New Description");

            assertThatThrownBy(() -> service.updateGroup(cmd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User group not found");
        }

        @Test
        @DisplayName("should throw exception for duplicate name on update")
        void shouldThrowExceptionForDuplicateNameOnUpdate() {
            UserGroup existingGroup = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            when(repository.findById(existingGroup.id())).thenReturn(Optional.of(existingGroup));
            when(repository.existsByProfileIdAndName(PROFILE_ID, "Existing Name")).thenReturn(true);

            UpdateGroupCmd cmd = new UpdateGroupCmd(existingGroup.id(), "Existing Name", "Description");

            assertThatThrownBy(() -> service.updateGroup(cmd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User group with name already exists: Existing Name");
        }
    }

    // ==================== Delete Group Tests ====================

    @Nested
    @DisplayName("deleteGroup()")
    class DeleteGroupTests {

        @Test
        @DisplayName("should delete group")
        void shouldDeleteGroup() {
            UserGroup existingGroup = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            when(repository.findById(existingGroup.id())).thenReturn(Optional.of(existingGroup));

            DeleteGroupCmd cmd = new DeleteGroupCmd(existingGroup.id());
            service.deleteGroup(cmd);

            verify(repository).delete(existingGroup);
        }

        @Test
        @DisplayName("should throw exception when group not found")
        void shouldThrowExceptionWhenGroupNotFound() {
            UserGroupId nonExistentId = UserGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            DeleteGroupCmd cmd = new DeleteGroupCmd(nonExistentId);

            assertThatThrownBy(() -> service.deleteGroup(cmd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User group not found");
        }
    }

    // ==================== Add Members Tests ====================

    @Nested
    @DisplayName("addMembers()")
    class AddMembersTests {

        @Test
        @DisplayName("should add members to group")
        void shouldAddMembersToGroup() {
            UserGroup existingGroup = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            when(repository.findById(existingGroup.id())).thenReturn(Optional.of(existingGroup));

            AddMembersCmd cmd = new AddMembersCmd(existingGroup.id(), Set.of(USER_ID_1, USER_ID_2), CREATED_BY);
            service.addMembers(cmd);

            verify(repository).save(groupCaptor.capture());
            UserGroup savedGroup = groupCaptor.getValue();
            assertThat(savedGroup.memberCount()).isEqualTo(2);
            assertThat(savedGroup.containsMember(USER_ID_1)).isTrue();
            assertThat(savedGroup.containsMember(USER_ID_2)).isTrue();
        }

        @Test
        @DisplayName("should skip existing members")
        void shouldSkipExistingMembers() {
            UserGroup existingGroup = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            existingGroup.addMember(USER_ID_1, CREATED_BY);
            when(repository.findById(existingGroup.id())).thenReturn(Optional.of(existingGroup));

            AddMembersCmd cmd = new AddMembersCmd(existingGroup.id(), Set.of(USER_ID_1, USER_ID_2), CREATED_BY);
            service.addMembers(cmd);

            verify(repository).save(groupCaptor.capture());
            UserGroup savedGroup = groupCaptor.getValue();
            assertThat(savedGroup.memberCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw exception when group not found")
        void shouldThrowExceptionWhenGroupNotFound() {
            UserGroupId nonExistentId = UserGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            AddMembersCmd cmd = new AddMembersCmd(nonExistentId, Set.of(USER_ID_1), CREATED_BY);

            assertThatThrownBy(() -> service.addMembers(cmd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User group not found");
        }
    }

    // ==================== Remove Members Tests ====================

    @Nested
    @DisplayName("removeMembers()")
    class RemoveMembersTests {

        @Test
        @DisplayName("should remove members from group")
        void shouldRemoveMembersFromGroup() {
            UserGroup existingGroup = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            existingGroup.addMember(USER_ID_1, CREATED_BY);
            existingGroup.addMember(USER_ID_2, CREATED_BY);
            when(repository.findById(existingGroup.id())).thenReturn(Optional.of(existingGroup));

            RemoveMembersCmd cmd = new RemoveMembersCmd(existingGroup.id(), Set.of(USER_ID_1));
            service.removeMembers(cmd);

            verify(repository).save(groupCaptor.capture());
            UserGroup savedGroup = groupCaptor.getValue();
            assertThat(savedGroup.memberCount()).isEqualTo(1);
            assertThat(savedGroup.containsMember(USER_ID_1)).isFalse();
            assertThat(savedGroup.containsMember(USER_ID_2)).isTrue();
        }

        @Test
        @DisplayName("should skip non-existent members")
        void shouldSkipNonExistentMembers() {
            UserGroup existingGroup = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            existingGroup.addMember(USER_ID_1, CREATED_BY);
            when(repository.findById(existingGroup.id())).thenReturn(Optional.of(existingGroup));

            RemoveMembersCmd cmd = new RemoveMembersCmd(existingGroup.id(), Set.of(USER_ID_2));
            service.removeMembers(cmd);

            verify(repository).save(groupCaptor.capture());
            UserGroup savedGroup = groupCaptor.getValue();
            assertThat(savedGroup.memberCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw exception when group not found")
        void shouldThrowExceptionWhenGroupNotFound() {
            UserGroupId nonExistentId = UserGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            RemoveMembersCmd cmd = new RemoveMembersCmd(nonExistentId, Set.of(USER_ID_1));

            assertThatThrownBy(() -> service.removeMembers(cmd))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("User group not found");
        }
    }

    // ==================== Query Tests ====================

    @Nested
    @DisplayName("Query Methods")
    class QueryTests {

        @Test
        @DisplayName("getGroupById should return group detail")
        void getGroupByIdShouldReturnGroupDetail() {
            UserGroup group = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            group.addMember(USER_ID_1, CREATED_BY);
            when(repository.findById(group.id())).thenReturn(Optional.of(group));

            Optional<UserGroupDetail> result = service.getGroupById(group.id());

            assertThat(result).isPresent();
            UserGroupDetail detail = result.get();
            assertThat(detail.groupId()).isEqualTo(group.id().id());
            assertThat(detail.profileId()).isEqualTo(PROFILE_ID.urn());
            assertThat(detail.name()).isEqualTo(GROUP_NAME);
            assertThat(detail.description()).isEqualTo(GROUP_DESCRIPTION);
            assertThat(detail.members()).hasSize(1);
        }

        @Test
        @DisplayName("getGroupById should return empty for non-existent group")
        void getGroupByIdShouldReturnEmptyForNonExistentGroup() {
            UserGroupId nonExistentId = UserGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            Optional<UserGroupDetail> result = service.getGroupById(nonExistentId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("listGroupsByProfile should return summaries")
        void listGroupsByProfileShouldReturnSummaries() {
            UserGroup group1 = UserGroup.create(PROFILE_ID, "Group 1", "Description 1", CREATED_BY);
            UserGroup group2 = UserGroup.create(PROFILE_ID, "Group 2", "Description 2", CREATED_BY);
            when(repository.findByProfileId(PROFILE_ID)).thenReturn(List.of(group1, group2));

            List<UserGroupSummary> result = service.listGroupsByProfile(PROFILE_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(UserGroupSummary::name)
                    .containsExactlyInAnyOrder("Group 1", "Group 2");
        }

        @Test
        @DisplayName("findGroupsByUser should return summaries")
        void findGroupsByUserShouldReturnSummaries() {
            UserGroup group = UserGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            group.addMember(USER_ID_1, CREATED_BY);
            when(repository.findByUserId(USER_ID_1)).thenReturn(List.of(group));

            List<UserGroupSummary> result = service.findGroupsByUser(USER_ID_1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo(GROUP_NAME);
        }

        @Test
        @DisplayName("existsByName should return true when exists")
        void existsByNameShouldReturnTrueWhenExists() {
            when(repository.existsByProfileIdAndName(PROFILE_ID, GROUP_NAME)).thenReturn(true);

            boolean result = service.existsByName(PROFILE_ID, GROUP_NAME);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("existsByName should return false when not exists")
        void existsByNameShouldReturnFalseWhenNotExists() {
            when(repository.existsByProfileIdAndName(PROFILE_ID, GROUP_NAME)).thenReturn(false);

            boolean result = service.existsByName(PROFILE_ID, GROUP_NAME);

            assertThat(result).isFalse();
        }
    }
}
