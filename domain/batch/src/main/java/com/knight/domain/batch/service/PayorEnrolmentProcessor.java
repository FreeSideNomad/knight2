package com.knight.domain.batch.service;

import com.knight.domain.batch.types.BatchItemResult;
import com.knight.domain.batch.types.PayorEnrolmentRequest;
import com.knight.platform.sharedkernel.ProfileId;

/**
 * Interface for processing individual payor enrolments.
 * Implementations coordinate the creation of indirect clients, profiles, and users.
 */
public interface PayorEnrolmentProcessor {

    /**
     * Process a single payor enrolment.
     *
     * @param sourceProfileId the parent servicing profile
     * @param request the payor data
     * @param createdBy the user performing the import
     * @return the result containing created entity IDs
     */
    BatchItemResult processPayor(ProfileId sourceProfileId, PayorEnrolmentRequest request, String createdBy);

    /**
     * Check if a business name already exists under the profile.
     */
    boolean existsByBusinessName(ProfileId profileId, String businessName);

    /**
     * Check if a user with the given email already exists in the identity provider.
     */
    boolean existsInIdentityProvider(String email);

    /**
     * Check if a user with the given email already exists locally.
     */
    boolean existsByEmail(String email);
}
