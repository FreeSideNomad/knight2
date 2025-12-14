package com.knight.domain.clients.api.queries;

import com.knight.platform.sharedkernel.ClientId;

/**
 * Result record representing a single client in search results.
 * Contains minimal information needed for client selection.
 */
public record ClientSearchResult(
    ClientId clientId,
    String name,
    String clientType     // "srf" or "cdr"
) {
}
