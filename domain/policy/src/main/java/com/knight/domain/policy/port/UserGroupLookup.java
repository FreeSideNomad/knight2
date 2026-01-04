package com.knight.domain.policy.port;

import com.knight.platform.sharedkernel.UserId;

import java.util.Set;
import java.util.UUID;

/**
 * Port for looking up user group memberships.
 * This is a secondary port that allows the policy module to
 * retrieve group memberships without depending on the users module.
 */
public interface UserGroupLookup {

    /**
     * Get all group IDs that a user belongs to.
     *
     * @param userId the user to lookup
     * @return set of group UUIDs the user belongs to
     */
    Set<UUID> getGroupsForUser(UserId userId);
}
