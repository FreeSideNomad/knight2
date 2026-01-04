package com.knight.application.persistence.accountgroups.repository;

import com.knight.application.persistence.accountgroups.entity.AccountGroupEntity;
import com.knight.application.persistence.accountgroups.mapper.AccountGroupMapper;
import com.knight.domain.serviceprofiles.aggregate.AccountGroup;
import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.domain.serviceprofiles.repository.AccountGroupRepository;
import com.knight.platform.sharedkernel.ProfileId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AccountGroupRepositoryAdapter implements AccountGroupRepository {

    private final AccountGroupJpaRepository jpaRepository;
    private final AccountGroupMapper mapper;

    public AccountGroupRepositoryAdapter(AccountGroupJpaRepository jpaRepository, AccountGroupMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(AccountGroup group) {
        AccountGroupEntity entity = mapper.toEntity(group);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<AccountGroup> findById(AccountGroupId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<AccountGroup> findByProfileId(ProfileId profileId) {
        return jpaRepository.findByProfileId(profileId.urn()).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<AccountGroup> findByProfileIdAndName(ProfileId profileId, String name) {
        return jpaRepository.findByProfileIdAndName(profileId.urn(), name).map(mapper::toDomain);
    }

    @Override
    public void delete(AccountGroup group) {
        jpaRepository.deleteById(group.id().value());
    }

    @Override
    public boolean existsById(AccountGroupId id) {
        return jpaRepository.existsById(id.value());
    }

    @Override
    public boolean existsByProfileIdAndName(ProfileId profileId, String name) {
        return jpaRepository.existsByProfileIdAndName(profileId.urn(), name);
    }
}
