package com.knight.domain.clients.api.queries;

/**
 * Query for searching clients by type and name.
 * Used as input for client search operations.
 */
public record ClientSearchQuery(
    String clientType,    // "srf" or "cdr"
    String nameQuery      // Partial name search
) {

    public ClientSearchQuery {
        if (clientType == null || clientType.isBlank()) {
            throw new IllegalArgumentException("Client type cannot be null or blank");
        }

        if (!clientType.equals("srf") && !clientType.equals("cdr")) {
            throw new IllegalArgumentException(
                "Invalid client type: " + clientType + ". Must be 'srf' or 'cdr'");
        }

        if (nameQuery == null || nameQuery.isBlank()) {
            throw new IllegalArgumentException("Name query cannot be null or blank");
        }

        // Normalize the name query by trimming whitespace
        nameQuery = nameQuery.trim();

        if (nameQuery.isEmpty()) {
            throw new IllegalArgumentException("Name query cannot be empty after trimming");
        }
    }
}
