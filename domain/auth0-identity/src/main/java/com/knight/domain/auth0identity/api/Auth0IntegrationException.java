package com.knight.domain.auth0identity.api;

/**
 * Exception thrown when Auth0 integration operations fail.
 */
public class Auth0IntegrationException extends RuntimeException {

    public Auth0IntegrationException(String message) {
        super(message);
    }

    public Auth0IntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
