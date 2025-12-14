package com.knight.domain.serviceprofiles.repository;

import com.knight.domain.serviceprofiles.aggregate.ServicingProfile;
import com.knight.platform.sharedkernel.ServicingProfileId;

import java.util.Optional;

/**
 * Repository interface for ServicingProfile aggregate persistence.
 * Implementations handle the actual storage mechanism (JPA, in-memory, etc.)
 */
public interface ServicingProfileRepository {

    /**
     * Persists a ServicingProfile aggregate.
     *
     * @param profile the servicing profile to save
     */
    void save(ServicingProfile profile);

    /**
     * Retrieves a ServicingProfile by its identifier.
     *
     * @param profileId the servicing profile identifier
     * @return the servicing profile if found
     */
    Optional<ServicingProfile> findById(ServicingProfileId profileId);
}
