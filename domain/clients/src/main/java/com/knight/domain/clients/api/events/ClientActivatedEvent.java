package com.knight.domain.clients.api.events;

import com.knight.platform.sharedkernel.ClientId;

import java.time.Instant;

/**
 * Event published when a Client is activated.
 *
 * @param clientId the client identifier
 * @param activatedAt timestamp when the client was activated
 */
public record ClientActivatedEvent(
    ClientId clientId,
    Instant activatedAt
) {
}
