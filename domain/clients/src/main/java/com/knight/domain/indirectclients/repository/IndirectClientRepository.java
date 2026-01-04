package com.knight.domain.indirectclients.repository;

import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;
import com.knight.platform.sharedkernel.ProfileId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for IndirectClient aggregate persistence.
 * Implementations handle the actual storage mechanism (JPA, in-memory, etc.)
 */
public interface IndirectClientRepository {

    /**
     * Persists an IndirectClient aggregate.
     *
     * @param client the indirect client to save
     */
    void save(IndirectClient client);

    /**
     * Retrieves an IndirectClient by its identifier.
     *
     * @param id the indirect client identifier
     * @return the indirect client if found
     */
    Optional<IndirectClient> findById(IndirectClientId id);

    /**
     * Finds all indirect clients for a given parent client.
     *
     * @param parentClientId the parent client identifier
     * @return list of indirect clients
     */
    List<IndirectClient> findByParentClientId(ClientId parentClientId);

    /**
     * Finds all indirect clients for a given parent profile.
     *
     * @param parentProfileId the parent profile identifier
     * @return list of indirect clients
     */
    List<IndirectClient> findByParentProfileId(ProfileId parentProfileId);

    /**
     * Checks if an indirect client with the given name already exists
     * under the specified parent profile.
     *
     * @param parentProfileId the parent profile identifier
     * @param name the name to check
     * @return true if a client with this name exists
     */
    boolean existsByParentProfileIdAndName(ProfileId parentProfileId, String name);

    /**
     * Finds an indirect client by its associated profile ID.
     * Used when an indirect user needs to access their own indirect client.
     * The profile ID here is the indirect client's own profile (not the parent).
     *
     * @param profileId the profile identifier of the indirect client's own profile
     * @return the indirect client if found
     */
    Optional<IndirectClient> findByProfileId(ProfileId profileId);

    /**
     * Checks if an indirect client with the given external reference exists
     * under the specified parent profile.
     *
     * @param parentProfileId the parent profile identifier
     * @param externalReference the external reference to check
     * @return true if a client with this external reference exists
     */
    boolean existsByExternalReference(ProfileId parentProfileId, String externalReference);

    /**
     * Finds an indirect client by its external reference within a profile.
     *
     * @param parentProfileId the parent profile identifier
     * @param externalReference the external reference
     * @return the indirect client if found
     */
    Optional<IndirectClient> findByExternalReference(ProfileId parentProfileId, String externalReference);
}
