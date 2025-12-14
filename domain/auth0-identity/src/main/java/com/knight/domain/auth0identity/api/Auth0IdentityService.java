package com.knight.domain.auth0identity.api;

import com.knight.platform.sharedkernel.UserId;

import java.util.Optional;

/**
 * Auth0 Identity Service interface.
 * Defines the contract for Auth0 identity operations.
 * Acts as an anti-corruption layer between the domain and Auth0.
 */
public interface Auth0IdentityService {

    /**
     * Creates a new user in Auth0.
     *
     * @param request the user creation request
     * @return the Auth0 user ID
     */
    String createUser(CreateAuth0UserRequest request);

    /**
     * Retrieves user information from Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     * @return the user info if found
     */
    Optional<Auth0UserInfo> getUser(String auth0UserId);

    /**
     * Retrieves user by email from Auth0.
     *
     * @param email the email address
     * @return the user info if found
     */
    Optional<Auth0UserInfo> getUserByEmail(String email);

    /**
     * Updates a user in Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     * @param request the update request
     */
    void updateUser(String auth0UserId, UpdateAuth0UserRequest request);

    /**
     * Blocks a user in Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     */
    void blockUser(String auth0UserId);

    /**
     * Unblocks a user in Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     */
    void unblockUser(String auth0UserId);

    /**
     * Deletes a user from Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     */
    void deleteUser(String auth0UserId);

    /**
     * Sends a password reset email to the user.
     *
     * @param email the user's email address
     */
    void sendPasswordResetEmail(String email);

    /**
     * Links the Auth0 user to an internal user ID.
     *
     * @param auth0UserId the Auth0 user ID
     * @param internalUserId the internal user ID
     */
    void linkToInternalUser(String auth0UserId, UserId internalUserId);

    // Request/Response records

    record CreateAuth0UserRequest(
        String email,
        String name,
        String connection,
        boolean emailVerified,
        String password
    ) {
        public CreateAuth0UserRequest(String email, String name) {
            this(email, name, "Username-Password-Authentication", false, null);
        }
    }

    record UpdateAuth0UserRequest(
        String name,
        String email,
        Boolean blocked
    ) {}

    record Auth0UserInfo(
        String auth0UserId,
        String email,
        String name,
        boolean emailVerified,
        boolean blocked,
        String picture,
        String lastLogin
    ) {}
}
