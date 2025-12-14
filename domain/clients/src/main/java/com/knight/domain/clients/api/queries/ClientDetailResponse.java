package com.knight.domain.clients.api.queries;

import com.knight.platform.sharedkernel.Address;
import com.knight.platform.sharedkernel.ClientId;

import java.time.Instant;

/**
 * Detailed client information response.
 * Contains complete client data including address and timestamps.
 */
public record ClientDetailResponse(
    ClientId clientId,
    String name,
    String clientType,
    Address address,
    Instant createdAt,
    Instant updatedAt
) {
}
