package com.knight.domain.policy.api.queries;

/**
 * Query interface for Policy Management.
 */
public interface PolicyQueries {

    record PolicySummary(
        String policyId,
        String policyType,
        String subject,
        String action,
        String resource,
        Integer approverCount
    ) {}

    PolicySummary getPolicySummary(String policyId);
}
