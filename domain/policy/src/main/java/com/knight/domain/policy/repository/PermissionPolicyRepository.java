package com.knight.domain.policy.repository;

import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for PermissionPolicy aggregate persistence.
 */
public interface PermissionPolicyRepository {

    /**
     * Save a permission policy.
     */
    void save(PermissionPolicy policy);

    /**
     * Find a permission policy by ID.
     */
    Optional<PermissionPolicy> findById(String policyId);

    /**
     * Find all permission policies for a profile.
     */
    List<PermissionPolicy> findByProfileId(ProfileId profileId);

    /**
     * Find permission policies for a specific subject within a profile.
     */
    List<PermissionPolicy> findByProfileIdAndSubject(ProfileId profileId, Subject subject);

    /**
     * Find permission policies matching any of the given subjects within a profile.
     */
    List<PermissionPolicy> findByProfileIdAndSubjects(ProfileId profileId, List<Subject> subjects);

    /**
     * Delete a permission policy by ID.
     */
    void deleteById(String policyId);

    /**
     * Check if a permission policy exists.
     */
    boolean existsById(String policyId);
}
