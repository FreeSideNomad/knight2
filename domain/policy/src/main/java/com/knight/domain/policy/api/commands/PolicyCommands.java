package com.knight.domain.policy.api.commands;

/**
 * Command interface for Policy Management.
 * Defines contract for creating and managing permission and approval policies.
 */
public interface PolicyCommands {

    String createPolicy(CreatePolicyCmd cmd);

    record CreatePolicyCmd(
        String policyType,
        String subject,
        String action,
        String resource,
        Integer approverCount
    ) {}

    void updatePolicy(UpdatePolicyCmd cmd);

    record UpdatePolicyCmd(
        String policyId,
        String action,
        String resource,
        Integer approverCount
    ) {}

    void deletePolicy(DeletePolicyCmd cmd);

    record DeletePolicyCmd(String policyId) {}
}
