package com.knight.domain.users.repository;

import com.knight.domain.users.aggregate.UserGroup;
import com.knight.domain.users.types.UserGroupId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for UserGroup aggregate.
 */
public interface UserGroupRepository {

    void save(UserGroup group);

    Optional<UserGroup> findById(UserGroupId id);

    List<UserGroup> findByProfileId(ProfileId profileId);

    List<UserGroup> findByUserId(UserId userId);

    Optional<UserGroup> findByProfileIdAndName(ProfileId profileId, String name);

    void delete(UserGroup group);

    boolean existsById(UserGroupId id);

    boolean existsByProfileIdAndName(ProfileId profileId, String name);
}
