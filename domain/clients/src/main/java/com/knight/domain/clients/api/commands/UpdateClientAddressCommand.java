package com.knight.domain.clients.api.commands;

import com.knight.platform.sharedkernel.Address;
import com.knight.platform.sharedkernel.ClientId;

/**
 * Command to update a client's address.
 *
 * @param clientId the client identifier
 * @param address the new address
 */
public record UpdateClientAddressCommand(
    ClientId clientId,
    Address address
) {
}
