package com.knight.application.service.passkey;

import java.time.Instant;

/**
 * Summary of a registered passkey for display purposes.
 */
public record PasskeySummary(
    String id,
    String displayName,
    Instant createdAt,
    Instant lastUsedAt,
    boolean userVerification,
    boolean backedUp
) {}
