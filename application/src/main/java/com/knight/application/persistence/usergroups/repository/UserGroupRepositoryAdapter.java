package com.knight.application.persistence.usergroups.repository;

import com.knight.application.persistence.usergroups.entity.UserGroupEntity;
import com.knight.application.persistence.usergroups.mapper.UserGroupMapper;
import com.knight.domain.users.aggregate.UserGroup;
import com.knight.domain.users.types.UserGroupId;
import com.knight.domain.users.repository.UserGroupRepository;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserGroupRepositoryAdapter implements UserGroupRepository {

    private final UserGroupJpaRepository jpaRepository;
    private final UserGroupMapper mapper;

    public UserGroupRepositoryAdapter(UserGroupJpaRepository jpaRepository, UserGroupMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void save(UserGroup group) {
        UserGroupEntity entity = mapper.toEntity(group);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<UserGroup> findById(UserGroupId id) {
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public List<UserGroup> findByProfileId(ProfileId profileId) {
        return jpaRepository.findByProfileId(profileId.urn()).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public List<UserGroup> findByUserId(UserId userId) {
        return jpaRepository.findByUserId(userId.id()).stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    public Optional<UserGroup> findByProfileIdAndName(ProfileId profileId, String name) {
        return jpaRepository.findByProfileIdAndName(profileId.urn(), name).map(mapper::toDomain);
    }

    @Override
    public void delete(UserGroup group) {
        jpaRepository.deleteById(group.id().value());
    }

    @Override
    public boolean existsById(UserGroupId id) {
        return jpaRepository.existsById(id.value());
    }

    @Override
    public boolean existsByProfileIdAndName(ProfileId profileId, String name) {
        return jpaRepository.existsByProfileIdAndName(profileId.urn(), name);
    }
}
