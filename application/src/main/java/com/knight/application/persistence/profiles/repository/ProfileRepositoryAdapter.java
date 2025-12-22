package com.knight.application.persistence.profiles.repository;

import com.knight.application.persistence.profiles.entity.ProfileEntity;
import com.knight.application.persistence.profiles.mapper.ServicingProfileMapper;
import com.knight.domain.serviceprofiles.aggregate.Profile;
import com.knight.domain.serviceprofiles.repository.ServicingProfileRepository;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ProfileId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JPA-based implementation of ServicingProfileRepository.
 * Adapts the domain repository interface to Spring Data JPA.
 */
@Repository
@RequiredArgsConstructor
public class ProfileRepositoryAdapter implements ServicingProfileRepository {

    private final ProfileJpaRepository jpaRepository;
    private final ServicingProfileMapper mapper;

    @Override
    @Transactional
    public void save(Profile profile) {
        String profileUrn = profile.profileId().urn();

        // Check if entity exists - if so, update it instead of replacing
        Optional<ProfileEntity> existingOpt = jpaRepository.findById(profileUrn);

        if (existingOpt.isPresent()) {
            // Update existing entity to preserve JPA-managed collections
            ProfileEntity existing = existingOpt.get();
            mapper.updateEntity(existing, profile);
            jpaRepository.save(existing);
        } else {
            // New entity - create fresh
            ProfileEntity entity = mapper.toEntity(profile);
            jpaRepository.save(entity);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Profile> findById(ProfileId profileId) {
        String urn = profileId.urn();
        return jpaRepository.findById(urn)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Profile> findByPrimaryClient(ClientId clientId) {
        return jpaRepository.findByPrimaryClientId(clientId.urn()).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Profile> findBySecondaryClient(ClientId clientId) {
        return jpaRepository.findBySecondaryClientId(clientId.urn()).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Profile> findByClient(ClientId clientId) {
        return jpaRepository.findByClientId(clientId.urn()).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsServicingProfileWithPrimaryClient(ClientId clientId) {
        return jpaRepository.existsServicingProfileWithPrimaryClient(clientId.urn());
    }

    // ==================== Paginated Search Methods ====================

    @Override
    @Transactional(readOnly = true)
    public PageResult<Profile> searchByPrimaryClient(ClientId clientId, Set<String> profileTypes, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Collection<String> types = profileTypes != null && !profileTypes.isEmpty() ? profileTypes : null;
        Page<ProfileEntity> pageResult = jpaRepository.searchByPrimaryClient(clientId.urn(), types, pageable);
        return toPageResult(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Profile> searchByClient(ClientId clientId, Set<String> profileTypes, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Collection<String> types = profileTypes != null && !profileTypes.isEmpty() ? profileTypes : null;
        Page<ProfileEntity> pageResult = jpaRepository.searchByClient(clientId.urn(), types, pageable);
        return toPageResult(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Profile> searchByPrimaryClientName(String clientName, Set<String> profileTypes, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Collection<String> types = profileTypes != null && !profileTypes.isEmpty() ? profileTypes : null;
        Page<ProfileEntity> pageResult = jpaRepository.searchByPrimaryClientName(clientName, types, pageable);
        return toPageResult(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Profile> searchByClientName(String clientName, Set<String> profileTypes, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Collection<String> types = profileTypes != null && !profileTypes.isEmpty() ? profileTypes : null;
        Page<ProfileEntity> pageResult = jpaRepository.searchByClientName(clientName, types, pageable);
        return toPageResult(pageResult, page, size);
    }

    private PageResult<Profile> toPageResult(Page<ProfileEntity> pageResult, int page, int size) {
        List<Profile> profiles = pageResult.getContent().stream()
            .map(mapper::toDomain)
            .toList();
        return new PageResult<>(profiles, pageResult.getTotalElements(), page, size);
    }
}
