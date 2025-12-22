package com.knight.application.persistence.users.repository;

import com.knight.application.persistence.users.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByIdentityProviderUserId(String identityProviderUserId);

    List<UserEntity> findByProfileId(String profileId);

    boolean existsByEmail(String email);
}
