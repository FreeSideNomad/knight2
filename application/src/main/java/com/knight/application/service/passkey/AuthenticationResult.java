package com.knight.application.service.passkey;

/**
 * Result of passkey authentication.
 */
public record AuthenticationResult(
    boolean success,
    String userId,
    String loginId,
    String email,
    String profileId,
    boolean userVerification,
    String error
) {
    public static AuthenticationResult success(String userId, String loginId, String email,
                                                String profileId, boolean userVerification) {
        return new AuthenticationResult(true, userId, loginId, email, profileId, userVerification, null);
    }

    public static AuthenticationResult failure(String error) {
        return new AuthenticationResult(false, null, null, null, null, false, error);
    }
}
