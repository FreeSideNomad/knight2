package com.knight.application.persistence.clients.repository;

import com.knight.application.persistence.clients.entity.ClientAccountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for ClientAccountEntity.
 * Provides CRUD operations and custom query methods.
 */
@Repository
public interface ClientAccountJpaRepository extends JpaRepository<ClientAccountEntity, String> {

    /**
     * Finds all accounts belonging to a specific client.
     *
     * @param clientId the client ID as URN string
     * @return list of account entities for the given client
     */
    List<ClientAccountEntity> findByClientId(String clientId);

    /**
     * Finds accounts belonging to a specific client with pagination.
     *
     * @param clientId the client ID as URN string
     * @param pageable pagination info
     * @return page of account entities for the given client
     */
    Page<ClientAccountEntity> findByClientId(String clientId, Pageable pageable);

    /**
     * Counts accounts belonging to a specific client.
     *
     * @param clientId the client ID as URN string
     * @return total count of accounts
     */
    long countByClientId(String clientId);

    /**
     * Finds all accounts belonging to a specific indirect client.
     *
     * @param indirectClientId the indirect client ID as UUID
     * @return list of account entities for the given indirect client
     */
    List<ClientAccountEntity> findByIndirectClientId(UUID indirectClientId);
}
