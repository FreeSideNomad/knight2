package com.knight.application.rest.accountgroups.dto;

import java.time.Instant;
import java.util.Set;

public record AccountGroupDetailDto(
    String groupId,
    String profileId,
    String name,
    String description,
    Set<String> accountIds,
    Instant createdAt,
    String createdBy,
    Instant updatedAt
) {}
