package com.knight.application.rest.usergroups.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record ModifyMembersRequest(
    @NotEmpty(message = "At least one user ID is required")
    Set<String> userIds
) {}
