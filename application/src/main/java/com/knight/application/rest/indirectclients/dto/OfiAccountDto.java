package com.knight.application.rest.indirectclients.dto;

import java.time.Instant;

/**
 * REST DTO for OFI (Other Financial Institution) account information.
 */
public record OfiAccountDto(
    String accountId,
    String bankCode,
    String transitNumber,
    String accountNumber,
    String accountHolderName,
    String status,
    String formattedAccountId,
    Instant addedAt
) {}
