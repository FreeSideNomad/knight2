package com.knight.application.persistence.clients.repository;

import com.knight.application.persistence.clients.entity.ClientEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for ClientEntity.
 * Provides CRUD operations and custom query methods.
 */
@Repository
public interface ClientJpaRepository extends JpaRepository<ClientEntity, String> {

    /**
     * Searches for clients by name pattern with pagination (case-insensitive).
     *
     * @param nameQuery the name search query
     * @param pageable pagination info
     * @return page of matching clients
     */
    Page<ClientEntity> findByNameContainingIgnoreCase(String nameQuery, Pageable pageable);

    /**
     * Searches for clients by client ID pattern with pagination (case-insensitive).
     *
     * @param clientIdQuery the client ID search query
     * @param pageable pagination info
     * @return page of matching clients
     */
    Page<ClientEntity> findByClientIdContainingIgnoreCase(String clientIdQuery, Pageable pageable);

    /**
     * Searches for clients by client ID prefix and name pattern with pagination.
     * Used for type-filtered name searches (e.g., type=srf, name=Corp).
     *
     * @param clientIdPrefix the client ID prefix (e.g., "srf:")
     * @param nameQuery the name search query
     * @param pageable pagination info
     * @return page of matching clients
     */
    Page<ClientEntity> findByClientIdStartingWithIgnoreCaseAndNameContainingIgnoreCase(
            String clientIdPrefix, String nameQuery, Pageable pageable);
}
