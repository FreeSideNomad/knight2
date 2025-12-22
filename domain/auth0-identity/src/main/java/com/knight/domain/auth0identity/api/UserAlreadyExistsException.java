package com.knight.domain.auth0identity.api;

/**
 * Exception thrown when attempting to create a user that already exists in Auth0.
 */
public class UserAlreadyExistsException extends Auth0IntegrationException {

    private final String email;
    private final String existingUserId;

    public UserAlreadyExistsException(String message) {
        super(message);
        this.email = null;
        this.existingUserId = null;
    }

    public UserAlreadyExistsException(String email, String existingUserId) {
        super("User already exists in Auth0: " + email + " (ID: " + existingUserId + ")");
        this.email = email;
        this.existingUserId = existingUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getExistingUserId() {
        return existingUserId;
    }
}
