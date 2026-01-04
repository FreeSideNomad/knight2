package com.knight.domain.serviceprofiles.api.queries;

import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ProfileId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Query interface for Account Group retrieval.
 */
public interface AccountGroupQueries {

    Optional<AccountGroupDetail> getGroupById(AccountGroupId groupId);

    List<AccountGroupSummary> listGroupsByProfile(ProfileId profileId);

    List<AccountGroupSummary> findGroupsContainingAccount(ProfileId profileId, ClientAccountId accountId);

    boolean existsByName(ProfileId profileId, String name);

    record AccountGroupSummary(
        String groupId,
        String profileId,
        String name,
        String description,
        int accountCount,
        Instant createdAt,
        String createdBy
    ) {}

    record AccountGroupDetail(
        String groupId,
        String profileId,
        String name,
        String description,
        Set<String> accountIds,
        Instant createdAt,
        String createdBy,
        Instant updatedAt
    ) {}
}
