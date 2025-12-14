package com.knight.domain.indirectclients.repository;

import com.knight.domain.indirectclients.aggregate.IndirectClient;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.IndirectClientId;

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
     * Gets the next available sequence number for creating a new indirect client
     * under the specified parent client.
     *
     * @param parentClientId the parent client identifier
     * @return the next sequence number
     */
    int getNextSequenceForClient(ClientId parentClientId);
}
