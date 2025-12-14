package com.knight.domain.users.api.commands;

import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.UserId;

/**
 * Command interface for User Management.
 * Defines contract for creating and managing users.
 */
public interface UserCommands {

    UserId createUser(CreateUserCmd cmd);

    record CreateUserCmd(
        String email,
        String userType,
        String identityProvider,
        ClientId clientId
    ) {}

    void activateUser(ActivateUserCmd cmd);

    record ActivateUserCmd(UserId userId) {}

    void deactivateUser(DeactivateUserCmd cmd);

    record DeactivateUserCmd(UserId userId, String reason) {}

    void lockUser(LockUserCmd cmd);

    record LockUserCmd(UserId userId, String reason) {}

    void unlockUser(UnlockUserCmd cmd);

    record UnlockUserCmd(UserId userId) {}
}
