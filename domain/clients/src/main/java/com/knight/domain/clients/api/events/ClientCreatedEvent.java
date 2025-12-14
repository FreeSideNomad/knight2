package com.knight.domain.clients.api.events;

import com.knight.platform.sharedkernel.Address;
import com.knight.platform.sharedkernel.ClientId;

import java.time.Instant;

/**
 * Event published when a new Client is created.
 *
 * @param clientId the client identifier
 * @param name the client name
 * @param clientType the type of client
 * @param address the client's address
 * @param createdAt timestamp when the client was created
 */
public record ClientCreatedEvent(
    ClientId clientId,
    String name,
    String clientType,
    Address address,
    Instant createdAt
) {
}
