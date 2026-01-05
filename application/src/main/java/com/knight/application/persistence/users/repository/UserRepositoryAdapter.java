package com.knight.application.persistence.users.repository;

import com.knight.application.persistence.users.entity.UserEntity;
import com.knight.application.persistence.users.entity.UserRoleEntity;
import com.knight.application.persistence.users.mapper.UserMapper;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;
    private final UserMapper mapper;

    @Override
    @Transactional
    public void save(User user) {
        UUID userId = UUID.fromString(user.id().id());
        Optional<UserEntity> existing = jpaRepository.findById(userId);

        UserEntity entity;
        if (existing.isPresent()) {
            // Update existing entity
            entity = existing.get();
            entity.setFirstName(user.firstName());
            entity.setLastName(user.lastName());
            entity.setIdentityProviderUserId(user.identityProviderUserId());
            entity.setEmailVerified(user.emailVerified());
            entity.setPasswordSet(user.passwordSet());
            entity.setMfaEnrolled(user.mfaEnrolled());
            entity.setLastSyncedAt(user.lastSyncedAt());
            entity.setLastLoggedInAt(user.lastLoggedInAt());
            entity.setStatus(user.status().name());
            entity.setLockType(user.lockType().name());
            entity.setLockedBy(user.lockedBy());
            entity.setLockedAt(user.lockedAt());
            entity.setDeactivationReason(user.deactivationReason());
            entity.setUpdatedAt(user.updatedAt());

            // Update roles - clear and re-add
            entity.getRoles().clear();
            for (User.Role role : user.roles()) {
                UserRoleEntity roleEntity = new UserRoleEntity(
                    userId,
                    role.name(),
                    Instant.now(),
                    user.createdBy()
                );
                entity.getRoles().add(roleEntity);
            }
        } else {
            // Create new entity
            entity = mapper.toEntity(user);
        }

        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId userId) {
        return jpaRepository.findById(UUID.fromString(userId.id()))
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByLoginId(String loginId) {
        return jpaRepository.findByLoginId(loginId)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByIdentityProviderUserId(String identityProviderUserId) {
        return jpaRepository.findByIdentityProviderUserId(identityProviderUserId)
            .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findByProfileId(ProfileId profileId) {
        return jpaRepository.findByProfileId(profileId.urn())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    @Transactional
    public void deleteById(UserId userId) {
        jpaRepository.deleteById(UUID.fromString(userId.id()));
    }
}
