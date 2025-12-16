package com.knight.domain.clients.api.commands;

import com.knight.domain.clients.types.ClientType;
import com.knight.platform.sharedkernel.Address;
import com.knight.platform.sharedkernel.ClientId;

/**
 * Command to create a new Client.
 *
 * @param clientId the unique client identifier
 * @param name the client name
 * @param clientType the type of client (INDIVIDUAL or BUSINESS)
 * @param address the client's address
 * @param taxId optional tax identification number
 * @param phoneNumber optional phone number
 * @param emailAddress optional email address
 */
public record CreateClientCommand(
    ClientId clientId,
    String name,
    ClientType clientType,
    Address address,
    String taxId,
    String phoneNumber,
    String emailAddress
) {
}
