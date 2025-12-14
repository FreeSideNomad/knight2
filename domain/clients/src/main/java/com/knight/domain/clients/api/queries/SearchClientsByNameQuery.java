package com.knight.domain.clients.api.queries;

/**
 * Query to search for clients by name pattern.
 *
 * @param nameQuery the name search pattern
 */
public record SearchClientsByNameQuery(
    String nameQuery
) {
}
