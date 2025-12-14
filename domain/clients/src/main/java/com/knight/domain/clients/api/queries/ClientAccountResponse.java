package com.knight.domain.clients.api.queries;

import com.knight.platform.sharedkernel.AccountStatus;
import com.knight.platform.sharedkernel.ClientAccountId;
import com.knight.platform.sharedkernel.ClientId;

import java.time.Instant;

/**
 * Client account information response.
 * Contains account details including currency and status.
 */
public record ClientAccountResponse(
    ClientAccountId accountId,
    ClientId clientId,
    String currency,
    AccountStatus status,
    Instant createdAt
) {
}
