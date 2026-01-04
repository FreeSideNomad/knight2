package com.knight.application.rest.usergroups.dto;

import java.time.Instant;
import java.util.Set;

public record UserGroupDetailDto(
    String groupId,
    String profileId,
    String name,
    String description,
    Set<UserGroupMemberDto> members,
    Instant createdAt,
    String createdBy,
    Instant updatedAt
) {
    public record UserGroupMemberDto(
        String userId,
        Instant addedAt,
        String addedBy
    ) {}
}
