package com.knight.domain.clients.api.commands;

import com.knight.platform.sharedkernel.ClientId;

/**
 * Command to update a client's name.
 *
 * @param clientId the client identifier
 * @param name the new name
 */
public record UpdateClientNameCommand(
    ClientId clientId,
    String name
) {
}
