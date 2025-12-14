package com.knight.domain.clients.repository;

import com.knight.domain.clients.aggregate.ClientAccount;
import com.knight.domain.clients.api.PageResult;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ClientAccount aggregate.
 * Defines the contract for persistence operations on ClientAccount entities.
 */
public interface ClientAccountRepository {

    /**
     * Finds a client account by its unique identifier.
     *
     * @param id the account identifier
     * @return an Optional containing the account if found, or empty if not found
     */
    Optional<ClientAccount> findById(ClientAccountId id);

    /**
     * Finds all accounts belonging to a specific client.
     *
     * @param clientId the client identifier
     * @return a list of accounts owned by the client (empty list if none found)
     */
    List<ClientAccount> findByClientId(ClientId clientId);

    /**
     * Finds accounts belonging to a specific client with pagination.
     *
     * @param clientId the client identifier
     * @param page the page number (0-based)
     * @param size the page size
     * @return paginated list of accounts owned by the client
     */
    PageResult<ClientAccount> findByClientId(ClientId clientId, int page, int size);

    /**
     * Counts the total number of accounts for a specific client.
     *
     * @param clientId the client identifier
     * @return the total number of accounts
     */
    long countByClientId(ClientId clientId);

    /**
     * Saves or updates a client account.
     *
     * @param account the account to save
     */
    void save(ClientAccount account);
}
