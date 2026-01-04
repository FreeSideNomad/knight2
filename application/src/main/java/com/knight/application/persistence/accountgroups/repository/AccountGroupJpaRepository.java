package com.knight.application.persistence.accountgroups.repository;

import com.knight.application.persistence.accountgroups.entity.AccountGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountGroupJpaRepository extends JpaRepository<AccountGroupEntity, UUID> {

    List<AccountGroupEntity> findByProfileId(String profileId);

    Optional<AccountGroupEntity> findByProfileIdAndName(String profileId, String name);

    boolean existsByProfileIdAndName(String profileId, String name);
}
