package com.knight.application.service.passkey;

import java.util.List;

/**
 * Response containing WebAuthn registration options.
 */
public record RegistrationOptionsResponse(
    String challengeId,
    String challenge,
    String rpId,
    String rpName,
    String userId,
    String userName,
    String userDisplayName,
    long timeout,
    String attestation,
    String authenticatorAttachment,
    String residentKey,
    String userVerification,
    List<String> excludeCredentials
) {}
