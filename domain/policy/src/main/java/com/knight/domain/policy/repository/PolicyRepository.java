package com.knight.domain.policy.repository;

import com.knight.domain.policy.aggregate.Policy;

import java.util.Optional;

/**
 * Repository interface for Policy aggregate persistence.
 * Implementations handle the actual storage mechanism (JPA, in-memory, etc.)
 */
public interface PolicyRepository {

    /**
     * Persists a Policy aggregate.
     *
     * @param policy the policy to save
     */
    void save(Policy policy);

    /**
     * Retrieves a Policy by its identifier.
     *
     * @param policyId the policy identifier
     * @return the policy if found
     */
    Optional<Policy> findById(String policyId);

    /**
     * Deletes a Policy by its identifier.
     *
     * @param policyId the policy identifier
     */
    void deleteById(String policyId);
}
