package com.knight.domain.clients.api.commands;

import com.knight.platform.sharedkernel.ClientId;

/**
 * Command to update a client's contact information.
 *
 * @param clientId the client identifier
 * @param phoneNumber the new phone number
 * @param emailAddress the new email address
 */
public record UpdateClientContactCommand(
    ClientId clientId,
    String phoneNumber,
    String emailAddress
) {
}
