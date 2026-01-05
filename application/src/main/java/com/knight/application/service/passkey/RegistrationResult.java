package com.knight.application.service.passkey;

/**
 * Result of passkey registration.
 */
public record RegistrationResult(
    boolean success,
    String passkeyId,
    String error
) {
    public static RegistrationResult success(String passkeyId) {
        return new RegistrationResult(true, passkeyId, null);
    }

    public static RegistrationResult failure(String error) {
        return new RegistrationResult(false, null, error);
    }
}
