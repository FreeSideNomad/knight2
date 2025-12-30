package com.knight.application.rest.indirect;

/**
 * Request to update an OFI account.
 */
public record UpdateOfiAccountRequest(
    String accountHolderName
) {}
