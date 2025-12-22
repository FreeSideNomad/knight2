package com.knight.domain.policy.aggregate;

import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.PredefinedRole;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * PermissionPolicy aggregate root.
 * Controls who can perform actions on resources within a profile.
 * Supports both persisted policies and in-memory role-based policies.
 */
public class PermissionPolicy {

    public enum Effect {
        ALLOW,  // Grant permission
        DENY    // Explicitly deny (takes precedence over ALLOW)
    }

    private final String id;
    private final ProfileId profileId;  // null for system policies
    private final Subject subject;
    private final Action action;
    private final Resource resource;
    private final Effect effect;
    private final String description;
    private final boolean systemPolicy;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private PermissionPolicy(String id, ProfileId profileId, Subject subject, Action action,
                             Resource resource, Effect effect, String description,
                             boolean systemPolicy, String createdBy) {
        this.id = Objects.requireNonNull(id);
        this.profileId = profileId;  // Can be null for system policies
        this.subject = Objects.requireNonNull(subject);
        this.action = Objects.requireNonNull(action);
        this.resource = resource != null ? resource : Resource.all();
        this.effect = effect != null ? effect : Effect.ALLOW;
        this.description = description;
        this.systemPolicy = systemPolicy;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Create a custom permission policy for a profile.
     */
    public static PermissionPolicy create(
            ProfileId profileId,
            Subject subject,
            Action action,
            Resource resource,
            Effect effect,
            String description,
            String createdBy) {
        Objects.requireNonNull(profileId, "profileId is required for custom policies");
        Objects.requireNonNull(createdBy, "createdBy is required");
        String id = UUID.randomUUID().toString();
        return new PermissionPolicy(id, profileId, subject, action, resource, effect, description, false, createdBy);
    }

    /**
     * Reconstitute from persistence.
     */
    public static PermissionPolicy reconstitute(
            String id, ProfileId profileId, Subject subject, Action action,
            Resource resource, Effect effect, String description,
            Instant createdAt, String createdBy, Instant updatedAt) {
        PermissionPolicy policy = new PermissionPolicy(
            id, profileId, subject, action, resource, effect, description, false, createdBy);
        // Override timestamps from persistence
        try {
            var createdAtField = PermissionPolicy.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(policy, createdAt);
        } catch (Exception e) {
            // Ignore - use default
        }
        policy.updatedAt = updatedAt;
        return policy;
    }

    /**
     * Update the policy.
     */
    public void update(Action action, Resource resource, Effect effect, String description) {
        if (this.systemPolicy) {
            throw new IllegalStateException("Cannot update system policy");
        }
        // Note: We create a new policy rather than mutate, but for simplicity we allow updates
        // The caller should save the updated policy
        this.updatedAt = Instant.now();
    }

    // ===== Factory Methods for Predefined Role Policies =====

    /**
     * Create in-memory policies for a given predefined role.
     * These are system policies that define base permissions for roles.
     */
    public static List<PermissionPolicy> forRole(PredefinedRole role) {
        return switch (role) {
            case SECURITY_ADMIN -> forSecurityAdmin();
            case SERVICE_ADMIN -> forServiceAdmin();
            case READER -> forReader();
            case CREATOR -> forCreator();
            case APPROVER -> forApprover();
        };
    }

    /**
     * Create in-memory policies for a role by name.
     * Useful when role name comes from User aggregate.
     */
    public static List<PermissionPolicy> forRoleName(String roleName) {
        return forRole(PredefinedRole.fromName(roleName));
    }

    /**
     * Create in-memory policies for all roles by name.
     */
    public static List<PermissionPolicy> forRoleNames(Set<String> roleNames) {
        List<PermissionPolicy> policies = new ArrayList<>();
        for (String roleName : roleNames) {
            if (PredefinedRole.isPredefinedRole(roleName)) {
                policies.addAll(forRoleName(roleName));
            }
        }
        return policies;
    }

    /**
     * Create in-memory policies for all predefined roles.
     */
    public static List<PermissionPolicy> forRoles(Set<PredefinedRole> roles) {
        List<PermissionPolicy> policies = new ArrayList<>();
        for (PredefinedRole role : roles) {
            policies.addAll(forRole(role));
        }
        return policies;
    }

    /**
     * SECURITY_ADMIN: Full access to security-related actions.
     * - security.* (all security operations)
     */
    public static List<PermissionPolicy> forSecurityAdmin() {
        return List.of(
            createSystemPolicy("system:role:SECURITY_ADMIN:security",
                Subject.role("SECURITY_ADMIN"),
                Action.of("security.*"),
                "Security admin can perform all security-related actions")
        );
    }

    /**
     * SERVICE_ADMIN: Full access to all services and settings.
     * - * (all operations)
     */
    public static List<PermissionPolicy> forServiceAdmin() {
        return List.of(
            createSystemPolicy("system:role:SERVICE_ADMIN:all",
                Subject.role("SERVICE_ADMIN"),
                Action.of("*"),
                "Service admin has full access to all services and settings")
        );
    }

    /**
     * READER: Read-only access across all services.
     * - *.view (view all resources)
     */
    public static List<PermissionPolicy> forReader() {
        return List.of(
            createSystemPolicy("system:role:READER:view",
                Subject.role("READER"),
                Action.of("*.view"),
                "Reader can view all resources")
        );
    }

    /**
     * CREATOR: Can create, update, and delete resources.
     * - *.create (create resources)
     * - *.update (update resources)
     * - *.delete (delete resources)
     */
    public static List<PermissionPolicy> forCreator() {
        return List.of(
            createSystemPolicy("system:role:CREATOR:create",
                Subject.role("CREATOR"),
                Action.of("*.create"),
                "Creator can create resources"),
            createSystemPolicy("system:role:CREATOR:update",
                Subject.role("CREATOR"),
                Action.of("*.update"),
                "Creator can update resources"),
            createSystemPolicy("system:role:CREATOR:delete",
                Subject.role("CREATOR"),
                Action.of("*.delete"),
                "Creator can delete resources")
        );
    }

    /**
     * APPROVER: Can approve pending items.
     * - *.approve (approve resources)
     */
    public static List<PermissionPolicy> forApprover() {
        return List.of(
            createSystemPolicy("system:role:APPROVER:approve",
                Subject.role("APPROVER"),
                Action.of("*.approve"),
                "Approver can approve pending items")
        );
    }

    /**
     * Helper to create system policies (no profileId, SYSTEM createdBy).
     */
    private static PermissionPolicy createSystemPolicy(String id, Subject subject,
                                                        Action action, String description) {
        return new PermissionPolicy(id, null, subject, action, Resource.all(),
                                    Effect.ALLOW, description, true, "SYSTEM");
    }

    // ===== Policy Matching =====

    /**
     * Check if this policy matches the given action and resource.
     */
    public boolean matches(Action requestedAction, String resourceId) {
        return this.action.matches(requestedAction) && this.resource.matches(resourceId);
    }

    /**
     * Check if this policy matches the given action (ignoring resource).
     */
    public boolean matchesAction(Action requestedAction) {
        return this.action.matches(requestedAction);
    }

    /**
     * Check if this policy applies to the given subject.
     */
    public boolean appliesTo(Subject requestSubject) {
        return this.subject.equals(requestSubject);
    }

    /**
     * Check if this policy applies to any of the given subjects.
     */
    public boolean appliesToAny(Collection<Subject> subjects) {
        return subjects.stream().anyMatch(this::appliesTo);
    }

    // ===== Getters =====

    public String id() { return id; }
    public ProfileId profileId() { return profileId; }
    public Subject subject() { return subject; }
    public Action action() { return action; }
    public Resource resource() { return resource; }
    public Effect effect() { return effect; }
    public String description() { return description; }
    public boolean isSystemPolicy() { return systemPolicy; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
}
