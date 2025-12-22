package com.knight.domain.policy.api.commands;

import com.knight.domain.policy.api.types.PolicyDto;
import com.knight.platform.sharedkernel.ProfileId;

/**
 * Command interface for PermissionPolicy operations.
 */
public interface PermissionPolicyCommands {

    /**
     * Create a new permission policy.
     */
    PolicyDto createPolicy(CreatePolicyCmd cmd);

    /**
     * Update an existing permission policy.
     */
    PolicyDto updatePolicy(UpdatePolicyCmd cmd);

    /**
     * Delete a permission policy.
     */
    void deletePolicy(DeletePolicyCmd cmd);

    // Command records

    record CreatePolicyCmd(
        ProfileId profileId,
        String subjectUrn,      // user:uuid, group:uuid, or role:NAME
        String actionPattern,   // e.g., "payments.ach-payments.*"
        String resourcePattern, // e.g., "*" or "CAN_DDA:DDA:*"
        String effect,          // ALLOW or DENY
        String description,
        String createdBy
    ) {}

    record UpdatePolicyCmd(
        String policyId,
        String actionPattern,
        String resourcePattern,
        String effect,
        String description
    ) {}

    record DeletePolicyCmd(
        String policyId
    ) {}
}
