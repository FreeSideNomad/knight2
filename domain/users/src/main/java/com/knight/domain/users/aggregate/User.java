package com.knight.domain.users.aggregate;

import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * User aggregate root.
 * Manages user lifecycle for direct and indirect clients.
 */
public class User {

    public enum Status {
        PENDING, ACTIVE, LOCKED, DEACTIVATED
    }

    public enum UserType {
        DIRECT, INDIRECT
    }

    public enum IdentityProvider {
        OKTA, AZURE_AD, ANP
    }

    private final UserId id;
    private final String email;
    private final UserType userType;
    private final IdentityProvider identityProvider;
    private final ProfileId profileId;
    private Status status;
    private final Instant createdAt;
    private Instant updatedAt;
    private String lockReason;
    private String deactivationReason;

    private User(UserId id, String email, UserType userType,
                IdentityProvider identityProvider, ProfileId profileId) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.email = Objects.requireNonNull(email, "email cannot be null");
        this.userType = Objects.requireNonNull(userType, "userType cannot be null");
        this.identityProvider = Objects.requireNonNull(identityProvider, "identityProvider cannot be null");
        this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static User create(String email, UserType userType,
                             IdentityProvider identityProvider, ProfileId profileId) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }
        UserId id = UserId.of(UUID.randomUUID().toString());
        return new User(id, email, userType, identityProvider, profileId);
    }

    public void activate() {
        if (this.status == Status.LOCKED) {
            throw new IllegalStateException("Cannot activate locked user. Unlock first.");
        }
        if (this.status == Status.ACTIVE) {
            return;
        }
        this.status = Status.ACTIVE;
        this.updatedAt = Instant.now();
        this.deactivationReason = null;
    }

    public void deactivate(String reason) {
        if (this.status == Status.DEACTIVATED) {
            return;
        }
        this.status = Status.DEACTIVATED;
        this.deactivationReason = reason;
        this.updatedAt = Instant.now();
    }

    public void lock(String reason) {
        if (this.status == Status.LOCKED) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Lock reason is required");
        }
        this.status = Status.LOCKED;
        this.lockReason = reason;
        this.updatedAt = Instant.now();
    }

    public void unlock() {
        if (this.status != Status.LOCKED) {
            throw new IllegalStateException("User is not locked");
        }
        this.status = Status.ACTIVE;
        this.lockReason = null;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UserId id() { return id; }
    public String email() { return email; }
    public UserType userType() { return userType; }
    public IdentityProvider identityProvider() { return identityProvider; }
    public ProfileId profileId() { return profileId; }
    public Status status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public String lockReason() { return lockReason; }
    public String deactivationReason() { return deactivationReason; }
}
