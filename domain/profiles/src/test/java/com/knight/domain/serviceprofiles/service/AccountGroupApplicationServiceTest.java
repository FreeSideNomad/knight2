package com.knight.domain.serviceprofiles.service;

import com.knight.domain.serviceprofiles.aggregate.AccountGroup;
import com.knight.domain.serviceprofiles.api.commands.AccountGroupCommands.*;
import com.knight.domain.serviceprofiles.api.queries.AccountGroupQueries.*;
import com.knight.domain.serviceprofiles.repository.AccountGroupRepository;
import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ProfileId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AccountGroupApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class AccountGroupApplicationServiceTest {

    private static final ProfileId PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final ClientAccountId ACCOUNT_ID_1 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000001");
    private static final ClientAccountId ACCOUNT_ID_2 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000002");
    private static final String CREATED_BY = "testUser";

    @Mock
    private AccountGroupRepository repository;

    @InjectMocks
    private AccountGroupApplicationService service;

    // ==================== Create Group ====================

    @Nested
    @DisplayName("Create Group")
    class CreateGroupTests {

        @Test
        @DisplayName("should create group successfully")
        void shouldCreateGroupSuccessfully() {
            when(repository.existsByProfileIdAndName(PROFILE_ID, "Test Group")).thenReturn(false);

            CreateGroupCmd cmd = new CreateGroupCmd(PROFILE_ID, "Test Group", "Description", null, CREATED_BY);
            AccountGroupId result = service.createGroup(cmd);

            assertThat(result).isNotNull();
            verify(repository).save(any(AccountGroup.class));
        }

        @Test
        @DisplayName("should create group with initial accounts")
        void shouldCreateGroupWithInitialAccounts() {
            when(repository.existsByProfileIdAndName(PROFILE_ID, "Test Group")).thenReturn(false);

            Set<ClientAccountId> initialAccounts = Set.of(ACCOUNT_ID_1, ACCOUNT_ID_2);
            CreateGroupCmd cmd = new CreateGroupCmd(PROFILE_ID, "Test Group", "Description", initialAccounts, CREATED_BY);
            service.createGroup(cmd);

            ArgumentCaptor<AccountGroup> captor = ArgumentCaptor.forClass(AccountGroup.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().accounts()).containsExactlyInAnyOrderElementsOf(initialAccounts);
        }

        @Test
        @DisplayName("should reject duplicate group name")
        void shouldRejectDuplicateGroupName() {
            when(repository.existsByProfileIdAndName(PROFILE_ID, "Existing Group")).thenReturn(true);

            CreateGroupCmd cmd = new CreateGroupCmd(PROFILE_ID, "Existing Group", "Description", null, CREATED_BY);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.createGroup(cmd))
                .withMessageContaining("already exists");
        }
    }

    // ==================== Update Group ====================

    @Nested
    @DisplayName("Update Group")
    class UpdateGroupTests {

        @Test
        @DisplayName("should update group successfully")
        void shouldUpdateGroupSuccessfully() {
            AccountGroup existingGroup = AccountGroup.create(PROFILE_ID, "Original Name", "Desc", CREATED_BY);
            when(repository.findById(any(AccountGroupId.class))).thenReturn(Optional.of(existingGroup));
            when(repository.existsByProfileIdAndName(PROFILE_ID, "New Name")).thenReturn(false);

            UpdateGroupCmd cmd = new UpdateGroupCmd(existingGroup.id(), "New Name", "New Desc");
            service.updateGroup(cmd);

            assertThat(existingGroup.name()).isEqualTo("New Name");
            assertThat(existingGroup.description()).isEqualTo("New Desc");
            verify(repository).save(existingGroup);
        }

        @Test
        @DisplayName("should allow updating with same name")
        void shouldAllowUpdatingWithSameName() {
            AccountGroup existingGroup = AccountGroup.create(PROFILE_ID, "Same Name", "Desc", CREATED_BY);
            when(repository.findById(any(AccountGroupId.class))).thenReturn(Optional.of(existingGroup));

            UpdateGroupCmd cmd = new UpdateGroupCmd(existingGroup.id(), "Same Name", "New Desc");
            service.updateGroup(cmd);

            verify(repository, never()).existsByProfileIdAndName(any(), any());
            verify(repository).save(existingGroup);
        }

        @Test
        @DisplayName("should reject update with duplicate name")
        void shouldRejectUpdateWithDuplicateName() {
            AccountGroup existingGroup = AccountGroup.create(PROFILE_ID, "Original", "Desc", CREATED_BY);
            when(repository.findById(any(AccountGroupId.class))).thenReturn(Optional.of(existingGroup));
            when(repository.existsByProfileIdAndName(PROFILE_ID, "Duplicate Name")).thenReturn(true);

            UpdateGroupCmd cmd = new UpdateGroupCmd(existingGroup.id(), "Duplicate Name", "Desc");

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateGroup(cmd))
                .withMessageContaining("already exists");
        }

        @Test
        @DisplayName("should reject update for non-existent group")
        void shouldRejectUpdateForNonExistentGroup() {
            AccountGroupId nonExistentId = AccountGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            UpdateGroupCmd cmd = new UpdateGroupCmd(nonExistentId, "Name", "Desc");

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateGroup(cmd))
                .withMessageContaining("not found");
        }
    }

    // ==================== Delete Group ====================

    @Nested
    @DisplayName("Delete Group")
    class DeleteGroupTests {

        @Test
        @DisplayName("should delete group successfully")
        void shouldDeleteGroupSuccessfully() {
            AccountGroup existingGroup = AccountGroup.create(PROFILE_ID, "To Delete", "Desc", CREATED_BY);
            when(repository.findById(any(AccountGroupId.class))).thenReturn(Optional.of(existingGroup));

            DeleteGroupCmd cmd = new DeleteGroupCmd(existingGroup.id());
            service.deleteGroup(cmd);

            verify(repository).delete(existingGroup);
        }

        @Test
        @DisplayName("should reject delete for non-existent group")
        void shouldRejectDeleteForNonExistentGroup() {
            AccountGroupId nonExistentId = AccountGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            DeleteGroupCmd cmd = new DeleteGroupCmd(nonExistentId);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.deleteGroup(cmd))
                .withMessageContaining("not found");
        }
    }

    // ==================== Add Accounts ====================

    @Nested
    @DisplayName("Add Accounts")
    class AddAccountsTests {

        @Test
        @DisplayName("should add accounts to group")
        void shouldAddAccountsToGroup() {
            AccountGroup existingGroup = AccountGroup.create(PROFILE_ID, "Test", "Desc", CREATED_BY);
            when(repository.findById(any(AccountGroupId.class))).thenReturn(Optional.of(existingGroup));

            AddAccountsCmd cmd = new AddAccountsCmd(existingGroup.id(), Set.of(ACCOUNT_ID_1, ACCOUNT_ID_2));
            service.addAccounts(cmd);

            assertThat(existingGroup.accounts()).containsExactlyInAnyOrder(ACCOUNT_ID_1, ACCOUNT_ID_2);
            verify(repository).save(existingGroup);
        }

        @Test
        @DisplayName("should reject add accounts for non-existent group")
        void shouldRejectAddAccountsForNonExistentGroup() {
            AccountGroupId nonExistentId = AccountGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            AddAccountsCmd cmd = new AddAccountsCmd(nonExistentId, Set.of(ACCOUNT_ID_1));

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.addAccounts(cmd))
                .withMessageContaining("not found");
        }
    }

    // ==================== Remove Accounts ====================

    @Nested
    @DisplayName("Remove Accounts")
    class RemoveAccountsTests {

        @Test
        @DisplayName("should remove accounts from group")
        void shouldRemoveAccountsFromGroup() {
            AccountGroup existingGroup = AccountGroup.create(PROFILE_ID, "Test", "Desc", CREATED_BY);
            existingGroup.addAccount(ACCOUNT_ID_1);
            existingGroup.addAccount(ACCOUNT_ID_2);
            when(repository.findById(any(AccountGroupId.class))).thenReturn(Optional.of(existingGroup));

            RemoveAccountsCmd cmd = new RemoveAccountsCmd(existingGroup.id(), Set.of(ACCOUNT_ID_1));
            service.removeAccounts(cmd);

            assertThat(existingGroup.accounts()).containsExactly(ACCOUNT_ID_2);
            verify(repository).save(existingGroup);
        }

        @Test
        @DisplayName("should skip accounts not in group")
        void shouldSkipAccountsNotInGroup() {
            AccountGroup existingGroup = AccountGroup.create(PROFILE_ID, "Test", "Desc", CREATED_BY);
            existingGroup.addAccount(ACCOUNT_ID_1);
            when(repository.findById(any(AccountGroupId.class))).thenReturn(Optional.of(existingGroup));

            // Try to remove ACCOUNT_ID_2 which is not in the group
            RemoveAccountsCmd cmd = new RemoveAccountsCmd(existingGroup.id(), Set.of(ACCOUNT_ID_2));
            service.removeAccounts(cmd);

            assertThat(existingGroup.accounts()).containsExactly(ACCOUNT_ID_1);
            verify(repository).save(existingGroup);
        }

        @Test
        @DisplayName("should reject remove accounts for non-existent group")
        void shouldRejectRemoveAccountsForNonExistentGroup() {
            AccountGroupId nonExistentId = AccountGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            RemoveAccountsCmd cmd = new RemoveAccountsCmd(nonExistentId, Set.of(ACCOUNT_ID_1));

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.removeAccounts(cmd))
                .withMessageContaining("not found");
        }
    }

    // ==================== Set Accounts ====================

    @Nested
    @DisplayName("Set Accounts")
    class SetAccountsTests {

        @Test
        @DisplayName("should replace all accounts in group")
        void shouldReplaceAllAccountsInGroup() {
            AccountGroup existingGroup = AccountGroup.create(PROFILE_ID, "Test", "Desc", CREATED_BY);
            existingGroup.addAccount(ACCOUNT_ID_1);
            when(repository.findById(any(AccountGroupId.class))).thenReturn(Optional.of(existingGroup));

            SetAccountsCmd cmd = new SetAccountsCmd(existingGroup.id(), Set.of(ACCOUNT_ID_2));
            service.setAccounts(cmd);

            assertThat(existingGroup.accounts()).containsExactly(ACCOUNT_ID_2);
            verify(repository).save(existingGroup);
        }

        @Test
        @DisplayName("should reject set accounts for non-existent group")
        void shouldRejectSetAccountsForNonExistentGroup() {
            AccountGroupId nonExistentId = AccountGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            SetAccountsCmd cmd = new SetAccountsCmd(nonExistentId, Set.of(ACCOUNT_ID_1));

            assertThatIllegalArgumentException()
                .isThrownBy(() -> service.setAccounts(cmd))
                .withMessageContaining("not found");
        }
    }

    // ==================== Get Group By ID ====================

    @Nested
    @DisplayName("Get Group By ID")
    class GetGroupByIdTests {

        @Test
        @DisplayName("should return group detail when found")
        void shouldReturnGroupDetailWhenFound() {
            AccountGroup existingGroup = AccountGroup.create(PROFILE_ID, "Test Group", "Description", CREATED_BY);
            existingGroup.addAccount(ACCOUNT_ID_1);
            when(repository.findById(any(AccountGroupId.class))).thenReturn(Optional.of(existingGroup));

            Optional<AccountGroupDetail> result = service.getGroupById(existingGroup.id());

            assertThat(result).isPresent();
            assertThat(result.get().name()).isEqualTo("Test Group");
            assertThat(result.get().description()).isEqualTo("Description");
            assertThat(result.get().accountIds()).containsExactly(ACCOUNT_ID_1.urn());
        }

        @Test
        @DisplayName("should return empty when group not found")
        void shouldReturnEmptyWhenGroupNotFound() {
            AccountGroupId nonExistentId = AccountGroupId.generate();
            when(repository.findById(nonExistentId)).thenReturn(Optional.empty());

            Optional<AccountGroupDetail> result = service.getGroupById(nonExistentId);

            assertThat(result).isEmpty();
        }
    }

    // ==================== List Groups By Profile ====================

    @Nested
    @DisplayName("List Groups By Profile")
    class ListGroupsByProfileTests {

        @Test
        @DisplayName("should return groups for profile")
        void shouldReturnGroupsForProfile() {
            AccountGroup group1 = AccountGroup.create(PROFILE_ID, "Group 1", "Desc 1", CREATED_BY);
            AccountGroup group2 = AccountGroup.create(PROFILE_ID, "Group 2", "Desc 2", CREATED_BY);
            when(repository.findByProfileId(PROFILE_ID)).thenReturn(List.of(group1, group2));

            List<AccountGroupSummary> result = service.listGroupsByProfile(PROFILE_ID);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AccountGroupSummary::name)
                .containsExactlyInAnyOrder("Group 1", "Group 2");
        }

        @Test
        @DisplayName("should return empty list when no groups exist")
        void shouldReturnEmptyListWhenNoGroupsExist() {
            when(repository.findByProfileId(PROFILE_ID)).thenReturn(List.of());

            List<AccountGroupSummary> result = service.listGroupsByProfile(PROFILE_ID);

            assertThat(result).isEmpty();
        }
    }

    // ==================== Find Groups Containing Account ====================

    @Nested
    @DisplayName("Find Groups Containing Account")
    class FindGroupsContainingAccountTests {

        @Test
        @DisplayName("should return groups containing account")
        void shouldReturnGroupsContainingAccount() {
            AccountGroup group1 = AccountGroup.create(PROFILE_ID, "Group 1", "Desc", CREATED_BY);
            group1.addAccount(ACCOUNT_ID_1);
            AccountGroup group2 = AccountGroup.create(PROFILE_ID, "Group 2", "Desc", CREATED_BY);
            group2.addAccount(ACCOUNT_ID_2);
            when(repository.findByProfileId(PROFILE_ID)).thenReturn(List.of(group1, group2));

            List<AccountGroupSummary> result = service.findGroupsContainingAccount(PROFILE_ID, ACCOUNT_ID_1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Group 1");
        }

        @Test
        @DisplayName("should return empty list when account not in any group")
        void shouldReturnEmptyListWhenAccountNotInAnyGroup() {
            AccountGroup group1 = AccountGroup.create(PROFILE_ID, "Group 1", "Desc", CREATED_BY);
            group1.addAccount(ACCOUNT_ID_2);
            when(repository.findByProfileId(PROFILE_ID)).thenReturn(List.of(group1));

            List<AccountGroupSummary> result = service.findGroupsContainingAccount(PROFILE_ID, ACCOUNT_ID_1);

            assertThat(result).isEmpty();
        }
    }

    // ==================== Exists By Name ====================

    @Nested
    @DisplayName("Exists By Name")
    class ExistsByNameTests {

        @Test
        @DisplayName("should return true when group exists")
        void shouldReturnTrueWhenGroupExists() {
            when(repository.existsByProfileIdAndName(PROFILE_ID, "Existing")).thenReturn(true);

            boolean result = service.existsByName(PROFILE_ID, "Existing");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when group does not exist")
        void shouldReturnFalseWhenGroupDoesNotExist() {
            when(repository.existsByProfileIdAndName(PROFILE_ID, "NonExisting")).thenReturn(false);

            boolean result = service.existsByName(PROFILE_ID, "NonExisting");

            assertThat(result).isFalse();
        }
    }
}
