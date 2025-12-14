package com.knight.domain.clients.api.queries;

/**
 * Query to search for clients by type and name pattern.
 *
 * @param clientType the client type filter (e.g., "INDIVIDUAL", "BUSINESS")
 * @param nameQuery the name search pattern
 */
public record SearchClientsByTypeAndNameQuery(
    String clientType,
    String nameQuery
) {
}
