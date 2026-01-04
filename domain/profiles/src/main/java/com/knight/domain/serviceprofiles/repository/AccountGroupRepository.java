package com.knight.domain.serviceprofiles.repository;

import com.knight.domain.serviceprofiles.aggregate.AccountGroup;
import com.knight.domain.serviceprofiles.types.AccountGroupId;
import com.knight.platform.sharedkernel.ProfileId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for AccountGroup aggregate.
 */
public interface AccountGroupRepository {

    void save(AccountGroup group);

    Optional<AccountGroup> findById(AccountGroupId id);

    List<AccountGroup> findByProfileId(ProfileId profileId);

    Optional<AccountGroup> findByProfileIdAndName(ProfileId profileId, String name);

    void delete(AccountGroup group);

    boolean existsById(AccountGroupId id);

    boolean existsByProfileIdAndName(ProfileId profileId, String name);
}
