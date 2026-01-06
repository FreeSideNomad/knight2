package com.knight.application.persistence.users.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "login_id", nullable = false, unique = true, length = 50)
    private String loginId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "user_type", nullable = false, length = 20)
    private String userType;

    @Column(name = "identity_provider", nullable = false, length = 20)
    private String identityProvider;

    @Column(name = "profile_id", nullable = false, length = 200)
    private String profileId;

    @Column(name = "identity_provider_user_id", unique = true, length = 255)
    private String identityProviderUserId;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password_set", nullable = false)
    private boolean passwordSet;

    @Column(name = "mfa_enrolled", nullable = false)
    private boolean mfaEnrolled;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_logged_in_at")
    private Instant lastLoggedInAt;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "lock_type", nullable = false, length = 20)
    private String lockType = "NONE";

    @Column(name = "locked_by", length = 255)
    private String lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "deactivation_reason", length = 500)
    private String deactivationReason;

    @Column(name = "mfa_preference", length = 20)
    private String mfaPreference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<UserRoleEntity> roles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
