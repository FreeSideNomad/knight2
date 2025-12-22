package com.knight.application.persistence.batch.repository;

import com.knight.application.persistence.batch.entity.BatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for BatchEntity.
 */
@Repository
public interface BatchJpaRepository extends JpaRepository<BatchEntity, UUID> {

    /**
     * Find batches by source profile ID.
     */
    List<BatchEntity> findBySourceProfileIdOrderByCreatedAtDesc(String sourceProfileId);

    /**
     * Find batches by status.
     */
    List<BatchEntity> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Find batches by source profile and status.
     */
    List<BatchEntity> findBySourceProfileIdAndStatusOrderByCreatedAtDesc(String sourceProfileId, String status);
}
