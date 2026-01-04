package com.knight.application.persistence.indirectclients.repository;

import com.knight.application.persistence.indirectclients.entity.IndirectClientEntity;
import com.knight.application.persistence.profiles.entity.ClientEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndirectClientJpaRepository extends JpaRepository<IndirectClientEntity, String> {
    List<IndirectClientEntity> findByParentClientId(String parentClientId);
    List<IndirectClientEntity> findByParentProfileId(String parentProfileId);
    boolean existsByParentProfileIdAndName(String parentProfileId, String name);

    /**
     * Find indirect client by its own profile ID.
     * Used when an indirect user needs to access their own indirect client.
     * The indirect client's profile is linked through client enrollments where the
     * indirect client is the primary client.
     */
    @Query("SELECT ic FROM IndirectClientEntity ic WHERE EXISTS " +
           "(SELECT ce FROM ClientEnrollmentEntity ce WHERE ce.clientId = ic.clientId " +
           "AND ce.isPrimary = true AND ce.profile.profileId = :profileId)")
    Optional<IndirectClientEntity> findByOwnProfileId(@Param("profileId") String profileId);

    /**
     * Get distinct parent client IDs for all indirect clients.
     */
    @Query("SELECT DISTINCT ic.parentClientId FROM IndirectClientEntity ic ORDER BY ic.parentClientId")
    List<String> findDistinctParentClientIds();

    /**
     * Check if an indirect client with the given external reference exists within a profile.
     */
    boolean existsByParentProfileIdAndExternalReference(String parentProfileId, String externalReference);

    /**
     * Find an indirect client by external reference within a profile.
     */
    Optional<IndirectClientEntity> findByParentProfileIdAndExternalReference(String parentProfileId, String externalReference);
}
