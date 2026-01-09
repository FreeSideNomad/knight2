package com.knight.application.persistence.accountgroups.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AccountGroupEntity.
 */
class AccountGroupEntityTest {

    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final String PROFILE_ID = "servicing:srf:123456789";
    private static final String GROUP_NAME = "Premium Accounts";
    private static final String DESCRIPTION = "Group for premium accounts";
    private static final String CREATED_BY = "admin@example.com";
    private static final String ACCOUNT_ID_1 = "CAN_DDA:DDA:12345:123456789012";
    private static final String ACCOUNT_ID_2 = "CAN_DDA:CC:54321:987654321098";

    private AccountGroupEntity entity;

    @BeforeEach
    void setUp() {
        entity = new AccountGroupEntity();
        entity.setGroupId(GROUP_ID);
        entity.setProfileId(PROFILE_ID);
        entity.setName(GROUP_NAME);
        entity.setDescription(DESCRIPTION);
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(CREATED_BY);
        entity.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("should create entity with default constructor")
    void shouldCreateEntityWithDefaultConstructor() {
        AccountGroupEntity newEntity = new AccountGroupEntity();

        assertThat(newEntity.getGroupId()).isNull();
        assertThat(newEntity.getProfileId()).isNull();
        assertThat(newEntity.getName()).isNull();
        assertThat(newEntity.getDescription()).isNull();
        assertThat(newEntity.getAccountIds()).isEmpty();
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
        assertThat(entity.getAccountIds()).isEmpty();
    }

    @Test
    @DisplayName("should set and get accountIds")
    void shouldSetAndGetAccountIds() {
        Set<String> accountIds = new HashSet<>();
        accountIds.add(ACCOUNT_ID_1);
        accountIds.add(ACCOUNT_ID_2);

        entity.setAccountIds(accountIds);

        assertThat(entity.getAccountIds()).hasSize(2);
        assertThat(entity.getAccountIds()).containsExactlyInAnyOrder(ACCOUNT_ID_1, ACCOUNT_ID_2);
    }

    @Test
    @DisplayName("should add accounts via setAccountIds")
    void shouldAddAccountsViaSetAccountIds() {
        Set<String> accountIds = new HashSet<>();
        accountIds.add(ACCOUNT_ID_1);
        accountIds.add(ACCOUNT_ID_2);
        entity.setAccountIds(accountIds);

        assertThat(entity.getAccountIds()).hasSize(2);
        assertThat(entity.getAccountIds()).contains(ACCOUNT_ID_1, ACCOUNT_ID_2);
    }

    @Test
    @DisplayName("should remove account by setting new set without it")
    void shouldRemoveAccountBySettingNewSet() {
        Set<String> accountIds = new HashSet<>();
        accountIds.add(ACCOUNT_ID_1);
        accountIds.add(ACCOUNT_ID_2);
        entity.setAccountIds(accountIds);

        Set<String> updatedIds = new HashSet<>();
        updatedIds.add(ACCOUNT_ID_2);
        entity.setAccountIds(updatedIds);

        assertThat(entity.getAccountIds()).hasSize(1);
        assertThat(entity.getAccountIds()).contains(ACCOUNT_ID_2);
    }

    @Test
    @DisplayName("should clear all accounts by setting empty set")
    void shouldClearAllAccountsBySettingEmptySet() {
        Set<String> accountIds = new HashSet<>();
        accountIds.add(ACCOUNT_ID_1);
        accountIds.add(ACCOUNT_ID_2);
        entity.setAccountIds(accountIds);

        entity.setAccountIds(new HashSet<>());

        assertThat(entity.getAccountIds()).isEmpty();
    }

    @Test
    @DisplayName("should update name")
    void shouldUpdateName() {
        String newName = "Updated Group Name";

        entity.setName(newName);

        assertThat(entity.getName()).isEqualTo(newName);
    }

    @Test
    @DisplayName("should update description")
    void shouldUpdateDescription() {
        String newDescription = "Updated description";

        entity.setDescription(newDescription);

        assertThat(entity.getDescription()).isEqualTo(newDescription);
    }

    @Test
    @DisplayName("should handle null description")
    void shouldHandleNullDescription() {
        entity.setDescription(null);

        assertThat(entity.getDescription()).isNull();
    }

    @Test
    @DisplayName("should update updatedAt timestamp")
    void shouldUpdateUpdatedAtTimestamp() {
        Instant initialUpdatedAt = entity.getUpdatedAt();
        Instant newUpdatedAt = Instant.now().plusSeconds(3600);

        entity.setUpdatedAt(newUpdatedAt);

        assertThat(entity.getUpdatedAt()).isEqualTo(newUpdatedAt);
        assertThat(entity.getUpdatedAt()).isNotEqualTo(initialUpdatedAt);
    }

    @Test
    @DisplayName("should replace entire accountIds set")
    void shouldReplaceEntireAccountIdsSet() {
        Set<String> initialIds = new HashSet<>();
        initialIds.add(ACCOUNT_ID_1);
        entity.setAccountIds(initialIds);

        Set<String> newAccountIds = new HashSet<>();
        newAccountIds.add(ACCOUNT_ID_2);
        entity.setAccountIds(newAccountIds);

        assertThat(entity.getAccountIds()).hasSize(1);
        assertThat(entity.getAccountIds()).contains(ACCOUNT_ID_2);
        assertThat(entity.getAccountIds()).doesNotContain(ACCOUNT_ID_1);
    }

    @Test
    @DisplayName("should store members with added_at timestamp")
    void shouldStoreMembersWithAddedAtTimestamp() {
        Set<String> accountIds = new HashSet<>();
        accountIds.add(ACCOUNT_ID_1);
        entity.setAccountIds(accountIds);

        assertThat(entity.getMembers()).hasSize(1);
        AccountGroupMemberEmbeddable member = entity.getMembers().iterator().next();
        assertThat(member.getAccountId()).isEqualTo(ACCOUNT_ID_1);
        assertThat(member.getAddedAt()).isNotNull();
    }
}
