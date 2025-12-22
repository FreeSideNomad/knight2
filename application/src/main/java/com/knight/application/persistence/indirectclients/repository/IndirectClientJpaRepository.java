package com.knight.application.persistence.indirectclients.repository;

import com.knight.application.persistence.indirectclients.entity.IndirectClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IndirectClientJpaRepository extends JpaRepository<IndirectClientEntity, UUID> {
    List<IndirectClientEntity> findByParentClientId(String parentClientId);
    List<IndirectClientEntity> findByProfileId(String profileId);
    Optional<IndirectClientEntity> findByIndirectClientUrn(String indirectClientUrn);
    int countByParentClientId(String parentClientId);
    boolean existsByProfileIdAndBusinessName(String profileId, String businessName);
}
