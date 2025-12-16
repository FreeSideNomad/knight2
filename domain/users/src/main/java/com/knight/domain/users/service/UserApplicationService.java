package com.knight.domain.users.service;

import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.api.commands.UserCommands;
import com.knight.domain.users.api.events.UserCreated;
import com.knight.domain.users.api.queries.UserQueries;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Application service for User Management.
 * Orchestrates user lifecycle operations with transactions and event publishing.
 */
@Service
public class UserApplicationService implements UserCommands, UserQueries {

    private final UserRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public UserApplicationService(
        UserRepository repository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public UserId createUser(CreateUserCmd cmd) {
        User.UserType userType = User.UserType.valueOf(cmd.userType());
        User.IdentityProvider identityProvider = User.IdentityProvider.valueOf(cmd.identityProvider());

        User user = User.create(cmd.email(), userType, identityProvider, cmd.profileId());

        repository.save(user);

        eventPublisher.publishEvent(new UserCreated(
            user.id().id(),
            cmd.email(),
            cmd.userType(),
            cmd.identityProvider(),
            Instant.now()
        ));

        return user.id();
    }

    @Override
    @Transactional
    public void activateUser(ActivateUserCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        user.activate();

        repository.save(user);
    }

    @Override
    @Transactional
    public void deactivateUser(DeactivateUserCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        user.deactivate(cmd.reason());

        repository.save(user);
    }

    @Override
    @Transactional
    public void lockUser(LockUserCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        user.lock(cmd.reason());

        repository.save(user);
    }

    @Override
    @Transactional
    public void unlockUser(UnlockUserCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        user.unlock();

        repository.save(user);
    }

    @Override
    public UserSummary getUserSummary(UserId userId) {
        User user = repository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId.id()));

        return new UserSummary(
            user.id().id(),
            user.email(),
            user.status().name(),
            user.userType().name(),
            user.identityProvider().name()
        );
    }
}
