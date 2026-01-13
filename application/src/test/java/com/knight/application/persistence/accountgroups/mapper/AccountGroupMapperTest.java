package com.knight.application.persistence.accountgroups.mapper;

import com.knight.application.persistence.accountgroups.entity.AccountGroupEntity;
import com.knight.domain.serviceprofiles.aggregate.AccountGroup;
import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.platform.sharedkernel.AccountSystem;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ProfileId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AccountGroupMapper.
 */
class AccountGroupMapperTest {

    private AccountGroupMapper mapper;

    private static final ProfileId PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final String GROUP_NAME = "Premium Accounts";
    private static final String GROUP_DESCRIPTION = "Group for premium client accounts";
    private static final String CREATED_BY = "admin@example.com";

    // Valid ClientAccountId URNs
    private static final String ACCOUNT_URN_1 = "CAN_DDA:DDA:12345:123456789012";
    private static final String ACCOUNT_URN_2 = "CAN_DDA:CC:54321:987654321098";

    @BeforeEach
    void setUp() {
        mapper = new AccountGroupMapper();
    }

    // ==================== Domain to Entity ====================

    @Nested
    @DisplayName("Domain to Entity Mapping")
    class DomainToEntityTests {

        @Test
        @DisplayName("should map all group fields from domain to entity")
        void shouldMapAllGroupFieldsFromDomainToEntity() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);

            AccountGroupEntity entity = mapper.toEntity(group);

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
        @DisplayName("should map accounts from domain to entity")
        void shouldMapAccountsFromDomainToEntity() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            group.addAccount(ClientAccountId.of(ACCOUNT_URN_1));
            group.addAccount(ClientAccountId.of(ACCOUNT_URN_2));

            AccountGroupEntity entity = mapper.toEntity(group);

            assertThat(entity.getAccountIds()).hasSize(2);
            assertThat(entity.getAccountIds()).containsExactlyInAnyOrder(ACCOUNT_URN_1, ACCOUNT_URN_2);
        }

        @Test
        @DisplayName("should map empty accounts from domain to entity")
        void shouldMapEmptyAccountsFromDomainToEntity() {
            AccountGroup group = AccountGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);

            AccountGroupEntity entity = mapper.toEntity(group);

            assertThat(entity.getAccountIds()).isEmpty();
        }
    }

    // ==================== Entity to Domain ====================

    @Nested
    @DisplayName("Entity to Domain Mapping")
    class EntityToDomainTests {

        @Test
        @DisplayName("should map all group fields from entity to domain")
        void shouldMapAllGroupFieldsFromEntityToDomain() {
            AccountGroupEntity entity = createAccountGroupEntity();

            AccountGroup group = mapper.toDomain(entity);

            assertThat(group).isNotNull();
            assertThat(group.id().value().toString()).isEqualTo(entity.getGroupId().toString());
            assertThat(group.profileId().urn()).isEqualTo(PROFILE_ID.urn());
            assertThat(group.name()).isEqualTo(GROUP_NAME);
            assertThat(group.description()).isEqualTo(GROUP_DESCRIPTION);
            assertThat(group.createdAt()).isEqualTo(entity.getCreatedAt());
            assertThat(group.createdBy()).isEqualTo(CREATED_BY);
            assertThat(group.updatedAt()).isEqualTo(entity.getUpdatedAt());
        }

        @Test
        @DisplayName("should map accounts from entity to domain")
        void shouldMapAccountsFromEntityToDomain() {
            AccountGroupEntity entity = createAccountGroupEntity();
            entity.setAccountIds(Set.of(ACCOUNT_URN_1, ACCOUNT_URN_2));

            AccountGroup group = mapper.toDomain(entity);

            assertThat(group.accounts()).hasSize(2);
            assertThat(group.accounts())
                .extracting(ClientAccountId::urn)
                .containsExactlyInAnyOrder(ACCOUNT_URN_1, ACCOUNT_URN_2);
        }

        @Test
        @DisplayName("should map empty accounts from entity to domain")
        void shouldMapEmptyAccountsFromEntityToDomain() {
            AccountGroupEntity entity = createAccountGroupEntity();

            AccountGroup group = mapper.toDomain(entity);

            assertThat(group.accounts()).isEmpty();
        }
    }

    // ==================== Round-trip ====================

    @Nested
    @DisplayName("Round-trip Mapping")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve all group data in round-trip conversion")
        void shouldPreserveAllGroupDataInRoundTrip() {
            AccountGroup original = AccountGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            original.addAccount(ClientAccountId.of(ACCOUNT_URN_1));
            original.addAccount(ClientAccountId.of(ACCOUNT_URN_2));

            // Round-trip: Domain -> Entity -> Domain
            AccountGroupEntity entity = mapper.toEntity(original);
            AccountGroup reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.id().value().toString()).isEqualTo(original.id().value().toString());
            assertThat(reconstructed.profileId().urn()).isEqualTo(original.profileId().urn());
            assertThat(reconstructed.name()).isEqualTo(original.name());
            assertThat(reconstructed.description()).isEqualTo(original.description());
            assertThat(reconstructed.accountCount()).isEqualTo(original.accountCount());
            assertThat(reconstructed.accounts())
                .extracting(ClientAccountId::urn)
                .containsExactlyInAnyOrderElementsOf(
                    original.accounts().stream().map(ClientAccountId::urn).toList()
                );
        }

        @Test
        @DisplayName("should preserve empty accounts in round-trip")
        void shouldPreserveEmptyAccountsInRoundTrip() {
            AccountGroup original = AccountGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);

            AccountGroupEntity entity = mapper.toEntity(original);
            AccountGroup reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.accounts()).isEmpty();
        }

        @Test
        @DisplayName("should preserve single account in round-trip")
        void shouldPreserveSingleAccountInRoundTrip() {
            AccountGroup original = AccountGroup.create(PROFILE_ID, GROUP_NAME, GROUP_DESCRIPTION, CREATED_BY);
            original.addAccount(ClientAccountId.of(ACCOUNT_URN_1));

            AccountGroupEntity entity = mapper.toEntity(original);
            AccountGroup reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.accounts()).hasSize(1);
            assertThat(reconstructed.accounts().iterator().next().urn()).isEqualTo(ACCOUNT_URN_1);
        }
    }

    // ==================== Helper Methods ====================

    private AccountGroupEntity createAccountGroupEntity() {
        AccountGroupEntity entity = new AccountGroupEntity();
        entity.setGroupId(UUID.randomUUID());
        entity.setProfileId(PROFILE_ID.urn());
        entity.setName(GROUP_NAME);
        entity.setDescription(GROUP_DESCRIPTION);
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(CREATED_BY);
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
