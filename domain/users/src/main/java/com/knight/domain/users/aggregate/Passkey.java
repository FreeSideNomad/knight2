package com.knight.domain.users.aggregate;

import com.knight.domain.users.types.PasskeyId;
import com.knight.platform.sharedkernel.UserId;

import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Passkey entity representing a WebAuthn credential.
 * Each passkey is associated with a user and contains the credential
 * information needed for passwordless authentication.
 */
public class Passkey {

    private final PasskeyId id;
    private final UserId userId;
    private final String credentialId;        // Base64URL encoded credential ID
    private final String publicKey;           // Base64URL encoded public key (COSE format)
    private final String aaguid;              // Authenticator Attestation GUID
    private String displayName;               // User-friendly name for this passkey
    private long signCount;                   // Signature counter for replay detection
    private boolean userVerification;         // Whether UV is supported
    private boolean backupEligible;           // Credential can be backed up
    private boolean backupState;              // Credential is currently backed up
    private final String[] transports;        // Authenticator transports (usb, nfc, ble, internal, hybrid)
    private Instant lastUsedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    private Passkey(PasskeyId id, UserId userId, String credentialId, String publicKey,
                    String aaguid, String displayName, long signCount,
                    boolean userVerification, boolean backupEligible, boolean backupState,
                    String[] transports, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.credentialId = Objects.requireNonNull(credentialId, "credentialId cannot be null");
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey cannot be null");
        this.aaguid = aaguid;
        this.displayName = displayName;
        this.signCount = signCount;
        this.userVerification = userVerification;
        this.backupEligible = backupEligible;
        this.backupState = backupState;
        this.transports = transports != null ? transports.clone() : new String[0];
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Factory method to create a new passkey from WebAuthn registration.
     */
    public static Passkey create(UserId userId, String credentialId, String publicKey,
                                  String aaguid, String displayName, long signCount,
                                  boolean userVerification, boolean backupEligible,
                                  boolean backupState, String[] transports) {
        validateCredentialId(credentialId);
        validatePublicKey(publicKey);
        return new Passkey(
            PasskeyId.generate(),
            userId,
            credentialId,
            publicKey,
            aaguid,
            displayName != null ? displayName : "Passkey",
            signCount,
            userVerification,
            backupEligible,
            backupState,
            transports,
            Instant.now()
        );
    }

    /**
     * Factory method for reconstitution from persistence.
     */
    public static Passkey reconstitute(PasskeyId id, UserId userId, String credentialId,
                                        String publicKey, String aaguid, String displayName,
                                        long signCount, boolean userVerification,
                                        boolean backupEligible, boolean backupState,
                                        String[] transports, Instant lastUsedAt,
                                        Instant createdAt, Instant updatedAt) {
        Passkey passkey = new Passkey(id, userId, credentialId, publicKey, aaguid,
            displayName, signCount, userVerification, backupEligible, backupState,
            transports, createdAt);
        passkey.lastUsedAt = lastUsedAt;
        passkey.updatedAt = updatedAt;
        return passkey;
    }

    private static void validateCredentialId(String credentialId) {
        if (credentialId == null || credentialId.isBlank()) {
            throw new IllegalArgumentException("Credential ID is required");
        }
        try {
            Base64.getUrlDecoder().decode(credentialId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Credential ID must be Base64URL encoded");
        }
    }

    private static void validatePublicKey(String publicKey) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("Public key is required");
        }
        try {
            Base64.getUrlDecoder().decode(publicKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Public key must be Base64URL encoded");
        }
    }

    /**
     * Update signature counter after successful authentication.
     * Validates that the new counter is greater than the stored counter
     * to prevent replay attacks.
     *
     * @param newSignCount The new signature counter from authenticator
     * @throws IllegalStateException if new counter is not greater than stored counter
     */
    public void updateSignCount(long newSignCount) {
        if (newSignCount <= this.signCount) {
            throw new IllegalStateException(
                "Signature counter did not increase. Possible cloned authenticator detected.");
        }
        this.signCount = newSignCount;
        this.lastUsedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Update the display name for this passkey.
     */
    public void updateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    /**
     * Update backup state (credential may have been synced to cloud).
     */
    public void updateBackupState(boolean backupState) {
        this.backupState = backupState;
        this.updatedAt = Instant.now();
    }

    /**
     * Record that this passkey was used for authentication.
     */
    public void recordUsage() {
        this.lastUsedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters
    public PasskeyId id() { return id; }
    public UserId userId() { return userId; }
    public String credentialId() { return credentialId; }
    public String publicKey() { return publicKey; }
    public String aaguid() { return aaguid; }
    public String displayName() { return displayName; }
    public long signCount() { return signCount; }
    public boolean userVerification() { return userVerification; }
    public boolean backupEligible() { return backupEligible; }
    public boolean backupState() { return backupState; }
    public String[] transports() { return transports.clone(); }
    public Instant lastUsedAt() { return lastUsedAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
