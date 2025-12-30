package com.knight.application.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when access is forbidden due to wrong token type.
 * Used to enforce BFF access rules (e.g., Auth0 tokens cannot access bank admin endpoints).
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
