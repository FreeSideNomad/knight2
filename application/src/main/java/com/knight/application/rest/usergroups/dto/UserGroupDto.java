package com.knight.application.rest.usergroups.dto;

import java.time.Instant;

public record UserGroupDto(
    String groupId,
    String profileId,
    String name,
    String description,
    int memberCount,
    Instant createdAt,
    String createdBy
) {}
