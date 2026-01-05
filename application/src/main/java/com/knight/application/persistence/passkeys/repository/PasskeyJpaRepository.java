package com.knight.application.persistence.passkeys.repository;

import com.knight.application.persistence.passkeys.entity.PasskeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasskeyJpaRepository extends JpaRepository<PasskeyEntity, UUID> {

    Optional<PasskeyEntity> findByCredentialId(String credentialId);

    List<PasskeyEntity> findByUserId(UUID userId);

    int countByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
