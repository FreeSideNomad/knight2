package com.knight.application.rest.policies.dto;

import java.util.List;
import java.util.Set;

/**
 * Response containing effective permissions for a user.
 */
public record EffectivePermissionsResponse(
    String userId,
    Set<String> roles,
    List<PermissionPolicyDto> policies,
    Set<String> allowedActions
) {}
