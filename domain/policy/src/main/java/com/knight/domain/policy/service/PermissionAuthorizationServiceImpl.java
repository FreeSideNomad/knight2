package com.knight.domain.policy.service;

import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.repository.PermissionPolicyRepository;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.PredefinedRole;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of PermissionAuthorizationService.
 * Evaluates both role-based (in-memory) and persisted policies.
 */
@Service
public class PermissionAuthorizationServiceImpl implements PermissionAuthorizationService {

    private final PermissionPolicyRepository policyRepository;

    public PermissionAuthorizationServiceImpl(PermissionPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    @Override
    public PermissionResult checkPermission(
            ProfileId profileId,
            UserId userId,
            Set<String> userRoles,
            Action action,
            String resourceId) {

        // Build list of subjects to check
        List<Subject> subjects = buildSubjectList(userId, userRoles);

        // Get all applicable policies
        List<PermissionPolicy> allPolicies = getApplicablePolicies(profileId, userId, userRoles, subjects);

        // Find matching policies
        List<PermissionPolicy> matchingPolicies = allPolicies.stream()
            .filter(p -> p.appliesToAny(subjects))
            .filter(p -> p.matches(action, resourceId))
            .toList();

        return evaluatePolicies(matchingPolicies, action.value());
    }

    @Override
    public PermissionResult checkPermission(
            ProfileId profileId,
            UserId userId,
            Set<String> userRoles,
            Action action) {

        // Build list of subjects to check
        List<Subject> subjects = buildSubjectList(userId, userRoles);

        // Get all applicable policies
        List<PermissionPolicy> allPolicies = getApplicablePolicies(profileId, userId, userRoles, subjects);

        // Find matching policies (action only, ignore resource)
        List<PermissionPolicy> matchingPolicies = allPolicies.stream()
            .filter(p -> p.appliesToAny(subjects))
            .filter(p -> p.matchesAction(action))
            .toList();

        return evaluatePolicies(matchingPolicies, action.value());
    }

    @Override
    public List<PermissionPolicy> getEffectivePermissions(
            ProfileId profileId,
            UserId userId,
            Set<String> userRoles) {

        List<Subject> subjects = buildSubjectList(userId, userRoles);
        return getApplicablePolicies(profileId, userId, userRoles, subjects);
    }

    @Override
    public Set<String> getAllowedActions(
            ProfileId profileId,
            UserId userId,
            Set<String> userRoles) {

        List<PermissionPolicy> policies = getEffectivePermissions(profileId, userId, userRoles);

        return policies.stream()
            .filter(p -> p.effect() == PermissionPolicy.Effect.ALLOW)
            .map(p -> p.action().value())
            .collect(Collectors.toSet());
    }

    /**
     * Build list of subjects for a user (user ID + roles).
     */
    private List<Subject> buildSubjectList(UserId userId, Set<String> userRoles) {
        List<Subject> subjects = new ArrayList<>();

        // Add user subject
        subjects.add(Subject.user(userId.id()));

        // Add role subjects
        for (String role : userRoles) {
            subjects.add(Subject.role(role));
        }

        return subjects;
    }

    /**
     * Get all applicable policies (role-based + persisted).
     */
    private List<PermissionPolicy> getApplicablePolicies(
            ProfileId profileId,
            UserId userId,
            Set<String> userRoles,
            List<Subject> subjects) {

        List<PermissionPolicy> allPolicies = new ArrayList<>();

        // Add role-based (system) policies
        for (String roleName : userRoles) {
            if (PredefinedRole.isPredefinedRole(roleName)) {
                allPolicies.addAll(PermissionPolicy.forRoleName(roleName));
            }
        }

        // Add persisted policies for the profile
        List<PermissionPolicy> persistedPolicies = policyRepository.findByProfileIdAndSubjects(profileId, subjects);
        allPolicies.addAll(persistedPolicies);

        return allPolicies;
    }

    /**
     * Evaluate policies and return result.
     * DENY takes precedence over ALLOW.
     */
    private PermissionResult evaluatePolicies(List<PermissionPolicy> matchingPolicies, String actionName) {
        if (matchingPolicies.isEmpty()) {
            return PermissionResult.noMatch("No matching policy found for action: " + actionName);
        }

        // Check for any DENY policies (they take precedence)
        List<PermissionPolicy> denyPolicies = matchingPolicies.stream()
            .filter(p -> p.effect() == PermissionPolicy.Effect.DENY)
            .toList();

        if (!denyPolicies.isEmpty()) {
            return PermissionResult.denied(
                "Action denied by policy: " + denyPolicies.get(0).description(),
                denyPolicies
            );
        }

        // Check for ALLOW policies
        List<PermissionPolicy> allowPolicies = matchingPolicies.stream()
            .filter(p -> p.effect() == PermissionPolicy.Effect.ALLOW)
            .toList();

        if (!allowPolicies.isEmpty()) {
            return PermissionResult.allowed(allowPolicies);
        }

        return PermissionResult.noMatch("No ALLOW policy found for action: " + actionName);
    }
}
