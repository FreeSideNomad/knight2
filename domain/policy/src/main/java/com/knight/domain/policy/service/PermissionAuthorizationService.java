package com.knight.domain.policy.service;

import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.util.List;
import java.util.Set;

/**
 * Service for evaluating permission policies and authorizing actions.
 */
public interface PermissionAuthorizationService {

    /**
     * Check if a user can perform an action on a resource.
     *
     * @param profileId the profile context
     * @param userId the user attempting the action
     * @param userRoles the user's assigned roles
     * @param action the action being attempted
     * @param resourceId the resource being acted upon
     * @return authorization result with details
     */
    PermissionResult checkPermission(
        ProfileId profileId,
        UserId userId,
        Set<String> userRoles,
        Action action,
        String resourceId
    );

    /**
     * Check if a user can perform an action (without resource check).
     */
    PermissionResult checkPermission(
        ProfileId profileId,
        UserId userId,
        Set<String> userRoles,
        Action action
    );

    /**
     * Get all effective permissions for a user in a profile.
     * Combines role-based policies and explicit policies.
     */
    List<PermissionPolicy> getEffectivePermissions(
        ProfileId profileId,
        UserId userId,
        Set<String> userRoles
    );

    /**
     * Get all actions a user is allowed to perform in a profile.
     */
    Set<String> getAllowedActions(
        ProfileId profileId,
        UserId userId,
        Set<String> userRoles
    );

    /**
     * Result of permission check.
     */
    record PermissionResult(
        boolean allowed,
        String reason,
        List<PermissionPolicy> matchingPolicies,
        PermissionPolicy.Effect effectiveEffect
    ) {
        public static PermissionResult allowed(List<PermissionPolicy> policies) {
            return new PermissionResult(true, "Permission granted", policies, PermissionPolicy.Effect.ALLOW);
        }

        public static PermissionResult denied(String reason, List<PermissionPolicy> policies) {
            return new PermissionResult(false, reason, policies,
                policies.isEmpty() ? null : PermissionPolicy.Effect.DENY);
        }

        public static PermissionResult noMatch(String reason) {
            return new PermissionResult(false, reason, List.of(), null);
        }
    }
}
