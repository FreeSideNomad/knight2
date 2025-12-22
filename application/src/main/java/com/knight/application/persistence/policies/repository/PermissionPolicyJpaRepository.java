package com.knight.application.persistence.policies.repository;

import com.knight.application.persistence.policies.entity.PermissionPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PermissionPolicyJpaRepository extends JpaRepository<PermissionPolicyEntity, UUID> {

    List<PermissionPolicyEntity> findByProfileId(String profileId);

    List<PermissionPolicyEntity> findByProfileIdAndSubjectTypeAndSubjectIdentifier(
        String profileId,
        String subjectType,
        String subjectIdentifier
    );

    @Query("SELECT p FROM PermissionPolicyEntity p WHERE p.profileId = :profileId " +
           "AND CONCAT(p.subjectType, ':', p.subjectIdentifier) IN :subjectUrns")
    List<PermissionPolicyEntity> findByProfileIdAndSubjectUrns(
        @Param("profileId") String profileId,
        @Param("subjectUrns") List<String> subjectUrns
    );
}
