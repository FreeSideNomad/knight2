package com.knight.domain.users.aggregate;

import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.time.Instant;
import java.util.*;

/**
 * User aggregate root.
 * Manages user lifecycle for client users and indirect client users.
 */
public class User {

    public enum Status {
        PENDING_CREATION,      // User created locally, not yet provisioned to IdP
        PENDING_VERIFICATION,  // Created in IdP, password not yet set
        PENDING_MFA,           // Password set, MFA not enrolled
        ACTIVE,                // Fully onboarded
        LOCKED,                // Account locked
        DEACTIVATED            // Account deactivated
    }

    public enum UserType {
        CLIENT_USER,    // Direct client users (ANP)
        INDIRECT_USER   // Indirect client users (Auth0 provisioned)
    }

    public enum IdentityProvider {
        AUTH0,      // For indirect client users
        ANP         // Legacy system
    }

    public enum Role {
        SECURITY_ADMIN,  // Manages users and security settings
        SERVICE_ADMIN,   // Manages service configuration
        READER,          // Read-only access
        CREATOR,         // Can create transactions/records
        APPROVER         // Can approve transactions/records
    }

    // Core fields
    private final UserId id;
    private final String email;
    private String firstName;
    private String lastName;
    private final UserType userType;
    private final IdentityProvider identityProvider;
    private final ProfileId profileId;
    private final Set<Role> roles;

    // Identity provider fields
    private String identityProviderUserId;
    private boolean passwordSet;
    private boolean mfaEnrolled;
    private Instant lastSyncedAt;

    // Status tracking
    private Status status;
    private String lockReason;
    private String deactivationReason;

    // Audit
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private User(UserId id, String email, String firstName, String lastName,
                 UserType userType, IdentityProvider identityProvider,
                 ProfileId profileId, Set<Role> roles, String createdBy) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.email = Objects.requireNonNull(email, "email cannot be null");
        this.firstName = firstName;
        this.lastName = lastName;
        this.userType = Objects.requireNonNull(userType, "userType cannot be null");
        this.identityProvider = Objects.requireNonNull(identityProvider, "identityProvider cannot be null");
        this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
        this.roles = new HashSet<>(roles != null ? roles : Set.of());
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
        this.status = Status.PENDING_CREATION;
        this.passwordSet = false;
        this.mfaEnrolled = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Factory method for reconstitution from persistence.
     */
    public static User reconstitute(
            UserId id, String email, String firstName, String lastName,
            UserType userType, IdentityProvider identityProvider,
            ProfileId profileId, Set<Role> roles, String identityProviderUserId,
            boolean passwordSet, boolean mfaEnrolled, Instant lastSyncedAt,
            Status status, String lockReason, String deactivationReason,
            Instant createdAt, String createdBy, Instant updatedAt) {
        User user = new User(id, email, firstName, lastName, userType, identityProvider,
                profileId, roles, createdBy);
        user.identityProviderUserId = identityProviderUserId;
        user.passwordSet = passwordSet;
        user.mfaEnrolled = mfaEnrolled;
        user.lastSyncedAt = lastSyncedAt;
        user.status = status;
        user.lockReason = lockReason;
        user.deactivationReason = deactivationReason;
        // Override createdAt and updatedAt from persistence
        try {
            var createdAtField = User.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(user, createdAt);
        } catch (Exception e) {
            // Ignore - use default
        }
        user.updatedAt = updatedAt;
        return user;
    }

    public static User create(String email, String firstName, String lastName,
                              UserType userType, IdentityProvider identityProvider,
                              ProfileId profileId, Set<Role> roles, String createdBy) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        UserId id = UserId.of(UUID.randomUUID().toString());
        return new User(id, email, firstName, lastName, userType, identityProvider,
                profileId, roles, createdBy);
    }

    /**
     * Mark user as provisioned to identity provider.
     */
    public void markProvisioned(String identityProviderUserId) {
        if (this.status != Status.PENDING_CREATION) {
            throw new IllegalStateException("User must be in PENDING_CREATION status to be provisioned");
        }
        this.identityProviderUserId = Objects.requireNonNull(identityProviderUserId);
        this.status = Status.PENDING_VERIFICATION;
        this.updatedAt = Instant.now();
    }

    /**
     * Update onboarding status from identity provider events.
     */
    public void updateOnboardingStatus(boolean passwordSet, boolean mfaEnrolled) {
        this.passwordSet = passwordSet;
        this.mfaEnrolled = mfaEnrolled;
        this.lastSyncedAt = Instant.now();
        this.updatedAt = Instant.now();

        // Update status based on onboarding progress
        if (passwordSet && mfaEnrolled) {
            this.status = Status.ACTIVE;
        } else if (passwordSet) {
            this.status = Status.PENDING_MFA;
        }
        // If neither, status remains PENDING_VERIFICATION
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

    public void addRole(Role role) {
        if (this.roles.add(role)) {
            this.updatedAt = Instant.now();
        }
    }

    public void removeRole(Role role) {
        if (this.roles.size() <= 1) {
            throw new IllegalStateException("Cannot remove last role. User must have at least one role.");
        }
        if (this.roles.remove(role)) {
            this.updatedAt = Instant.now();
        }
    }

    public void updateName(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.updatedAt = Instant.now();
    }

    // Getters
    public UserId id() { return id; }
    public String email() { return email; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public UserType userType() { return userType; }
    public IdentityProvider identityProvider() { return identityProvider; }
    public ProfileId profileId() { return profileId; }
    public Set<Role> roles() { return Collections.unmodifiableSet(roles); }
    public String identityProviderUserId() { return identityProviderUserId; }
    public boolean passwordSet() { return passwordSet; }
    public boolean mfaEnrolled() { return mfaEnrolled; }
    public Instant lastSyncedAt() { return lastSyncedAt; }
    public Status status() { return status; }
    public String lockReason() { return lockReason; }
    public String deactivationReason() { return deactivationReason; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
}
