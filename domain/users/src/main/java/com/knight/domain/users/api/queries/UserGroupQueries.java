package com.knight.domain.users.api.queries;

import com.knight.domain.users.types.UserGroupId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Query interface for User Group retrieval.
 */
public interface UserGroupQueries {

    Optional<UserGroupDetail> getGroupById(UserGroupId groupId);

    List<UserGroupSummary> listGroupsByProfile(ProfileId profileId);

    List<UserGroupSummary> findGroupsByUser(UserId userId);

    boolean existsByName(ProfileId profileId, String name);

    record UserGroupSummary(
        String groupId,
        String profileId,
        String name,
        String description,
        int memberCount,
        Instant createdAt,
        String createdBy
    ) {}

    record UserGroupDetail(
        String groupId,
        String profileId,
        String name,
        String description,
        Set<UserGroupMemberInfo> members,
        Instant createdAt,
        String createdBy,
        Instant updatedAt
    ) {}

    record UserGroupMemberInfo(
        String userId,
        Instant addedAt,
        String addedBy
    ) {}
}
