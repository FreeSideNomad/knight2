package com.knight.domain.clients.api.events;

import com.knight.platform.sharedkernel.ClientId;

import java.time.Instant;

/**
 * Event published when a Client is suspended.
 *
 * @param clientId the client identifier
 * @param suspendedAt timestamp when the client was suspended
 */
public record ClientSuspendedEvent(
    ClientId clientId,
    Instant suspendedAt
) {
}
