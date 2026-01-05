package com.knight.application.service.passkey;

import java.util.List;

/**
 * Response containing WebAuthn authentication options.
 */
public record AuthenticationOptionsResponse(
    String challengeId,
    String challenge,
    String rpId,
    long timeout,
    String userVerification,
    List<AllowedCredential> allowCredentials
) {}
