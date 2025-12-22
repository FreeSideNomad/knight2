# Approval Policy Design Document

## Overview

This document defines the design for managing Approval Policies within the Knight Platform. Approval policies control which actions require approval and who can approve them.

### Requirements Summary

1. **Profile Ownership**: Approval policies are owned by a Profile (ProfileId)
2. **Hierarchical Actions**: Actions follow a URN-like hierarchy (service-group.service.resource-type.action)
3. **Approver Subjects**: Define who can approve using users, groups, or roles
4. **Required Approvers**: Configure number of approvals required
5. **Resource Scoping**: Policies can be scoped to specific resources or wildcards
6. **Self-Approval Control**: Configure whether initiators can approve their own actions

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Knight Platform                                     │
│                                                                                  │
│  ┌─────────────────┐    ┌─────────────────────────────────────────────────────┐ │
│  │   Profile       │    │              Approval Policy Engine                 │ │
│  │   (Owner)       │───▶│                                                     │ │
│  └─────────────────┘    │  ┌───────────────────────────────────────────────┐  │ │
│                         │  │            ApprovalPolicy                      │  │ │
│                         │  │                                                │  │ │
│                         │  │  - Action (hierarchical URN pattern)           │  │ │
│                         │  │  - Resource (resource pattern)                 │  │ │
│                         │  │  - RequiredApprovers (count)                   │  │ │
│                         │  │  - ApproverSubjects (user/group/role set)      │  │ │
│                         │  │  - SelfApprovalAllowed (boolean)               │  │ │
│                         │  └───────────────────────────────────────────────┘  │ │
│                         └─────────────────────────────────────────────────────┘ │
│                                        │                                        │
│                                        ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────────────┐│
│  │                         Approval Workflow Service                           ││
│  │  ┌───────────────────────┐  ┌───────────────────────┐  ┌─────────────────┐  ││
│  │  │ Get Approval Reqs     │  │ Who Can Approve?      │  │ Execute on      │  ││
│  │  │ for Action            │  │ (Resolve Subjects)    │  │ Approval        │  ││
│  │  └───────────────────────┘  └───────────────────────┘  └─────────────────┘  ││
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

#### Action Examples for Approval Policies

```java
// Common actions requiring approval
payments.ach-payments.single-payment.create    // ACH payments over threshold
payments.wire-payments.wire-payment.create     // All wire payments
payments.wire-payments.wire-template.create    // Wire template creation
security.users.user.create                     // User creation
security.users.permission.create               // Permission grant

// Wildcard patterns for approval
payments.*                           // All payment actions
payments.wire-payments.*             // All wire payment actions
*.create                             // All create actions (high-value)
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
     */
    public boolean matches(Action action) {
        if ("*".equals(this.value)) {
            return true;
        }

        String[] patternParts = this.value.split("\\.");

        // Handle suffix wildcards like *.create
        if (patternParts.length == 2 && "*".equals(patternParts[0])) {
            return action.value.endsWith("." + patternParts[1]);
        }

        // Handle prefix wildcards like payments.*
        if (patternParts.length >= 1 && "*".equals(patternParts[patternParts.length - 1])) {
            String prefix = this.value.substring(0, this.value.length() - 2);
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

Subject represents who can approve. It can be a user, group, or role.

```
user:{userId}       - Specific user by UUID
group:{groupId}     - Group of users by UUID
role:{roleName}     - Predefined role (typically 'approver')
```

#### Subject Value Object

```java
package com.knight.domain.policy.types;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Subject value object representing who can approve.
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

Resource represents the scope of resources the approval policy applies to.

```
CAN_DDA:DDA:00000:081154333874                    // Specific account
CAN_DDA:DDA:*                                      // All DDA accounts in CAN_DDA
*                                                  // All resources (default)
```

#### Resource Value Object

```java
package com.knight.domain.policy.types;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resource value object representing the scope of resources.
 * Supports wildcards and comma-separated lists.
 */
public record Resource(String value) {

    public Resource {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Resource cannot be null or blank");
        }
    }

    public boolean matches(String resourceId) {
        if ("*".equals(this.value)) {
            return true;
        }

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

        String regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*");

        return Pattern.matches("^" + regex + "$", resourceId);
    }

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
}
```

---

## Domain Model

### ApprovalPolicy Aggregate

Approval policies control which actions require approval and who can approve them.

```java
package com.knight.domain.policy.aggregate;

import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;

import java.time.Instant;
import java.util.*;

/**
 * ApprovalPolicy aggregate root.
 * Controls which actions require approval and who can approve them.
 */
public class ApprovalPolicy {

    private final String id;
    private final ProfileId profileId;
    private final Action action;
    private final Resource resource;
    private int requiredApprovers;
    private final Set<Subject> approverSubjects;
    private final String description;
    private boolean selfApprovalAllowed;
    private boolean enabled;
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private ApprovalPolicy(String id, ProfileId profileId, Action action,
                           Resource resource, int requiredApprovers,
                           Set<Subject> approverSubjects, String description,
                           boolean selfApprovalAllowed, String createdBy) {
        this.id = Objects.requireNonNull(id);
        this.profileId = Objects.requireNonNull(profileId);
        this.action = Objects.requireNonNull(action);
        this.resource = resource != null ? resource : Resource.all();
        this.requiredApprovers = requiredApprovers;
        this.approverSubjects = new HashSet<>(approverSubjects);
        this.description = description;
        this.selfApprovalAllowed = selfApprovalAllowed;
        this.enabled = true;
        this.createdBy = Objects.requireNonNull(createdBy);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;

        validate();
    }

    private void validate() {
        if (requiredApprovers <= 0) {
            throw new IllegalArgumentException("Required approvers must be > 0");
        }
        if (approverSubjects.isEmpty()) {
            throw new IllegalArgumentException("At least one approver subject is required");
        }
    }

    public static ApprovalPolicy create(
            ProfileId profileId,
            Action action,
            Resource resource,
            int requiredApprovers,
            Set<Subject> approverSubjects,
            String description,
            boolean selfApprovalAllowed,
            String createdBy) {
        String id = UUID.randomUUID().toString();
        return new ApprovalPolicy(id, profileId, action, resource, requiredApprovers,
                approverSubjects, description, selfApprovalAllowed, createdBy);
    }

    /**
     * Check if this policy applies to the given action and resource.
     */
    public boolean appliesTo(Action requestedAction, String resourceId) {
        if (!enabled) {
            return false;
        }
        return this.action.matches(requestedAction) && this.resource.matches(resourceId);
    }

    /**
     * Check if the given subject can approve under this policy.
     */
    public boolean canApprove(Subject subject) {
        return approverSubjects.contains(subject);
    }

    /**
     * Check if the initiator can approve their own request.
     */
    public boolean canInitiatorApprove() {
        return selfApprovalAllowed;
    }

    /**
     * Update the required approvers count.
     */
    public void updateRequiredApprovers(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Required approvers must be > 0");
        }
        this.requiredApprovers = count;
        this.updatedAt = Instant.now();
    }

    /**
     * Add an approver subject.
     */
    public void addApprover(Subject subject) {
        this.approverSubjects.add(subject);
        this.updatedAt = Instant.now();
    }

    /**
     * Remove an approver subject.
     */
    public void removeApprover(Subject subject) {
        if (this.approverSubjects.size() <= 1) {
            throw new IllegalStateException("Cannot remove last approver");
        }
        this.approverSubjects.remove(subject);
        this.updatedAt = Instant.now();
    }

    /**
     * Enable or disable self-approval.
     */
    public void setSelfApprovalAllowed(boolean allowed) {
        this.selfApprovalAllowed = allowed;
        this.updatedAt = Instant.now();
    }

    /**
     * Enable the policy.
     */
    public void enable() {
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Disable the policy.
     */
    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    // Getters
    public String id() { return id; }
    public ProfileId profileId() { return profileId; }
    public Action action() { return action; }
    public Resource resource() { return resource; }
    public int requiredApprovers() { return requiredApprovers; }
    public Set<Subject> approverSubjects() { return Collections.unmodifiableSet(approverSubjects); }
    public String description() { return description; }
    public boolean selfApprovalAllowed() { return selfApprovalAllowed; }
    public boolean enabled() { return enabled; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
}
```

---

## Approval Workflow Integration

The approval policy works with the existing ApprovalWorkflow aggregate.

### ApprovalRequirement

```java
package com.knight.domain.policy.service;

import com.knight.domain.policy.types.Subject;

import java.util.Set;

/**
 * Represents the approval requirements for an action.
 */
public record ApprovalRequirement(
    String approvalPolicyId,
    int requiredApprovers,
    Set<Subject> approverSubjects,
    boolean selfApprovalAllowed
) {
    /**
     * Check if the given user can approve.
     */
    public boolean canUserApprove(String userId, Set<String> userRoles, Set<String> userGroups, boolean isInitiator) {
        if (isInitiator && !selfApprovalAllowed) {
            return false;
        }

        // Check if user is directly an approver
        if (approverSubjects.contains(Subject.user(userId))) {
            return true;
        }

        // Check if any of user's roles are approvers
        for (String role : userRoles) {
            if (approverSubjects.contains(Subject.role(role))) {
                return true;
            }
        }

        // Check if any of user's groups are approvers
        for (String groupId : userGroups) {
            if (approverSubjects.contains(Subject.group(groupId))) {
                return true;
            }
        }

        return false;
    }
}
```

### Approval Policy Service

```java
package com.knight.domain.policy.service;

import com.knight.domain.policy.aggregate.ApprovalPolicy;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Approval policy service for determining approval requirements.
 */
public interface ApprovalPolicyService {

    /**
     * Get the approval requirement for an action (if any).
     *
     * @param profileId the profile context
     * @param action the action being performed
     * @param resourceId the resource being acted upon
     * @return approval requirement if approval is needed, empty otherwise
     */
    Optional<ApprovalRequirement> getApprovalRequirement(ProfileId profileId, Action action, String resourceId);

    /**
     * Check if a user can approve a pending action.
     *
     * @param profileId the profile context
     * @param userId the potential approver
     * @param action the action pending approval
     * @param resourceId the resource
     * @param initiatorUserId the user who initiated the action
     * @return true if user can approve
     */
    boolean canApprove(ProfileId profileId, UserId userId, Action action, String resourceId, UserId initiatorUserId);

    /**
     * Get all users who can approve an action.
     *
     * @param profileId the profile context
     * @param action the action
     * @param resourceId the resource
     * @return set of user IDs who can approve
     */
    Set<UserId> getEligibleApprovers(ProfileId profileId, Action action, String resourceId);

    /**
     * Get all approval policies for a profile.
     */
    List<ApprovalPolicy> listPolicies(ProfileId profileId);

    /**
     * Check if an action requires approval.
     */
    boolean requiresApproval(ProfileId profileId, Action action, String resourceId);
}
```

---

## API Design

### REST Endpoints

```
POST   /api/profiles/{profileId}/approval-policies          # Create approval policy
GET    /api/profiles/{profileId}/approval-policies          # List approval policies
GET    /api/profiles/{profileId}/approval-policies/{id}     # Get approval policy
PUT    /api/profiles/{profileId}/approval-policies/{id}     # Update approval policy
DELETE /api/profiles/{profileId}/approval-policies/{id}     # Delete approval policy

POST   /api/profiles/{profileId}/approval-policies/{id}/approvers        # Add approver
DELETE /api/profiles/{profileId}/approval-policies/{id}/approvers/{urn}  # Remove approver

PUT    /api/profiles/{profileId}/approval-policies/{id}/enable   # Enable policy
PUT    /api/profiles/{profileId}/approval-policies/{id}/disable  # Disable policy

GET    /api/profiles/{profileId}/approval-requirements            # Get requirements for action
GET    /api/profiles/{profileId}/approvers                        # Get approvers for action
```

### DTOs

```java
// Create Approval Policy
record CreateApprovalPolicyRequest(
    String action,                   // action pattern (e.g., "payments.wire-payments.*")
    String resource,                 // resource pattern (optional, defaults to "*")
    int requiredApprovers,           // number of approvals needed
    Set<String> approverSubjects,    // Set of subject URNs (user:uuid, group:uuid, role:name)
    String description,
    boolean selfApprovalAllowed
) {}

// Update Approval Policy
record UpdateApprovalPolicyRequest(
    Integer requiredApprovers,
    Boolean selfApprovalAllowed,
    String description
) {}

// Approval Policy Response
record ApprovalPolicyDto(
    String id,
    String profileId,
    String action,
    String resource,
    int requiredApprovers,
    Set<String> approverSubjects,
    String description,
    boolean selfApprovalAllowed,
    boolean enabled,
    Instant createdAt,
    String createdBy
) {}

// Add Approver Request
record AddApproverRequest(
    String subject    // subject URN (user:uuid, group:uuid, role:name)
) {}

// Check Approval Requirements
record CheckApprovalRequest(
    String action,
    String resourceId
) {}

record ApprovalRequirementResponse(
    boolean requiresApproval,
    String approvalPolicyId,
    int requiredApprovers,
    Set<String> approverSubjects,
    boolean selfApprovalAllowed,
    Set<String> eligibleApproverUserIds  // Resolved user IDs who can approve
) {}
```

---

## Database Schema

```sql
-- Approval Policies
CREATE TABLE approval_policies (
    id UNIQUEIDENTIFIER PRIMARY KEY,
    profile_id VARCHAR(200) NOT NULL,
    action_pattern VARCHAR(500) NOT NULL,       -- Action URN pattern
    resource_pattern VARCHAR(1000) NOT NULL DEFAULT '*',  -- Resource pattern
    required_approvers INT NOT NULL,
    self_approval_allowed BIT NOT NULL DEFAULT 0,
    enabled BIT NOT NULL DEFAULT 1,
    description NVARCHAR(500),
    created_at DATETIME2 NOT NULL,
    created_by NVARCHAR(255) NOT NULL,
    updated_at DATETIME2 NOT NULL,

    CONSTRAINT FK_approval_policies_profile FOREIGN KEY (profile_id)
        REFERENCES profiles(profile_id),
    CONSTRAINT CHK_approval_required_approvers CHECK (required_approvers > 0)
);

CREATE INDEX idx_approval_policies_profile ON approval_policies(profile_id);
CREATE INDEX idx_approval_policies_action ON approval_policies(action_pattern);
CREATE INDEX idx_approval_policies_enabled ON approval_policies(profile_id, enabled);

-- Approval Policy Approvers (many-to-many)
CREATE TABLE approval_policy_approvers (
    approval_policy_id UNIQUEIDENTIFIER NOT NULL,
    subject_type VARCHAR(20) NOT NULL,          -- USER, GROUP, ROLE
    subject_identifier VARCHAR(255) NOT NULL,   -- UUID or role name
    added_at DATETIME2 NOT NULL,
    added_by NVARCHAR(255) NOT NULL,

    PRIMARY KEY (approval_policy_id, subject_type, subject_identifier),
    CONSTRAINT FK_approval_policy_approvers_policy FOREIGN KEY (approval_policy_id)
        REFERENCES approval_policies(id) ON DELETE CASCADE,
    CONSTRAINT CHK_approver_subject_type CHECK (subject_type IN ('USER', 'GROUP', 'ROLE'))
);

CREATE INDEX idx_approval_policy_approvers_policy ON approval_policy_approvers(approval_policy_id);
CREATE INDEX idx_approval_policy_approvers_subject ON approval_policy_approvers(subject_type, subject_identifier);
```

---

## Approval Flow

```
┌──────────────────────┐
│  User Initiates      │
│  Action              │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────────────┐
│  Check Approval Requirement  │
│  - Find matching policy      │
│  - Get required approvers    │
└──────────┬───────────────────┘
           │
           ▼
    ┌──────────────┐
    │  Approval    │  No
    │  Required?   │────────────────────┐
    └──────┬───────┘                    │
           │ Yes                         │
           ▼                             ▼
┌──────────────────────────────┐  ┌───────────────┐
│  Create ApprovalWorkflow     │  │ Execute       │
│  - Link to action/resource   │  │ Immediately   │
│  - Set required approvers    │  └───────────────┘
│  - Record initiator          │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────┐
│  Notify Eligible Approvers   │
│  - Resolve user/group/role   │
│  - Send notifications        │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────┐
│  Approver Reviews Request    │
│  - Validate can approve      │
│  - Check self-approval rules │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────┐
│  Record Decision             │
│  - APPROVE or REJECT         │
│  - Optional comments         │
└──────────┬───────────────────┘
           │
           ▼
┌────────────────────────────────────────┐
│  Check Completion                       │
│  - If any rejection: REJECTED          │
│  - If approvals >= required: APPROVED  │
│  - Otherwise: still PENDING            │
└──────────┬─────────────────────────────┘
           │
           ▼
    ┌──────────────┐
    │  Status?     │
    └──────┬───────┘
           │
    ┌──────┼──────┐
    │      │      │
    ▼      ▼      ▼
┌───────┐ ┌────────┐ ┌──────────┐
│APPROVED│ │REJECTED│ │ PENDING  │
│Execute │ │ Notify │ │ Wait for │
│ Action │ │Initiator│ │ more    │
└───────┘ └────────┘ └──────────┘
```

---

## Integration with ApprovalWorkflow Aggregate

The existing `ApprovalWorkflow` aggregate handles the execution of approval workflows:

```java
// When action requires approval:
ApprovalRequirement req = approvalPolicyService.getApprovalRequirement(profileId, action, resourceId);

if (req.isPresent()) {
    // Create workflow
    ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
        resourceType,
        resourceId,
        req.get().requiredApprovers(),
        initiatorUserId
    );

    // Store pending action details (in separate table)
    pendingActionRepository.save(new PendingAction(
        workflow.id(),
        action,
        resourceId,
        actionPayload,
        initiatorUserId
    ));

    workflowRepository.save(workflow);
}

// When approver approves/rejects:
ApprovalWorkflow workflow = workflowRepository.findById(workflowId);

// Validate approver can approve
boolean canApprove = approvalPolicyService.canApprove(
    profileId, approverId, action, resourceId, workflow.initiatedBy()
);

if (canApprove) {
    workflow.recordApproval(approverId, decision, comments);
    workflowRepository.save(workflow);

    if (workflow.status() == ApprovalWorkflow.Status.APPROVED) {
        // Execute the pending action
        PendingAction pending = pendingActionRepository.findByWorkflowId(workflow.id());
        actionExecutor.execute(pending);
    }
}
```

---

## Implementation Phases

### Phase 1: Domain Model
1. Create ApprovalPolicy aggregate
2. Implement ApprovalRequirement record
3. Add database migration for approval_policies tables

### Phase 2: Service Layer
1. Implement ApprovalPolicyRepository
2. Implement ApprovalPolicyService
3. Integrate with existing ApprovalWorkflow

### Phase 3: REST API
1. Create ApprovalPolicyController
2. Create ApprovalRequirementController
3. Add request validation

### Phase 4: Notifications
1. Implement approver notification service
2. Add email/in-app notifications
3. Track notification delivery

---

## Security Considerations

1. **Self-Approval Prevention**: Default to preventing self-approval, allow opt-in per policy
2. **Audit Logging**: Log all approval decisions with timestamps and approvers
3. **Workflow Integrity**: Prevent modification of pending workflows
4. **Approver Validation**: Always validate approver eligibility before recording decisions
5. **Expiration**: Consider adding workflow expiration for stale requests

---

## Design Decisions

1. **Profile Ownership**: Policies scoped to profiles for multi-tenant isolation
2. **Flexible Approvers**: Support users, groups, and roles for flexible approver assignment
3. **Self-Approval Control**: Per-policy configuration for self-approval
4. **Enable/Disable**: Policies can be temporarily disabled without deletion
5. **Required Count**: Configurable number of required approvals (multi-approval support)
6. **First Rejection Terminates**: Any rejection immediately terminates the workflow

---

## References

- Permission Policy Design: `docs/permission-policy-design.md`
- ApprovalWorkflow aggregate: `domain/approvals/src/main/java/.../aggregate/ApprovalWorkflow.java`
- User aggregate with Role enum: `domain/users/src/main/java/.../aggregate/User.java`
