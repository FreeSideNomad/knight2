package com.knight.application.persistence.profiles.repository;

import com.knight.application.persistence.profiles.entity.ServicingProfileEntity;
import com.knight.application.persistence.profiles.mapper.ServicingProfileMapper;
import com.knight.domain.serviceprofiles.aggregate.ServicingProfile;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.platform.sharedkernel.ServicingProfileId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * JPA-based implementation of ServicingProfileRepository.
 * Adapts the domain repository interface to Spring Data JPA.
 */
@Repository
@RequiredArgsConstructor
public class ServicingProfileRepositoryAdapter implements ServicingProfileRepository {

    private final ServicingProfileJpaRepository jpaRepository;
    private final ServicingProfileMapper mapper;

    @Override
    @Transactional
    public void save(ServicingProfile profile) {
        ServicingProfileEntity entity = mapper.toEntity(profile);
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServicingProfile> findById(ServicingProfileId profileId) {
        String urn = profileId.urn();
        return jpaRepository.findById(urn)
            .map(mapper::toDomain);
    }
}
