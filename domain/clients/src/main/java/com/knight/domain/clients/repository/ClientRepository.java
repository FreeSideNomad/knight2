package com.knight.domain.clients.repository;

import com.knight.domain.clients.aggregate.Client;
import com.knight.domain.clients.api.PageResult;
import com.knight.platform.sharedkernel.ClientId;

import java.util.Optional;

/**
 * Repository interface for Client aggregate persistence.
 * Implementations handle the actual storage mechanism (JPA, in-memory, etc.)
 */
public interface ClientRepository {

    /**
     * Persists a Client aggregate.
     *
     * @param client the client to save
     */
    void save(Client client);

    /**
     * Retrieves a Client by its identifier.
     *
     * @param id the client identifier (can be SrfClientId or CdrClientId)
     * @return the client if found
     */
    Optional<Client> findById(ClientId id);

    /**
     * Searches for clients by name pattern with pagination.
     *
     * @param nameQuery the name search query
     * @param page the page number (0-based)
     * @param size the page size
     * @return paginated list of matching clients
     */
    PageResult<Client> searchByName(String nameQuery, int page, int size);

    /**
     * Checks if a client exists with the given identifier.
     *
     * @param id the client identifier
     * @return true if the client exists, false otherwise
     */
    boolean existsById(ClientId id);

    /**
     * Searches for clients by client ID with pagination.
     *
     * @param clientIdQuery the client ID search query
     * @param page the page number (0-based)
     * @param size the page size
     * @return paginated list of matching clients
     */
    PageResult<Client> searchByClientId(String clientIdQuery, int page, int size);

    /**
     * Searches for clients by client ID prefix and name pattern with pagination.
     * Used for type-filtered name searches (e.g., type=srf, name=Corp).
     *
     * @param clientIdPrefix the client ID prefix (e.g., "srf:")
     * @param nameQuery the name search query
     * @param page the page number (0-based)
     * @param size the page size
     * @return paginated list of matching clients
     */
    PageResult<Client> searchByClientIdPrefixAndName(String clientIdPrefix, String nameQuery, int page, int size);
}
