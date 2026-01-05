package com.knight.application.service.passkey;

import java.util.List;

/**
 * Allowed credential for WebAuthn authentication.
 */
public record AllowedCredential(
    String id,
    List<String> transports
) {}
