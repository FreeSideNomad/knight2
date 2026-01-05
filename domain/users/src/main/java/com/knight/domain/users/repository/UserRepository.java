package com.knight.domain.users.repository;

import com.knight.domain.users.aggregate.User;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for User aggregate persistence.
 * Implementations handle the actual storage mechanism (JPA, in-memory, etc.)
 */
public interface UserRepository {

    /**
     * Persists a User aggregate.
     *
     * @param user the user to save
     */
    void save(User user);

    /**
     * Retrieves a User by its identifier.
     *
     * @param userId the user identifier
     * @return the user if found
     */
    Optional<User> findById(UserId userId);

    /**
     * Retrieves a User by email.
     *
     * @param email the email address
     * @return the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Retrieves a User by login ID.
     *
     * @param loginId the login ID
     * @return the user if found
     */
    Optional<User> findByLoginId(String loginId);

    /**
     * Retrieves a User by identity provider user ID.
     *
     * @param identityProviderUserId the IdP user ID (e.g., auth0|xxx)
     * @return the user if found
     */
    Optional<User> findByIdentityProviderUserId(String identityProviderUserId);

    /**
     * Lists all users for a profile.
     *
     * @param profileId the profile identifier
     * @return list of users for the profile
     */
    List<User> findByProfileId(ProfileId profileId);

    /**
     * Checks if a user with the given email exists.
     *
     * @param email the email address
     * @return true if a user with this email exists
     */
    boolean existsByEmail(String email);

    /**
     * Deletes a user by ID.
     *
     * @param userId the user identifier
     */
    void deleteById(UserId userId);
}
