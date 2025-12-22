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
     * Finds all indirect clients for a given profile.
     *
     * @param profileId the profile identifier
     * @return list of indirect clients
     */
    List<IndirectClient> findByProfileId(ProfileId profileId);

    /**
     * Gets the next available sequence number for creating a new indirect client
     * under the specified parent client.
     *
     * @param parentClientId the parent client identifier
     * @return the next sequence number
     */
    int getNextSequenceForClient(ClientId parentClientId);

    /**
     * Checks if an indirect client with the given business name already exists
     * under the specified profile.
     *
     * @param profileId the profile identifier
     * @param businessName the business name to check
     * @return true if a client with this name exists
     */
    boolean existsByProfileIdAndBusinessName(ProfileId profileId, String businessName);
}
