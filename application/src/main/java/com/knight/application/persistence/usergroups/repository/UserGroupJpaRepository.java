package com.knight.application.persistence.usergroups.repository;

import com.knight.application.persistence.usergroups.entity.UserGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserGroupJpaRepository extends JpaRepository<UserGroupEntity, UUID> {

    List<UserGroupEntity> findByProfileId(String profileId);

    Optional<UserGroupEntity> findByProfileIdAndName(String profileId, String name);

    boolean existsByProfileIdAndName(String profileId, String name);

    @Query("SELECT DISTINCT g FROM UserGroupEntity g JOIN g.members m WHERE m.userId = :userId")
    List<UserGroupEntity> findByUserId(@Param("userId") String userId);
}
