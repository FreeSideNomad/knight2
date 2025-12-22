package com.knight.application.persistence.batch.repository;

import com.knight.application.persistence.batch.entity.BatchEntity;
import com.knight.application.persistence.batch.mapper.BatchMapper;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.repository.BatchRepository;
import com.knight.domain.batch.types.BatchStatus;
import com.knight.platform.sharedkernel.BatchId;
import com.knight.platform.sharedkernel.ProfileId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA implementation of BatchRepository.
 */
@Repository
public class BatchRepositoryAdapter implements BatchRepository {

    private final BatchJpaRepository jpaRepository;
    private final BatchMapper mapper;

    public BatchRepositoryAdapter(BatchJpaRepository jpaRepository, BatchMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Batch save(Batch batch) {
        BatchEntity entity = mapper.toEntity(batch);
        BatchEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Batch> findById(BatchId id) {
        return jpaRepository.findById(id.value())
                .map(mapper::toDomain);
    }

    @Override
    public List<Batch> findBySourceProfileId(ProfileId profileId) {
        return jpaRepository.findBySourceProfileIdOrderByCreatedAtDesc(profileId.urn())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Batch> findByStatus(BatchStatus status) {
        return jpaRepository.findByStatusOrderByCreatedAtDesc(status.name())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Batch> findBySourceProfileIdAndStatus(ProfileId profileId, BatchStatus status) {
        return jpaRepository.findBySourceProfileIdAndStatusOrderByCreatedAtDesc(profileId.urn(), status.name())
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}
