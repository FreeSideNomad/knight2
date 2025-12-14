package com.knight.domain.clients.api.events;

import com.knight.platform.sharedkernel.ClientId;

import java.time.Instant;

/**
 * Event published when a Client is updated.
 *
 * @param clientId the client identifier
 * @param fieldUpdated the name of the field that was updated
 * @param updatedAt timestamp when the client was updated
 */
public record ClientUpdatedEvent(
    ClientId clientId,
    String fieldUpdated,
    Instant updatedAt
) {
}
