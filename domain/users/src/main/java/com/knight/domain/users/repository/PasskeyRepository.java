package com.knight.domain.users.repository;

import com.knight.domain.users.aggregate.Passkey;
import com.knight.domain.users.types.PasskeyId;
import com.knight.platform.sharedkernel.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Passkey persistence.
 * Implementations handle the actual storage mechanism (JPA, in-memory, etc.)
 */
public interface PasskeyRepository {

    /**
     * Persists a Passkey.
     *
     * @param passkey the passkey to save
     */
    void save(Passkey passkey);

    /**
     * Retrieves a Passkey by its identifier.
     *
     * @param passkeyId the passkey identifier
     * @return the passkey if found
     */
    Optional<Passkey> findById(PasskeyId passkeyId);

    /**
     * Retrieves a Passkey by its credential ID.
     * Credential ID is the unique identifier from the WebAuthn authenticator.
     *
     * @param credentialId the Base64URL-encoded credential ID
     * @return the passkey if found
     */
    Optional<Passkey> findByCredentialId(String credentialId);

    /**
     * Lists all passkeys for a user.
     *
     * @param userId the user identifier
     * @return list of passkeys for the user
     */
    List<Passkey> findByUserId(UserId userId);

    /**
     * Counts passkeys for a user.
     *
     * @param userId the user identifier
     * @return number of passkeys registered for the user
     */
    int countByUserId(UserId userId);

    /**
     * Checks if a user has any passkeys registered.
     *
     * @param userId the user identifier
     * @return true if the user has at least one passkey
     */
    boolean existsByUserId(UserId userId);

    /**
     * Deletes a passkey by ID.
     *
     * @param passkeyId the passkey identifier
     */
    void deleteById(PasskeyId passkeyId);

    /**
     * Deletes all passkeys for a user.
     *
     * @param userId the user identifier
     */
    void deleteByUserId(UserId userId);
}
