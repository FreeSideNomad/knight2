package com.knight.application.adapter;

import com.knight.domain.policy.port.UserGroupLookup;
import com.knight.domain.users.aggregate.UserGroup;
import com.knight.domain.users.repository.UserGroupRepository;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter that implements UserGroupLookup by delegating to UserGroupRepository.
 */
@Component
public class UserGroupLookupAdapter implements UserGroupLookup {

    private final UserGroupRepository userGroupRepository;

    public UserGroupLookupAdapter(UserGroupRepository userGroupRepository) {
        this.userGroupRepository = userGroupRepository;
    }

    @Override
    public Set<UUID> getGroupsForUser(UserId userId) {
        return userGroupRepository.findByUserId(userId).stream()
            .map(group -> group.id().value())
            .collect(Collectors.toSet());
    }
}
