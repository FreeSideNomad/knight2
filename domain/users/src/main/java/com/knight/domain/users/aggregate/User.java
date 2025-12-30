package com.knight.domain.users.aggregate;

import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * User aggregate root.
 * Manages user lifecycle for client users and indirect client users.
 */
public class User {

    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,50}$");

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

    public enum LockType {
        NONE,      // Not locked
        CLIENT,    // Locked by Client Admin - can be unlocked by CLIENT, BANK, or SECURITY
        BANK,      // Locked by Bank Admin - can be unlocked by BANK or SECURITY
        SECURITY;  // Locked by Security Admin - can only be unlocked by SECURITY

        /**
         * Check if the requester level is sufficient to unlock this lock type.
         * @param requesterLevel The lock type level of the requester
         * @return true if requester can unlock this lock type
         */
        public boolean canBeUnlockedBy(LockType requesterLevel) {
            if (this == NONE) {
                return true; // Nothing to unlock
            }
            // Requester level must be >= current lock level
            return requesterLevel.ordinal() >= this.ordinal();
        }
    }

    // Core fields
    private final UserId id;
    private final String loginId;
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
    private Instant lastLoggedInAt;

    // Status tracking
    private Status status;
    private LockType lockType;
    private String lockedBy;
    private Instant lockedAt;
    private String deactivationReason;

    // Audit
    private final Instant createdAt;
    private final String createdBy;
    private Instant updatedAt;

    private User(UserId id, String loginId, String email, String firstName, String lastName,
                 UserType userType, IdentityProvider identityProvider,
                 ProfileId profileId, Set<Role> roles, String createdBy) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.loginId = Objects.requireNonNull(loginId, "loginId cannot be null");
        this.email = Objects.requireNonNull(email, "email cannot be null");
        this.firstName = firstName;
        this.lastName = lastName;
        this.userType = Objects.requireNonNull(userType, "userType cannot be null");
        this.identityProvider = Objects.requireNonNull(identityProvider, "identityProvider cannot be null");
        this.profileId = Objects.requireNonNull(profileId, "profileId cannot be null");
        this.roles = new HashSet<>(roles != null ? roles : Set.of());
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
        this.status = Status.PENDING_CREATION;
        this.lockType = LockType.NONE;
        this.passwordSet = false;
        this.mfaEnrolled = false;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Factory method for reconstitution from persistence.
     */
    public static User reconstitute(
            UserId id, String loginId, String email, String firstName, String lastName,
            UserType userType, IdentityProvider identityProvider,
            ProfileId profileId, Set<Role> roles, String identityProviderUserId,
            boolean passwordSet, boolean mfaEnrolled, Instant lastSyncedAt,
            Instant lastLoggedInAt,
            Status status, LockType lockType, String lockedBy, Instant lockedAt,
            String deactivationReason,
            Instant createdAt, String createdBy, Instant updatedAt) {
        User user = new User(id, loginId, email, firstName, lastName, userType, identityProvider,
                profileId, roles, createdBy);
        user.identityProviderUserId = identityProviderUserId;
        user.passwordSet = passwordSet;
        user.mfaEnrolled = mfaEnrolled;
        user.lastSyncedAt = lastSyncedAt;
        user.lastLoggedInAt = lastLoggedInAt;
        user.status = status;
        user.lockType = lockType != null ? lockType : LockType.NONE;
        user.lockedBy = lockedBy;
        user.lockedAt = lockedAt;
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

    public static User create(String loginId, String email, String firstName, String lastName,
                              UserType userType, IdentityProvider identityProvider,
                              ProfileId profileId, Set<Role> roles, String createdBy) {
        validateLoginId(loginId);
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        UserId id = UserId.of(UUID.randomUUID().toString());
        return new User(id, loginId, email, firstName, lastName, userType, identityProvider,
                profileId, roles, createdBy);
    }

    private static void validateLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new IllegalArgumentException("Login ID is required");
        }
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw new IllegalArgumentException(
                "Login ID must be 3-50 characters and contain only alphanumeric characters and underscores");
        }
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

    /**
     * Lock the user account with a specific lock type.
     * @param type The type of lock to apply
     * @param actor The user/system performing the lock action
     */
    public void lock(LockType type, String actor) {
        if (type == null || type == LockType.NONE) {
            throw new IllegalArgumentException("Lock type must be CLIENT, BANK, or SECURITY");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Actor is required for lock operation");
        }
        if (this.status == Status.LOCKED) {
            return;
        }
        this.status = Status.LOCKED;
        this.lockType = type;
        this.lockedBy = actor;
        this.lockedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Unlock the user account.
     * @param requesterLevel The lock type level of the requester attempting to unlock
     * @param actor The user/system performing the unlock action
     */
    public void unlock(LockType requesterLevel, String actor) {
        if (this.status != Status.LOCKED) {
            throw new IllegalStateException("User is not locked");
        }
        if (requesterLevel == null) {
            throw new IllegalArgumentException("Requester level is required");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("Actor is required for unlock operation");
        }
        if (!this.lockType.canBeUnlockedBy(requesterLevel)) {
            throw new IllegalStateException(
                String.format("Cannot unlock %s lock with %s level. Insufficient permissions.",
                    this.lockType, requesterLevel)
            );
        }
        this.status = Status.ACTIVE;
        this.lockType = LockType.NONE;
        this.lockedBy = null;
        this.lockedAt = null;
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

    /**
     * Record a successful login event.
     * Updates the lastLoggedInAt timestamp to track user activity.
     */
    public void recordLogin() {
        this.lastLoggedInAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters
    public UserId id() { return id; }
    public String loginId() { return loginId; }
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
    public Instant lastLoggedInAt() { return lastLoggedInAt; }
    public Status status() { return status; }
    public LockType lockType() { return lockType; }
    public String lockedBy() { return lockedBy; }
    public Instant lockedAt() { return lockedAt; }
    public String deactivationReason() { return deactivationReason; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant updatedAt() { return updatedAt; }
}
