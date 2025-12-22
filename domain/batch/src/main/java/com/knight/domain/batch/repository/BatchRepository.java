package com.knight.domain.batch.repository;

import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.types.BatchStatus;
import com.knight.platform.sharedkernel.BatchId;
import com.knight.platform.sharedkernel.ProfileId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Batch aggregate.
 */
public interface BatchRepository {

    /**
     * Save a batch (create or update).
     */
    Batch save(Batch batch);

    /**
     * Find a batch by ID.
     */
    Optional<Batch> findById(BatchId id);

    /**
     * Find batches by source profile.
     */
    List<Batch> findBySourceProfileId(ProfileId profileId);

    /**
     * Find batches by status.
     */
    List<Batch> findByStatus(BatchStatus status);

    /**
     * Find batches by source profile and status.
     */
    List<Batch> findBySourceProfileIdAndStatus(ProfileId profileId, BatchStatus status);
}
