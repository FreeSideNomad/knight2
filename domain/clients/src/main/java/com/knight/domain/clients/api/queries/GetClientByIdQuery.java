package com.knight.domain.clients.api.queries;

import com.knight.platform.sharedkernel.ClientId;

/**
 * Query to retrieve a client by its identifier.
 *
 * @param clientId the client identifier
 */
public record GetClientByIdQuery(
    ClientId clientId
) {
}
