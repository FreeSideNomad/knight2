package com.knight.application.persistence.profiles.repository;

import com.knight.application.persistence.profiles.entity.ServicingProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for ServicingProfileEntity.
 */
@Repository
public interface ServicingProfileJpaRepository extends JpaRepository<ServicingProfileEntity, String> {

    /**
     * Finds a servicing profile by client ID.
     */
    Optional<ServicingProfileEntity> findByClientId(String clientId);
}
