package com.knight.application.persistence.passkeys.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passkeys")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasskeyEntity {

    @Id
    @Column(name = "passkey_id", nullable = false)
    private UUID passkeyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "credential_id", nullable = false, unique = true, length = 1024)
    private String credentialId;

    @Column(name = "public_key", nullable = false, length = 2048)
    private String publicKey;

    @Column(name = "aaguid", length = 36)
    private String aaguid;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "sign_count", nullable = false)
    private long signCount;

    @Column(name = "user_verification", nullable = false)
    private boolean userVerification;

    @Column(name = "backup_eligible", nullable = false)
    private boolean backupEligible;

    @Column(name = "backup_state", nullable = false)
    private boolean backupState;

    @Column(name = "transports", length = 255)
    private String transports;  // Comma-separated list

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
