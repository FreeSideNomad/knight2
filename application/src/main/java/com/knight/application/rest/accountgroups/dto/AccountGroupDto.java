package com.knight.application.rest.accountgroups.dto;

import java.time.Instant;
import java.util.Set;

public record AccountGroupDto(
    String groupId,
    String profileId,
    String name,
    String description,
    int accountCount,
    Instant createdAt,
    String createdBy
) {}
