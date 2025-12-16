package com.knight.application.persistence.profiles.repository;

import com.knight.application.persistence.profiles.entity.ProfileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data JPA repository for ProfileEntity.
 */
@Repository
public interface ProfileJpaRepository extends JpaRepository<ProfileEntity, String> {

    /**
     * Find all profiles where the given client is the primary client.
     */
    @Query("SELECT p FROM ProfileEntity p JOIN p.clientEnrollments ce WHERE ce.clientId = :clientId AND ce.isPrimary = true")
    List<ProfileEntity> findByPrimaryClientId(@Param("clientId") String clientId);

    /**
     * Find all profiles where the given client is a secondary client (not primary).
     */
    @Query("SELECT p FROM ProfileEntity p JOIN p.clientEnrollments ce WHERE ce.clientId = :clientId AND ce.isPrimary = false")
    List<ProfileEntity> findBySecondaryClientId(@Param("clientId") String clientId);

    /**
     * Find all profiles where the given client is enrolled (primary or secondary).
     */
    @Query("SELECT DISTINCT p FROM ProfileEntity p JOIN p.clientEnrollments ce WHERE ce.clientId = :clientId")
    List<ProfileEntity> findByClientId(@Param("clientId") String clientId);

    /**
     * Check if client is already primary in a SERVICING profile.
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM ProfileEntity p " +
           "JOIN p.clientEnrollments ce " +
           "WHERE ce.clientId = :clientId AND ce.isPrimary = true AND p.profileType = 'SERVICING'")
    boolean existsServicingProfileWithPrimaryClient(@Param("clientId") String clientId);

    // ==================== Paginated Search Methods ====================

    /**
     * Search profiles where the given client is primary, filtered by profile types.
     */
    @Query("SELECT p FROM ProfileEntity p JOIN p.clientEnrollments ce " +
           "WHERE ce.clientId = :clientId AND ce.isPrimary = true " +
           "AND (:profileTypes IS NULL OR p.profileType IN :profileTypes)")
    Page<ProfileEntity> searchByPrimaryClient(
        @Param("clientId") String clientId,
        @Param("profileTypes") Collection<String> profileTypes,
        Pageable pageable
    );

    /**
     * Search profiles where the given client is enrolled (primary or secondary), filtered by profile types.
     */
    @Query("SELECT DISTINCT p FROM ProfileEntity p JOIN p.clientEnrollments ce " +
           "WHERE ce.clientId = :clientId " +
           "AND (:profileTypes IS NULL OR p.profileType IN :profileTypes)")
    Page<ProfileEntity> searchByClient(
        @Param("clientId") String clientId,
        @Param("profileTypes") Collection<String> profileTypes,
        Pageable pageable
    );

    /**
     * Search profiles by client name (primary client only), filtered by profile types.
     * Joins with clients table to search by name.
     */
    @Query("SELECT p FROM ProfileEntity p JOIN p.clientEnrollments ce " +
           "JOIN com.knight.application.persistence.clients.entity.ClientEntity c ON c.clientId = ce.clientId " +
           "WHERE ce.isPrimary = true AND LOWER(c.name) LIKE LOWER(CONCAT('%', :clientName, '%')) " +
           "AND (:profileTypes IS NULL OR p.profileType IN :profileTypes)")
    Page<ProfileEntity> searchByPrimaryClientName(
        @Param("clientName") String clientName,
        @Param("profileTypes") Collection<String> profileTypes,
        Pageable pageable
    );

    /**
     * Search profiles by client name (any enrolled client), filtered by profile types.
     * Joins with clients table to search by name.
     */
    @Query("SELECT DISTINCT p FROM ProfileEntity p JOIN p.clientEnrollments ce " +
           "JOIN com.knight.application.persistence.clients.entity.ClientEntity c ON c.clientId = ce.clientId " +
           "WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :clientName, '%')) " +
           "AND (:profileTypes IS NULL OR p.profileType IN :profileTypes)")
    Page<ProfileEntity> searchByClientName(
        @Param("clientName") String clientName,
        @Param("profileTypes") Collection<String> profileTypes,
        Pageable pageable
    );

    /**
     * Count profiles where the given client is primary, filtered by profile types.
     */
    @Query("SELECT COUNT(p) FROM ProfileEntity p JOIN p.clientEnrollments ce " +
           "WHERE ce.clientId = :clientId AND ce.isPrimary = true " +
           "AND (:profileTypes IS NULL OR p.profileType IN :profileTypes)")
    long countByPrimaryClient(
        @Param("clientId") String clientId,
        @Param("profileTypes") Collection<String> profileTypes
    );

    /**
     * Count profiles where the given client is enrolled (primary or secondary), filtered by profile types.
     */
    @Query("SELECT COUNT(DISTINCT p) FROM ProfileEntity p JOIN p.clientEnrollments ce " +
           "WHERE ce.clientId = :clientId " +
           "AND (:profileTypes IS NULL OR p.profileType IN :profileTypes)")
    long countByClient(
        @Param("clientId") String clientId,
        @Param("profileTypes") Collection<String> profileTypes
    );
}
