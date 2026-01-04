package com.knight.domain.serviceprofiles.aggregate;

import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ProfileId;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AccountGroup aggregate.
 * Tests business logic and invariants without Spring context.
 */
class AccountGroupTest {

    private static final ProfileId PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final ClientAccountId ACCOUNT_ID_1 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000001");
    private static final ClientAccountId ACCOUNT_ID_2 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000002");
    private static final ClientAccountId ACCOUNT_ID_3 = ClientAccountId.of("CAN_DDA:DDA:12345:000000000003");
    private static final String CREATED_BY = "testUser";

    // ==================== Account Group Creation ====================

    @Nested
    @DisplayName("Account Group Creation")
    class AccountGroupCreationTests {

        @Test
        @DisplayName("should create account group with valid name")
        void shouldCreateAccountGroupWithValidName() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test Group", "Test description", CREATED_BY);

            assertThat(group.id()).isNotNull();
            assertThat(group.profileId()).isEqualTo(PROFILE_ID);
            assertThat(group.name()).isEqualTo("Test Group");
            assertThat(group.description()).isEqualTo("Test description");
            assertThat(group.createdBy()).isEqualTo(CREATED_BY);
            assertThat(group.accounts()).isEmpty();
            assertThat(group.accountCount()).isZero();
            assertThat(group.createdAt()).isNotNull();
            assertThat(group.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should reject null name")
        void shouldRejectNullName() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> AccountGroup.create(PROFILE_ID, null, "desc", CREATED_BY))
                .withMessageContaining("name is required");
        }

        @Test
        @DisplayName("should reject blank name")
        void shouldRejectBlankName() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> AccountGroup.create(PROFILE_ID, "   ", "desc", CREATED_BY))
                .withMessageContaining("name is required");
        }

        @Test
        @DisplayName("should reject name exceeding 100 characters")
        void shouldRejectNameExceeding100Characters() {
            String longName = "a".repeat(101);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> AccountGroup.create(PROFILE_ID, longName, "desc", CREATED_BY))
                .withMessageContaining("cannot exceed 100 characters");
        }

        @Test
        @DisplayName("should allow name with exactly 100 characters")
        void shouldAllowNameWithExactly100Characters() {
            String maxLengthName = "a".repeat(100);

            AccountGroup group = AccountGroup.create(PROFILE_ID, maxLengthName, "desc", CREATED_BY);

            assertThat(group.name()).hasSize(100);
        }

        @Test
        @DisplayName("should allow null description")
        void shouldAllowNullDescription() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test Group", null, CREATED_BY);

            assertThat(group.description()).isNull();
        }
    }

    // ==================== Account Group Reconstitution ====================

    @Nested
    @DisplayName("Account Group Reconstitution")
    class AccountGroupReconstitutionTests {

        @Test
        @DisplayName("should reconstitute account group from persistence")
        void shouldReconstituteAccountGroupFromPersistence() {
            AccountGroupId id = AccountGroupId.generate();
            java.time.Instant createdAt = java.time.Instant.now().minusSeconds(3600);
            java.time.Instant updatedAt = java.time.Instant.now();
            Set<ClientAccountId> accounts = Set.of(ACCOUNT_ID_1, ACCOUNT_ID_2);

            AccountGroup group = AccountGroup.reconstitute(
                id, PROFILE_ID, "Reconstituted Group", "Description",
                accounts, createdAt, CREATED_BY, updatedAt
            );

            assertThat(group.id()).isEqualTo(id);
            assertThat(group.profileId()).isEqualTo(PROFILE_ID);
            assertThat(group.name()).isEqualTo("Reconstituted Group");
            assertThat(group.description()).isEqualTo("Description");
            assertThat(group.accounts()).containsExactlyInAnyOrderElementsOf(accounts);
            assertThat(group.createdAt()).isEqualTo(createdAt);
            assertThat(group.createdBy()).isEqualTo(CREATED_BY);
            assertThat(group.updatedAt()).isEqualTo(updatedAt);
        }
    }

    // ==================== Update Account Group ====================

    @Nested
    @DisplayName("Update Account Group")
    class UpdateAccountGroupTests {

        @Test
        @DisplayName("should update name and description")
        void shouldUpdateNameAndDescription() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Original Name", "Original Desc", CREATED_BY);
            java.time.Instant originalUpdatedAt = group.updatedAt();

            group.update("New Name", "New Description");

            assertThat(group.name()).isEqualTo("New Name");
            assertThat(group.description()).isEqualTo("New Description");
            assertThat(group.updatedAt()).isAfterOrEqualTo(originalUpdatedAt);
        }

        @Test
        @DisplayName("should reject update with null name")
        void shouldRejectUpdateWithNullName() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Original", "Desc", CREATED_BY);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> group.update(null, "New Desc"))
                .withMessageContaining("name is required");
        }

        @Test
        @DisplayName("should reject update with name exceeding 100 characters")
        void shouldRejectUpdateWithLongName() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Original", "Desc", CREATED_BY);
            String longName = "a".repeat(101);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> group.update(longName, "New Desc"))
                .withMessageContaining("cannot exceed 100 characters");
        }
    }

    // ==================== Add Single Account ====================

    @Nested
    @DisplayName("Add Single Account")
    class AddSingleAccountTests {

        @Test
        @DisplayName("should add account to group")
        void shouldAddAccountToGroup() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);

            group.addAccount(ACCOUNT_ID_1);

            assertThat(group.accounts()).containsExactly(ACCOUNT_ID_1);
            assertThat(group.accountCount()).isEqualTo(1);
            assertThat(group.containsAccount(ACCOUNT_ID_1)).isTrue();
        }

        @Test
        @DisplayName("should reject null account ID")
        void shouldRejectNullAccountId() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> group.addAccount(null))
                .withMessageContaining("Account ID cannot be null");
        }

        @Test
        @DisplayName("should reject duplicate account")
        void shouldRejectDuplicateAccount() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);
            group.addAccount(ACCOUNT_ID_1);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> group.addAccount(ACCOUNT_ID_1))
                .withMessageContaining("Account already in group");
        }
    }

    // ==================== Add Multiple Accounts ====================

    @Nested
    @DisplayName("Add Multiple Accounts")
    class AddMultipleAccountsTests {

        @Test
        @DisplayName("should add multiple accounts")
        void shouldAddMultipleAccounts() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);

            group.addAccounts(List.of(ACCOUNT_ID_1, ACCOUNT_ID_2, ACCOUNT_ID_3));

            assertThat(group.accounts()).containsExactlyInAnyOrder(ACCOUNT_ID_1, ACCOUNT_ID_2, ACCOUNT_ID_3);
            assertThat(group.accountCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should ignore null collection")
        void shouldIgnoreNullCollection() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);

            group.addAccounts(null);

            assertThat(group.accounts()).isEmpty();
        }

        @Test
        @DisplayName("should ignore null entries in collection")
        void shouldIgnoreNullEntriesInCollection() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);

            group.addAccounts(java.util.Arrays.asList(ACCOUNT_ID_1, null, ACCOUNT_ID_2));

            assertThat(group.accounts()).containsExactlyInAnyOrder(ACCOUNT_ID_1, ACCOUNT_ID_2);
        }

        @Test
        @DisplayName("should skip duplicates when adding multiple accounts")
        void shouldSkipDuplicatesWhenAddingMultiple() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);
            group.addAccount(ACCOUNT_ID_1);

            group.addAccounts(List.of(ACCOUNT_ID_1, ACCOUNT_ID_2));

            assertThat(group.accounts()).containsExactlyInAnyOrder(ACCOUNT_ID_1, ACCOUNT_ID_2);
        }
    }

    // ==================== Remove Account ====================

    @Nested
    @DisplayName("Remove Account")
    class RemoveAccountTests {

        @Test
        @DisplayName("should remove account from group")
        void shouldRemoveAccountFromGroup() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);
            group.addAccount(ACCOUNT_ID_1);
            group.addAccount(ACCOUNT_ID_2);

            group.removeAccount(ACCOUNT_ID_1);

            assertThat(group.accounts()).containsExactly(ACCOUNT_ID_2);
            assertThat(group.containsAccount(ACCOUNT_ID_1)).isFalse();
        }

        @Test
        @DisplayName("should reject null account ID for removal")
        void shouldRejectNullAccountIdForRemoval() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> group.removeAccount(null))
                .withMessageContaining("Account ID cannot be null");
        }

        @Test
        @DisplayName("should reject removing non-existent account")
        void shouldRejectRemovingNonExistentAccount() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);
            group.addAccount(ACCOUNT_ID_1);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> group.removeAccount(ACCOUNT_ID_2))
                .withMessageContaining("Account not in group");
        }
    }

    // ==================== Clear Accounts ====================

    @Nested
    @DisplayName("Clear Accounts")
    class ClearAccountsTests {

        @Test
        @DisplayName("should clear all accounts from group")
        void shouldClearAllAccountsFromGroup() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);
            group.addAccounts(List.of(ACCOUNT_ID_1, ACCOUNT_ID_2, ACCOUNT_ID_3));
            assertThat(group.accountCount()).isEqualTo(3);

            group.clearAccounts();

            assertThat(group.accounts()).isEmpty();
            assertThat(group.accountCount()).isZero();
        }

        @Test
        @DisplayName("should handle clearing empty group")
        void shouldHandleClearingEmptyGroup() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);

            group.clearAccounts();

            assertThat(group.accounts()).isEmpty();
        }
    }

    // ==================== Query Methods ====================

    @Nested
    @DisplayName("Query Methods")
    class QueryMethodsTests {

        @Test
        @DisplayName("should return immutable accounts set")
        void shouldReturnImmutableAccountsSet() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);
            group.addAccount(ACCOUNT_ID_1);

            Set<ClientAccountId> accounts = group.accounts();

            assertThatThrownBy(() -> accounts.add(ACCOUNT_ID_2))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should return correct containsAccount result")
        void shouldReturnCorrectContainsAccountResult() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, "Test", null, CREATED_BY);
            group.addAccount(ACCOUNT_ID_1);

            assertThat(group.containsAccount(ACCOUNT_ID_1)).isTrue();
            assertThat(group.containsAccount(ACCOUNT_ID_2)).isFalse();
        }
    }
}
