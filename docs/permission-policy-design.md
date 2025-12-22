# Permission Policy Design Document

## Overview

This document defines the design for managing Permission Policies within the Knight Platform. Permission policies control who can perform actions on resources within a profile.

### Requirements Summary

1. **Profile Ownership**: Permission policies are owned by a Profile (ProfileId)
2. **Hierarchical Actions**: Actions follow a URN-like hierarchy (service-group.service.resource-type.action)
3. **Flexible Subjects**: Policies can target users, groups, or roles
4. **Resource Scoping**: Policies can be scoped to specific resources or wildcards
5. **Effect Types**: Support for ALLOW and DENY (DENY takes precedence)
6. **Predefined Roles**: System roles map to common action patterns

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Knight Platform                                    │
│                                                                                 │
│  ┌─────────────────┐    ┌─────────────────────────────────────────────────────┐ │
│  │   Profile       │    │              Permission Policy Engine               │ │
│  │   (Owner)       │───▶│                                                     │ │
│  └─────────────────┘    │  ┌───────────────────────────────────────────────┐  │ │
│                         │  │            PermissionPolicy                   │  │ │
│                         │  │                                               │  │ │
│                         │  │  - Subject (user/group/role)                  │  │ │
│                         │  │  - Action (hierarchical URN pattern)          │  │ │
│                         │  │  - Resource (resource pattern)                │  │ │
│                         │  │  - Effect (ALLOW/DENY)                        │  │ │
│                         │  └───────────────────────────────────────────────┘  │ │
│                         └─────────────────────────────────────────────────────┘ │
│                                        │                                        │
│                                        ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────────────┐│
│  │                         Authorization Service                               ││
│  │  ┌─────────────────────────────┐  ┌─────────────────────────────────────┐   ││
│  │  │  Can User Perform Action?   │  │  Evaluate Policy Match              │   ││
│  │  │  (Permission Check)         │  │  (Action + Resource + Subject)      │   ││
│  │  └─────────────────────────────┘  └─────────────────────────────────────┘   ││
│  └─────────────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Value Objects

### Action

Action is a hierarchical URN-like string representing an operation. The hierarchy is:

```
{service-group}.{service}.{resource-type}.{operation}
```

| Level | Description | Examples |
|-------|-------------|----------|
| Service Group | Top-level category | `security`, `payments`, `reporting` |
| Service | Specific service | `ach-payments`, `wire-payments`, `users` |
| Resource Type | Type of resource (optional) | `single-payment`, `recurring-payment`, `user` |
| Operation | The action being performed | `create`, `view`, `approve`, `delete` |

#### Action Examples

```java
// Full qualified actions
payments.ach-payments.single-payment.create
payments.ach-payments.recurring-payment.create
payments.wire-payments.wire-template.create
payments.wire-payments.wire-template.approve
reporting.balance-and-transactions.transactions.view
security.users.user.create
security.users.permission.create
security.approvals.approval-policy.create

// Wildcard patterns
*                                    // matches any action
payments.*                           // matches any action under payments
payments.ach-payments.*              // matches any ACH payment action
*.create                             // matches any create action
*.approve                            // matches any approve action
security.*                           // matches any security action
```

#### Action Value Object

```java
package com.knight.domain.policy.types;

import java.util.regex.Pattern;

/**
 * Action value object representing a hierarchical action URN.
 * Supports wildcards for pattern matching.
 */
public record Action(String value) {

    private static final Pattern VALID_ACTION = Pattern.compile(
        "^(\\*|[a-z][a-z0-9-]*)(\\.([a-z][a-z0-9-]*|\\*))*$"
    );

    public Action {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Action cannot be null or blank");
        }
        if (!VALID_ACTION.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid action format: " + value);
        }
    }

    /**
     * Check if this action pattern matches the given action.
     * Supports wildcards: * matches any segment, *.action matches suffix.
     */
    public boolean matches(Action action) {
        if ("*".equals(this.value)) {
            return true;
        }

        String[] patternParts = this.value.split("\\.");
        String[] actionParts = action.value.split("\\.");

        // Handle suffix wildcards like *.create
        if (patternParts.length == 2 && "*".equals(patternParts[0])) {
            return action.value.endsWith("." + patternParts[1]);
        }

        // Handle prefix wildcards like payments.*
        if (patternParts.length >= 1 && "*".equals(patternParts[patternParts.length - 1])) {
            String prefix = this.value.substring(0, this.value.length() - 2); // Remove .*
            return action.value.startsWith(prefix + ".");
        }

        // Exact match
        return this.value.equals(action.value);
    }

    public static Action of(String value) {
        return new Action(value);
    }

    public static Action all() {
        return new Action("*");
    }
}
```

---

### Subject

Subject represents who the policy applies to. It can be a user, group, or role.

```
user:{userId}       - Specific user by UUID
group:{groupId}     - Group of users by UUID
role:{roleName}     - Predefined role
```

#### Subject Value Object

```java
package com.knight.domain.policy.types;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Subject value object representing who a policy applies to.
 * Can be a user, group, or role.
 */
public record Subject(SubjectType type, String identifier) {

    public enum SubjectType {
        USER,   // user:{uuid}
        GROUP,  // group:{uuid}
        ROLE    // role:{roleName}
    }

    private static final Pattern ROLE_NAME = Pattern.compile("^[a-z][a-z0-9-]*$");

    public Subject {
        if (type == null) {
            throw new IllegalArgumentException("Subject type cannot be null");
        }
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Subject identifier cannot be null or blank");
        }

        // Validate identifier format based on type
        if (type == SubjectType.USER || type == SubjectType.GROUP) {
            try {
                UUID.fromString(identifier);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid UUID for " + type + ": " + identifier);
            }
        } else if (type == SubjectType.ROLE) {
            if (!ROLE_NAME.matcher(identifier).matches()) {
                throw new IllegalArgumentException("Invalid role name: " + identifier);
            }
        }
    }

    /**
     * Parse a subject from URN format (user:uuid, group:uuid, role:name).
     */
    public static Subject fromUrn(String urn) {
        if (urn == null || !urn.contains(":")) {
            throw new IllegalArgumentException("Invalid subject URN: " + urn);
        }
        String[] parts = urn.split(":", 2);
        SubjectType type = switch (parts[0].toLowerCase()) {
            case "user" -> SubjectType.USER;
            case "group" -> SubjectType.GROUP;
            case "role" -> SubjectType.ROLE;
            default -> throw new IllegalArgumentException("Unknown subject type: " + parts[0]);
        };
        return new Subject(type, parts[1]);
    }

    public static Subject user(String userId) {
        return new Subject(SubjectType.USER, userId);
    }

    public static Subject group(String groupId) {
        return new Subject(SubjectType.GROUP, groupId);
    }

    public static Subject role(String roleName) {
        return new Subject(SubjectType.ROLE, roleName);
    }

    public String toUrn() {
        return type.name().toLowerCase() + ":" + identifier;
    }
}
```

---

### Resource

Resource represents the scope of resources the policy applies to. It can be a list of specific resource URNs or wildcards.

```
CAN_DDA:DDA:00000:081154333874                    // Specific account
CAN_DDA:DDA:00000:081154333874,CAN_DDA:DDA:*      // Multiple with wildcard
CAN_DDA:DDA:*                                      // All DDA accounts in CAN_DDA
*:DDA:*                                            // All DDA accounts in any system
*                                                  // All resources (default if not specified)
```

#### Resource Value Object

```java
package com.knight.domain.policy.types;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resource value object representing the scope of resources a policy applies to.
 * Supports wildcards and comma-separated lists.
 */
public record Resource(String value) {

    public Resource {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Resource cannot be null or blank");
        }
    }

    /**
     * Check if this resource pattern matches the given resource.
     */
    public boolean matches(String resourceId) {
        if ("*".equals(this.value)) {
            return true;
        }

        // Check each pattern in comma-separated list
        String[] patterns = this.value.split(",");
        for (String pattern : patterns) {
            if (matchesPattern(pattern.trim(), resourceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String pattern, String resourceId) {
        if ("*".equals(pattern)) {
            return true;
        }

        // Convert wildcard pattern to regex
        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*");

        return Pattern.matches("^" + regex + "$", resourceId);
    }

    /**
     * Get list of individual resource patterns.
     */
    public List<String> patterns() {
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .toList();
    }

    public static Resource of(String value) {
        return new Resource(value);
    }

    public static Resource all() {
        return new Resource("*");
    }

    public static Resource ofList(List<String> resourceIds) {
        return new Resource(String.join(",", resourceIds));
    }
}
```

---

## Predefined Roles

System roles provide default action patterns for common use cases.

| Role | Action Pattern | Description |
|------|----------------|-------------|
| `super-admin` | `*` | All actions on all resources |
| `security-admin` | `security.*` | All security-related actions |
| `approver` | `*.approve` | Can approve any approvable action |
| `creator` | `*.create` | Can create any resource |
| `viewer` | `*.view` | Can view any resource |

### Role Definition

```java
package com.knight.domain.policy.types;

/**
 * Predefined system roles with default action patterns.
 */
public enum PredefinedRole {

    SUPER_ADMIN("super-admin", "*", "All actions on all resources"),
    SECURITY_ADMIN("security-admin", "security.*", "All security-related actions"),
    APPROVER("approver", "*.approve", "Can approve any approvable action"),
    CREATOR("creator", "*.create", "Can create any resource"),
    VIEWER("viewer", "*.view", "Can view any resource");

    private final String roleName;
    private final String actionPattern;
    private final String description;

    PredefinedRole(String roleName, String actionPattern, String description) {
        this.roleName = roleName;
        this.actionPattern = actionPattern;
        this.description = description;
    }

    public String roleName() { return roleName; }
    public Action actionPattern() { return Action.of(actionPattern); }
    public String description() { return description; }

    public static PredefinedRole fromName(String name) {
        for (PredefinedRole role : values()) {
            if (role.roleName.equals(name)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown predefined role: " + name);
    }
}
```

---

## Domain Model

### PermissionPolicy Aggregate

Permission policies control who can perform actions on resources.

```java
package com.knight.domain.policy.aggregate;

import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PermissionPolicy aggregate root.
 * Controls who can perform actions on resources within a profile.
 */
public class PermissionPolicy {

    public enum Effect {
        ALLOW,  // Grant permission
        DENY    // Explicitly deny (takes precedence over ALLOW)
    }

    private final String id;
    private final ProfileId profileId;
    private final Subject subject;
    private final Action action;
    private final Resource resource;
    private final Effect effect;
    private final String description;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private PermissionPolicy(String id, ProfileId profileId, Subject subject,
                             Action action, Resource resource, Effect effect,
                             String description, String createdBy) {
        this.id = Objects.requireNonNull(id);
        this.profileId = Objects.requireNonNull(profileId);
        this.subject = Objects.requireNonNull(subject);
        this.action = Objects.requireNonNull(action);
        this.resource = resource != null ? resource : Resource.all();
        this.effect = effect != null ? effect : Effect.ALLOW;
        this.description = description;
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static PermissionPolicy create(
            ProfileId profileId,
            Subject subject,
            Action action,
            Resource resource,
            Effect effect,
            String description,
            String createdBy) {
        String id = UUID.randomUUID().toString();
        return new PermissionPolicy(id, profileId, subject, action, resource, effect, description, createdBy);
    }

    /**
     * Check if this policy matches the given action and resource.
     */
    public boolean matches(Action requestedAction, String resourceId) {
        return this.action.matches(requestedAction) && this.resource.matches(resourceId);
    }

    /**
     * Check if this policy applies to the given subject.
     */
    public boolean appliesTo(Subject requestSubject) {
        return this.subject.equals(requestSubject);
    }

    // Getters
    public String id() { return id; }
    public ProfileId profileId() { return profileId; }
    public Subject subject() { return subject; }
    public Action action() { return action; }
    public Resource resource() { return resource; }
    public Effect effect() { return effect; }
    public String description() { return description; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
}
```

---

## Permission Authorization Service

The authorization service evaluates permission policies to determine if actions are allowed.

```java
package com.knight.domain.policy.service;

import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.util.List;
import java.util.Set;

/**
 * Permission authorization service for evaluating permission policies.
 */
public interface PermissionAuthorizationService {

    /**
     * Check if a user can perform an action on a resource.
     *
     * @param profileId the profile context
     * @param userId the user attempting the action
     * @param action the action being attempted
     * @param resourceId the resource being acted upon
     * @return authorization result with details
     */
    PermissionResult checkPermission(ProfileId profileId, UserId userId, Action action, String resourceId);

    /**
     * Get all permissions for a user in a profile.
     */
    List<PermissionPolicy> getUserPermissions(ProfileId profileId, UserId userId);

    /**
     * Get effective permissions for a subject (resolves role patterns).
     */
    Set<String> getEffectiveActions(ProfileId profileId, Subject subject);

    // Result record

    record PermissionResult(
        boolean allowed,
        String reason,
        List<PermissionPolicy> matchingPolicies,
        PermissionPolicy.Effect effectiveEffect
    ) {
        public static PermissionResult allowed(List<PermissionPolicy> policies) {
            return new PermissionResult(true, null, policies, PermissionPolicy.Effect.ALLOW);
        }

        public static PermissionResult denied(String reason, List<PermissionPolicy> policies) {
            return new PermissionResult(false, reason, policies, PermissionPolicy.Effect.DENY);
        }

        public static PermissionResult noMatch(String reason) {
            return new PermissionResult(false, reason, List.of(), null);
        }
    }
}
```

---

## API Design

### REST Endpoints

```
POST   /api/profiles/{profileId}/permission-policies        # Create permission policy
GET    /api/profiles/{profileId}/permission-policies        # List permission policies
GET    /api/profiles/{profileId}/permission-policies/{id}   # Get permission policy
PUT    /api/profiles/{profileId}/permission-policies/{id}   # Update permission policy
DELETE /api/profiles/{profileId}/permission-policies/{id}   # Delete permission policy

POST   /api/profiles/{profileId}/authorize                  # Check authorization
GET    /api/profiles/{profileId}/users/{userId}/permissions # Get user permissions
```

### DTOs

```java
// Create Permission Policy
record CreatePermissionPolicyRequest(
    String subject,      // user:uuid, group:uuid, or role:name
    String action,       // action pattern
    String resource,     // resource pattern (optional, defaults to *)
    String effect,       // ALLOW or DENY
    String description
) {}

// Permission Policy Response
record PermissionPolicyDto(
    String id,
    String profileId,
    String subject,
    String action,
    String resource,
    String effect,
    String description,
    Instant createdAt,
    String createdBy
) {}

// Authorization Check
record AuthorizeRequest(
    String action,
    String resourceId
) {}

record AuthorizeResponse(
    boolean allowed,
    String reason,
    String effectiveEffect
) {}
```

---

## Database Schema

```sql
-- Permission Policies
CREATE TABLE permission_policies (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL,
    subject_type VARCHAR(20) NOT NULL,          -- USER, GROUP, ROLE
    subject_identifier VARCHAR(255) NOT NULL,   -- UUID or role name
    action_pattern VARCHAR(500) NOT NULL,       -- Action URN pattern
    resource_pattern VARCHAR(1000) NOT NULL DEFAULT '*',  -- Resource pattern
    effect VARCHAR(10) NOT NULL DEFAULT 'ALLOW', -- ALLOW, DENY
    description NVARCHAR(500),
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT FK_permission_policies_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id),
    CONSTRAINT CHK_permission_effect CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT CHK_permission_subject_type CHECK (subject_type IN ('USER', 'GROUP', 'ROLE'))
);

CREATE INDEX idx_permission_policies_profile ON permission_policies(profile_id);
CREATE INDEX idx_permission_policies_subject ON permission_policies(subject_type, subject_identifier);
CREATE INDEX idx_permission_policies_action ON permission_policies(action_pattern);

-- User Groups (for group-based policies)
CREATE TABLE user_groups (
    group_id UNIQUEIDENTIFIER PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    description NVARCHAR(500),
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT FK_user_groups_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id),
    CONSTRAINT UQ_user_groups_name UNIQUE(profile_id, name)
);

CREATE INDEX idx_user_groups_profile ON user_groups(profile_id);

-- User Group Members
CREATE TABLE user_group_members (
    group_id UNIQUEIDENTIFIER NOT NULL,
    user_id UNIQUEIDENTIFIER NOT NULL,
    added_at DATETIME2 NOT NULL,
    added_by NVARCHAR(255) NOT NULL,

    PRIMARY KEY (group_id, user_id),
    CONSTRAINT FK_user_group_members_group FOREIGN KEY (group_id)
        REFERENCES user_groups(group_id) ON DELETE CASCADE,
    CONSTRAINT FK_user_group_members_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_group_members_group ON user_group_members(group_id);
CREATE INDEX idx_user_group_members_user ON user_group_members(user_id);
```

---

## Permission Check Flow

```
┌─────────────────┐
│  User Request   │
│  (action, res)  │
└────────┬────────┘
         │
         ▼
┌─────────────────────┐
│ Resolve User Roles  │ ◄── From user.roles (User aggregate)
│ and Groups          │
└────────┬────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  Build Subject List                  │
│  - Subject.user(userId)             │
│  - Subject.role(role) for each role │
│  - Subject.group(groupId) for groups│
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  Find Matching Permission Policies   │
│  - Match action pattern              │
│  - Match resource pattern            │
│  - Match any subject in list         │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  Evaluate Policies                   │
│  - DENY takes precedence over ALLOW │
│  - Check predefined role mappings   │
│  - Return final decision            │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────┐
│  Return Result      │
│  (allowed/denied)   │
└─────────────────────┘
```

---

## Integration with User Roles

The User aggregate already has roles defined. Here's how they map to policy subjects:

| User Role | Policy Subject | Default Action Pattern |
|-----------|----------------|------------------------|
| `SECURITY_ADMIN` | `role:security-admin` | `security.*` |
| `SERVICE_ADMIN` | `role:service-admin` | Service-specific patterns |
| `READER` | `role:viewer` | `*.view` |
| `CREATOR` | `role:creator` | `*.create` |
| `APPROVER` | `role:approver` | `*.approve` |

When checking permissions, the system will:
1. Get user's assigned roles from the User aggregate
2. Map each role to a policy subject
3. Include predefined role action patterns
4. Evaluate against explicit policies

---

## Implementation Phases

### Phase 1: Value Objects and Domain Model
1. Implement Action, Subject, Resource value objects
2. Implement PredefinedRole enum
3. Create PermissionPolicy aggregate
4. Add database migration for permission_policies table

### Phase 2: Repository and Service Layer
1. Implement PermissionPolicyRepository
2. Implement PermissionAuthorizationService
3. Add policy caching for performance

### Phase 3: REST API
1. Create PermissionPolicyController
2. Create AuthorizationController
3. Add request validation

### Phase 4: User Group Support
1. Implement UserGroup aggregate
2. Implement UserGroupRepository
3. Add group management endpoints
4. Integrate groups into authorization

---

## Security Considerations

1. **Deny Precedence**: DENY policies always take precedence over ALLOW policies
2. **Least Privilege**: Default to deny if no matching ALLOW policy
3. **Audit Logging**: Log all policy evaluations and decisions
4. **Policy Validation**: Validate action patterns to prevent overly permissive wildcards
5. **Role Separation**: Separate policy management from regular user actions

---

## Design Decisions

1. **Profile Ownership**: Policies are scoped to profiles, not global, allowing multi-tenant isolation
2. **Explicit Deny**: Support DENY effect for fine-grained access control
3. **Wildcard Support**: Balance flexibility with security through pattern validation
4. **Predefined Roles**: Map to action patterns rather than embedding permissions in roles
5. **Group Support**: Enable group-based policies for easier management at scale

---

## References

- User aggregate with Role enum: `domain/users/src/main/java/.../aggregate/User.java`
- Approval Policy Design: `docs/approval-policy-design.md`
