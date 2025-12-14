package com.knight.domain.users.api.queries;

import com.knight.platform.sharedkernel.UserId;

/**
 * Query interface for User Management.
 */
public interface UserQueries {

    record UserSummary(
        String userId,
        String email,
        String status,
        String userType,
        String identityProvider
    ) {}

    UserSummary getUserSummary(UserId userId);
}
