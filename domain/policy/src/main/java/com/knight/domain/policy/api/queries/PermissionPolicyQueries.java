package com.knight.domain.policy.api.queries;

import com.knight.domain.policy.api.types.PolicyDto;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Query interface for PermissionPolicy operations.
 */
public interface PermissionPolicyQueries {

    /**
     * Get a policy by ID.
     */
    Optional<PolicyDto> getPolicyById(String policyId);

    /**
     * List all policies for a profile.
     */
    List<PolicyDto> listPoliciesByProfile(ProfileId profileId);

    /**
     * List policies for a specific subject in a profile.
     */
    List<PolicyDto> listPoliciesBySubject(ProfileId profileId, String subjectUrn);

    /**
     * Get effective permissions for a user (role-based + explicit).
     */
    List<PolicyDto> getEffectivePermissions(ProfileId profileId, UserId userId, Set<String> userRoles);

    /**
     * Check if user has permission for an action.
     */
    AuthorizationResult checkAuthorization(AuthorizationRequest request);

    // Query DTOs

    record AuthorizationRequest(
        ProfileId profileId,
        UserId userId,
        Set<String> userRoles,
        String action,
        String resourceId
    ) {}

    record AuthorizationResult(
        boolean allowed,
        String reason,
        String effectiveEffect
    ) {}
}
