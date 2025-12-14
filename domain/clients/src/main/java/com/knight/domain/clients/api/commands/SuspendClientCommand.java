package com.knight.domain.clients.api.commands;

import com.knight.platform.sharedkernel.ClientId;

/**
 * Command to suspend a client.
 *
 * @param clientId the client identifier
 */
public record SuspendClientCommand(
    ClientId clientId
) {
}
